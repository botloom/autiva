package cn.bitloom.agentic.permission.command;

import cn.bitloom.agentic.permission.model.CommandClass;
import cn.bitloom.agentic.tool.command.CommandSafety;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 命令分类器 — 把命令分为 READ / WRITE / DESTRUCTIVE 三类，供批准策略使用。
 *
 * <p>分类策略（优先级从高到低）：
 * <ol>
 *   <li>复用 {@link CommandSafety} 的危险检测 → DESTRUCTIVE</li>
 *   <li>复用 {@link CommandSafety} 的 warning 检测 → WRITE</li>
 *   <li>写文件重定向（{@code >} / {@code >>}）→ WRITE</li>
 *   <li>READ 命令白名单（首 token）→ READ</li>
 *   <li>多级命令子命令白名单（git/npm/mvn/gradle/pip 等）→ READ 或 WRITE</li>
 *   <li>默认 → WRITE（宁可误判弹框也不漏判）</li>
 * </ol>
 */
public final class CommandClassifier {

    /** 单 token 只读命令（Windows + Unix 混合） */
    private static final Set<String> READ_COMMANDS = Set.of(
            // Windows
            "dir", "type", "where", "ver", "whoami", "hostname", "cd", "pwd",
            // Unix
            "ls", "cat", "find", "grep", "which", "uname", "date", "cal",
            "head", "tail", "wc", "sort", "uniq", "cut", "tr", "tee",
            "echo", "print", "printf",  // echo 本身只输出，重定向另算
            "env", "printenv",
            "ps", "top",  // top 是交互式但只读
            "df", "du", "free", "uptime",
            "ipconfig", "ifconfig", "netstat", "ping", "traceroute",
            "man", "help"
    );

    /** git 子命令：只读 */
    private static final Set<String> GIT_READ_SUB = Set.of(
            "status", "log", "diff", "show", "branch", "blame", "annotate",
            "ls-files", "ls-remote", "rev-parse", "reflog", "describe",
            "remote", "config", "tag", "stash", "list", "shortlog",
            "rev-list", "cat-file", "name-rev", "symbolic-ref", "for-each-ref",
            "fsck", "gc"
    );

    /** git 子命令：写操作 */
    private static final Set<String> GIT_WRITE_SUB = Set.of(
            "add", "commit", "push", "pull", "fetch",  // fetch 改本地引用，算写
            "reset", "checkout", "merge", "rebase", "cherry-pick", "revert",
            "rm", "mv", "init", "clone", "apply", "am",
            "clean", "prune", "update-ref", "update-server-info"
    );

    /** npm 子命令：只读 */
    private static final Set<String> NPM_READ_SUB = Set.of(
            "list", "ls", "info", "view", "outdated", "audit", "search",
            "explain", "why", "org", "team", "profile", "token", "ping",
            "config", "get", "set"  // set 算写? 暂归 read, 避免过度弹框
    );

    /** npm 子命令：写操作 */
    private static final Set<String> NPM_WRITE_SUB = Set.of(
            "install", "i", "uninstall", "un", "remove", "rm", "update", "upgrade",
            "publish", "unpublish", "deprecate", "dist-tag", "owner",
            "access", "bin", "ci", "link", "create", "init", "exec", "run",
            "start", "stop", "restart", "test"  // run/start/test 可能执行任意脚本
    );

    /** mvn 子命令：只读（仅编译/验证，不改部署） */
    private static final Set<String> MVN_READ_SUB = Set.of(
            "compile", "test-compile", "test", "verify", "validate",
            "dependency:tree", "dependency:list", "dependency:analyze",
            "help:effective-pom", "help:active-profiles",
            "versions:display-dependency-updates"
    );

    /** mvn 子命令：写 */
    private static final Set<String> MVN_WRITE_SUB = Set.of(
            "clean", "install", "package", "deploy", "site",
            "release:prepare", "release:perform",
            "versions:set", "versions:use-latest-releases"
    );

    /** gradle 子命令：只读 */
    private static final Set<String> GRADLE_READ_SUB = Set.of(
            "tasks", "projects", "dependencies", "dependencyInsight",
            "properties", "help", "model", "buildEnvironment",
            "components"
    );

    /** gradle 子命令：写 */
    private static final Set<String> GRADLE_WRITE_SUB = Set.of(
            "build", "clean", "assemble", "assembleDist", "buildOwl",
            "check", "test", "jar", "war", "zip", "tar",
            "uploadArchives", "publish", "bootJar", "bootWar",
            "init", "wrapper"
    );

    /** pip 子命令：只读 */
    private static final Set<String> PIP_READ_SUB = Set.of(
            "list", "show", "freeze", "search", "index"
    );

    /** pip 子命令：写 */
    private static final Set<String> PIP_WRITE_SUB = Set.of(
            "install", "uninstall", "download", "wheel", "hash"
    );

    /** docker 子命令：只读 */
    private static final Set<String> DOCKER_READ_SUB = Set.of(
            "ps", "images", "logs", "inspect", "stats", "top", "port",
            "history", "diff", "events", "version", "info", "search"
    );

