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
