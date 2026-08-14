package cn.bitloom.agentic.permission.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * workdir 越界守卫 — 对标 code.py Gate 2 "write outside workspace"。
 *
 * <p>检查命令的写操作目标路径是否越出项目工作区（沙箱）。静态可判的越界路径返回给调用方，
 * 由 {ApprovalService} 硬拒绝；无法静态判定（环境变量、通配符）的路径保守放行，
 * 交由后续审批弹框兜底，避免误拦。
 *
 * <p>边界处理：
 * <ul>
 *   <li>相对路径以 workdir 为基准绝对化（shell cwd 即 projectPath）</li>
 *   <li>判定用父子关系而非裸 {@code startsWith}，避免 {@code /a/proj} vs {@code /a/projb} 前缀碰撞</li>
 *   <li>通配符：根据通配符的固定前缀根判定——根在沙箱外判越界，根在内判不越界</li>
 *   <li>环境变量 {@code $VAR}/{@code %VAR%}：无法静态判定，返回 null 走审批</li>
 *   <li>多段命令（{@code &}/{@code ;}/{@code |} 连接）：逐段检查，任一段越界即返回</li>
 *   <li>含 {@code cd}/{@code pushd}/{@code popd} 的命令：因持久化 shell 的 cwd 可变，静态守卫
 *       无法可靠追踪，返降级走审批兜底，避免误放行。</li>
 * </ul>
 *
 * <p>已知限制：命令通过持久化 Shell 会话执行，若会话内曾 {@code cd} 出项目目录，守卫无法感知，
 * 相对路径解析可能失真。本守卫定位为"尽力而为"的安全网，最终防线仍是审批弹框。
 */
public final class WorkdirEscapeGuard {

    private static final Logger log = LoggerFactory.getLogger(WorkdirEscapeGuard.class);

    /** 写操作命令（带路径目标）— 合并 Unix + Windows，因未知环境可能是 cmd 或 Git Bash */
    private static final Set<String> WRITE_CMDS = Set.of(
            // Unix / Git Bash
            "rm", "mv", "cp", "mkdir", "rmdir", "touch", "chmod", "chown", "ln", "tee",
            // Windows cmd / PowerShell
            "copy", "move", "xcopy", "robocopy", "del", "type",
            "Remove-Item", "Move-Item", "Copy-Item", "Set-Content", "Add-Content", "Out-File"
    );

    /** 重定向符（> / >> / 1> / 2> 等） */
    private static final Pattern REDIRECT = Pattern.compile("(?:[0-9]?>>?)\\s*([^\\s|&;<]+)");

    /** 改变持久化会话 cwd 的命令 — 命中时守卫降级（无法静态追踪相对路径） */
    private static final Set<String> CD_CMDS = Set.of("cd", "pushd", "popd");

    private WorkdirEscapeGuard() {
    }

    /**
     * 检查命令的写操作目标是否越出工作区。
     *
     * @param command   原始命令
     * @param workdir   项目工作区绝对或相对路径；null/blank 时返回 null（不检查）
     * @return 越界的第一个目标绝对路径；未越界或无法判定时返回 null
     */
    public static String checkEscape(String command, String workdir) {
        if (command == null || command.isBlank()) {
            return null;
        }
        if (workdir == null || workdir.isBlank()) {
            return null;
        }
        Path root;
        try {
            root = Path.of(workdir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            log.warn("[WorkdirEscapeGuard] 无效的 workdir: {}", workdir);
            return null;
        }

        // 含 cd / pushd / popd 的命令：会改变持久化会话 cwd，后续相对路径无法静态判定，降级走审批兜底
        String[] firstSeg = command.split("[&|;]")[0].trim().split("\\s+");
        if (firstSeg.length > 0 && CD_CMDS.contains(firstSeg[0].toLowerCase(Locale.ROOT))) {
            return null;
        }

        // 按 & | ; 拆分多段，逐段检查任一段越界
        for (String segment : command.split("[&|;]")) {
            String escaped = checkSegment(segment, root);
            if (escaped != null) {
                return escaped;
            }
        }
        return null;
    }

    /** 检查单个命令段。 */
    private static String checkSegment(String segment, Path root) {
        String trimmed = segment.trim();
        if (trimmed.isBlank() || trimmed.isEmpty()) {
            return null;
        }

        Set<String> writeCmds = WRITE_CMDS;
        String[] tokens = CommandLineParser.tokenize(trimmed);
        if (tokens.length == 0) {
            return null;
        }
        String first = tokens[0].toLowerCase(Locale.ROOT);

        // 重定向写目标（echo hi > file）优先检查；排除 /dev/null 与 NUL（丢弃输出设备，非磁盘写）
        Matcher m = REDIRECT.matcher(trimmed);
        while (m.find()) {
            String target = m.group(1);
            if (isDiscardDevice(target)) {
                continue;
            }
            String resolved = resolve(target, root);
            if (resolved != null && isOutside(root, resolved)) {
                return resolved;
            }
        }

        // 非写命令直接跳过
        if (!writeCmds.contains(first)) {
            return null;
        }

        // mv/cp 的目标是最后一个非选项路径参数；其余命令所有路径参数皆为目标
        List<String> pathArgs = extractPathArgs(tokens);
        if (first.equals("mv") || first.equals("move")) {
            if (!pathArgs.isEmpty()) {
                String target = resolve(pathArgs.get(pathArgs.size() - 1), root);
                if (target != null && isOutside(root, target)) {
                    return target;
                }
            }
            return null;
        }

        for (String arg : pathArgs) {
            String target = resolve(arg, root);
            if (target != null && isOutside(root, target)) {
                return target;
            }
        }
        return null;
    }

    /** 提取命令的路径参数（跳过选项/flag）。 */
    private static List<String> extractPathArgs(String[] tokens) {
        List<String> args = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (t.startsWith("-") || t.isEmpty()) {
                continue;
            }
            args.add(t);
        }
        return args;
    }