    /** docker 子命令：写 */
    private static final Set<String> DOCKER_WRITE_SUB = Set.of(
            "run", "exec", "create", "rm", "rmi", "build", "push", "pull",
            "stop", "kill", "restart", "pause", "unpause",
            "tag", "save", "load", "import", "export",
            "network", "volume", "swarm", "service", "stack",
            "system", "container", "image", "secret", "plugin", "trust"
    );

    /** 写文件重定向模式（> file / >> file） */
    private static final Pattern REDIRECT_WRITE = Pattern.compile(">{1,2}\\s*\\S");

    private CommandClassifier() {
    }

    /**
     * 分类命令。
     *
     * @param command 原始命令（可能含参数、管道、重定向）
     * @return 分类结果（含规则描述，用于 UI 展示）
     */
    public static Classification classify(String command) {
        if (command == null || command.isBlank()) {
            return new Classification(CommandClass.READ, "空命令");
        }

        // 1. 复用 CommandSafety 危险检测
        CommandSafety.SafetyCheck safety = CommandSafety.check(command);
        if (safety.isDestructive()) {
            return new Classification(CommandClass.DESTRUCTIVE,
                    "匹配危险模式: " + safety.pattern());
        }
        if (safety.isWarning()) {
            return new Classification(CommandClass.WRITE,
                    "匹配告警规则: " + safety.rule());
        }

        // 2. 写文件重定向
        if (REDIRECT_WRITE.matcher(command).find()) {
            return new Classification(CommandClass.WRITE, "命令包含文件重定向 (>)");
        }

        // 3. 取首 token（忽略前导 @echo off / sudo 等修饰）
        String[] tokens = tokenize(command);
        if (tokens.length == 0) {
            return new Classification(CommandClass.READ, "空命令");
        }

        // 处理 cmd 前缀修饰：@echo off & ... → 取 & 后的命令
        int startIdx = 0;
        if (tokens[0].startsWith("@") || tokens[0].equalsIgnoreCase("sudo")) {
            startIdx = 1;
            if (startIdx >= tokens.length) {
                return new Classification(CommandClass.WRITE, "修饰符后无命令");
            }
        }

        String first = tokens[startIdx].toLowerCase();
        String second = startIdx + 1 < tokens.length ? tokens[startIdx + 1].toLowerCase() : null;

        // 4. 单 token 命令在 READ 白名单
        if (second == null && READ_COMMANDS.contains(first)) {
            return new Classification(CommandClass.READ, "只读命令: " + first);
        }

        // 5. 多级命令子命令白名单
        CommandClass subClass = classifyMultiCommand(first, second);
        if (subClass != null) {
            String reason = subClass == CommandClass.READ
                    ? first + " " + second + " 是只读操作"
                    : first + " " + second + " 是写操作";
            return new Classification(subClass, reason);
        }

        // 6. 默认 WRITE（宁可误判弹框也不漏判）
        return new Classification(CommandClass.WRITE, "默认按写操作处理");
    }

    /**
     * 多级命令（git/npm/mvn 等）子命令分类。
     *
     * @return 分类结果；null 表示该命令不在多级命令白名单中
     */
    private static CommandClass classifyMultiCommand(String first, String second) {
        if (second == null) {
            // 只有命令本身没有子命令：git/npm/gradle 等无参数调用通常是只读（显示帮助）
            if (Set.of("git", "npm", "mvn", "gradle", "pip", "docker", "kubectl").contains(first)) {
                return CommandClass.READ;
            }
            return null;
        }

        // 去除子命令前的 - 前缀（如 npm -v）
        String sub = second.startsWith("-") ? null : second;

        return switch (first) {
            case "git" -> sub == null ? CommandClass.READ
                    : GIT_READ_SUB.contains(sub) ? CommandClass.READ
                    : GIT_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case "npm", "pnpm", "yarn" -> sub == null ? CommandClass.READ
                    : NPM_READ_SUB.contains(sub) ? CommandClass.READ
                    : NPM_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case "mvn", "mvnw" -> sub == null ? CommandClass.READ
                    : MVN_READ_SUB.contains(sub) ? CommandClass.READ
                    : MVN_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case "gradle", "gradlew" -> sub == null ? CommandClass.READ
                    : GRADLE_READ_SUB.contains(sub) ? CommandClass.READ
                    : GRADLE_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case "pip", "pip3", "pipx" -> sub == null ? CommandClass.READ
                    : PIP_READ_SUB.contains(sub) ? CommandClass.READ
                    : PIP_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case "docker" -> sub == null ? CommandClass.READ
                    : DOCKER_READ_SUB.contains(sub) ? CommandClass.READ
                    : DOCKER_WRITE_SUB.contains(sub) ? CommandClass.WRITE : CommandClass.WRITE;
            case null, default -> null;
        };
    }

    /**
     * 简单分词：按空白切分，去除引号。
     * 委托 {@link CommandLineParser#tokenize} 统一逻辑。
     */
    private static String[] tokenize(String command) {
        return CommandLineParser.tokenize(command);
    }

    /**
     * 分类结果。
     *
     * @param commandClass 分类
     * @param reason       原因（用于 UI 展示）
     */
    public record Classification(CommandClass commandClass, String reason) {
    }
}
