package cn.bitloom.agentic.tool.command.shell;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Windows cmd.exe 执行器。
 *
 * <p>使用 {@code cmd.exe /v:on /c} 单次执行模式：
 * <ul>
 *   <li>{@code /v:on} 启用延迟变量展开，用于 {@code !ERRORLEVEL!} 和 {@code !CD!} 捕获</li>
 *   <li>{@code /c} 执行完毕后自动退出</li>
 *   <li>{@code chcp 65001} 切换 UTF-8 codepage</li>
 *   <li>{@code redirectErrorStream(true)} 合并 stderr 到 stdout</li>
 * </ul>
 *
 * <p>灵感来源：Trae Agent 的 Windows _BashSession（cmd.exe /v:on + !errorlevel! + sentinel）。
 */
@Slf4j
public class WindowsShellExecutor implements ShellExecutor {

    @Override
    public ProcessBuilder createProcessBuilder(String wrappedCommand, String workdir, Map<String, String> env) {
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/v:on", "/c", wrappedCommand);
        if (workdir != null && !workdir.isEmpty()) {
            pb.directory(new File(workdir));
        }
        pb.redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }
        return pb;
    }

    @Override
    public String wrapCommand(String command, String workdir) {
        String markerId = generateMarkerId();
        StringBuilder sb = new StringBuilder();
        // 切换到 UTF-8 codepage
        sb.append("chcp 65001 > nul 2>&1 & ");
        // 导航到工作目录
        if (workdir != null && !workdir.isEmpty()) {
            sb.append("cd /d \"").append(workdir).append("\" & ");
        }
        // 用户命令
        sb.append(command);
        // marker 行：捕获退出码和当前目录
        // ^|^| 转义为 ||（避免与 Windows 驱动器号冲突）
        sb.append(" & echo ").append(markerId).append("^|^|!ERRORLEVEL!^|^|!CD!");
        return sb.toString();
    }

    @Override
    public ProcessBuilder createPersistentShellBuilder(Map<String, String> env) {
        // /v:on 启用延迟变量展开（!ERRORLEVEL! / !CD!）
        // /q 关闭命令回显（关键：不关闭的话 cmd 会把 stdin 写入的命令回显到 stdout，
        //   而回显的命令文本包含 markerId，会导致 stdout reader 误判命令已完成）
        // /k 执行 chcp 后保持会话存活
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/v:on", "/q", "/k", "chcp 65001 >nul 2>&1");
        pb.directory(new File(System.getProperty("user.home")));
        pb.redirectErrorStream(false); // stdout 与 stderr 分离（v13）
        if (env != null) {
            pb.environment().putAll(env);
        }
        return pb;
    }

    @Override
    public String wrapPersistentCommand(String command, String markerId) {
        // 关键：用户命令与 marker 用换行分隔（而非 & 连接）。
        // 原因：cmd 的 & 连接符在遇到 if/for/括号块 时会把后续内容吞进块内解析，
        //   例如 "if exist X (echo A) else (echo B) & echo MARKER" 中的 & echo MARKER
        //   会被 cmd 当作 else 块的延续，导致 marker 永远不输出，stdout reader 阻塞。
        // 换行让 cmd 逐行执行：先执行用户命令，再单独执行 marker 输出行。
        // 用 set /a 捕获 ERRORLEVEL（避免 if/for 块内 ERRORLEVEL 被覆盖），
        // !ERRORLEVEL! 在下一行仍可访问（cmd 在每行结束后才更新 ERRORLEVEL）。
        return "@echo off\r\n" + command + "\r\necho " + markerId + "^|^|!ERRORLEVEL!^|^|!CD!";
    }

    @Override
    public ParseResult parseOutput(String rawOutput, String markerId, String fallbackCwd, boolean timedOut) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return new ParseResult("", -1, nvl(fallbackCwd));
        }

        int idx = rawOutput.indexOf(markerId);
        if (idx < 0) {
            log.debug("[WindowsShellExecutor] marker '{}' not found in output", markerId);
            return new ParseResult(rawOutput, -1, nvl(fallbackCwd));
        }

        String output = rawOutput.substring(0, idx);
        int lineEnd = rawOutput.indexOf('\n', idx);
        String markerLine = lineEnd >= 0 ? rawOutput.substring(idx, lineEnd) : rawOutput.substring(idx);

        // Split on "||" (avoids conflict with Windows drive letters like C:\)
        String[] parts = markerLine.split("\\|\\|");
        int exitCode = -1;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            try {
                exitCode = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                log.debug("[WindowsShellExecutor] bad exit code: {}", parts[1]);
            }
        }
        String cwd = parts.length > 2 ? parts[2].trim() : nvl(fallbackCwd);

        // 清理输出中的 \r\n 和 ANSI 转义
        String cleaned = output.replace("\r\n", "\n")
                .replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .strip();

        return new ParseResult(cleaned, exitCode, cwd);
    }

    @Override
    public Map<String, String> filterEnv(Map<String, String> env) {
        String path = env.get("PATH");
        if (path == null) path = System.getenv("PATH");
        if (path == null) return env;

        StringBuilder filtered = new StringBuilder();
        for (String entry : path.split(";")) {
            if (!entry.toLowerCase().contains("\\windowsapps")) {
                if (!filtered.isEmpty()) filtered.append(';');
                filtered.append(entry);
            }
        }
        Map<String, String> result = new LinkedHashMap<>(env);
        result.put("PATH", filtered.toString());
        return result;
    }

    @Override
    public String platformName() {
        return "Windows (cmd.exe)";
    }

    @Override
    public boolean isWindows() {
        return true;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