    /**
     * 把命令中的路径参数解析为绝对路径。
     *
     * @return 可判定的绝对路径；含环境变量/通配符无法判定时返回 null（表示需交给审批兜底）
     */
    private static String resolve(String arg, Path root) {
        String clean = stripQuotes(arg);
        if (clean.isEmpty()) {
            return null;
        }
        // 含环境变量无法静态判定 → 放行（交给审批兜底）
        if (containsEnv(clean)) {
            return null;
        }
        try {
            // 通配符路径：取固定前缀根判定（Windows 的 Path 会拒绝含 * 的路径，须先截取前缀）
            if (hasWildcard(clean)) {
                int wcIdx = clean.indexOf('*');
                int qIdx = clean.indexOf('?');
                int bIdx = clean.indexOf('[');
                int idx = smallestPositive(wcIdx, qIdx, bIdx);
                String prefix = clean.substring(0, idx);
                Path p = Path.of(prefix).isAbsolute() ? Path.of(prefix).normalize()
                        : root.resolve(prefix).normalize();
                return p.toString();
            }
            Path p = Path.of(clean);
            if (!p.isAbsolute()) {
                p = root.resolve(p);
            }
            return p.toAbsolutePath().normalize().toString();
        } catch (InvalidPathException e) {
            log.debug("[WorkdirEscapeGuard] 无法解析路径参数: arg={}, error={}", arg, e.getMessage());
            return null;
        }
    }

    /** 判断是否为丢弃输出设备（/dev/null、Windows NUL）。这些不是磁盘写，不应判为越界。 */
    private static boolean isDiscardDevice(String s) {
        String clean = stripQuotes(s.trim());
        if (clean.isEmpty()) {
            return false;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        return lower.equals("/dev/null") || lower.equals("nul")
                || lower.startsWith("/dev/null:") || lower.startsWith("\\\\.\\");
    }

    /** 判断 target 是否在 root 之外（父子关系判断，避免前缀碰撞）。 */
    private static boolean isOutside(Path root, String target) {
        try {
            Path t = Path.of(target);
            if (isWindows()) {
                return !t.normalize().toAbsolutePath().startsWith(root.normalize().toAbsolutePath());
            }
            return !t.normalize().startsWith(root.normalize());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static String stripQuotes(String s) {
        String out = s.trim();
        while (out.length() >= 2
                && ((out.startsWith("\"") && out.endsWith("\""))
                || (out.startsWith("'") && out.endsWith("'")))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static boolean containsEnv(String s) {
        return s.contains("$") || s.contains("%");
    }

    private static boolean hasWildcard(String s) {
        return s.contains("*") || s.contains("?") || s.contains("[");
    }

    private static int smallestPositive(int... vals) {
        int min = Integer.MAX_VALUE;
        for (int v : vals) {
            if (v >= 0 && v < min) {
                min = v;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
