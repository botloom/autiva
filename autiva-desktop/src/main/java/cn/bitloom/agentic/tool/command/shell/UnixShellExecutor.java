package cn.bitloom.agentic.tool.command.shell;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;

/**
 * Unix bash 执行器。
 *
 * <p>使用 {@code bash -c} 单次执行模式：
 * <ul>
 *   <li>{@code -c} 执行命令字符串后自动退出</li>
 *   <li>退出码通过 {@code $?} 捕获</li>
 *   <li>当前目录通过 {@code $(pwd)} 捕获</li>
 *   <li>{@code redirectErrorStream(true)} 合并 stderr 到 stdout</li>
 * </ul>
 *
 * <p>灵感来源：Trae Agent 的 Unix _BashSession（/bin/bash + $? + sentinel）。
 */
@Slf4j
public class UnixShellExecutor implements ShellExecutor {

    @Override
    public ProcessBuilder createProcessBuilder(String wrappedCommand, String workdir, Map<String, String> env) {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", wrappedCommand);
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
        if (workdir != null && !workdir.isEmpty()) {
            sb.append("cd '").append(workdir.replace("'", "'\\''")).append("' ; ");
        }
        sb.append(command);
        sb.append(" ; echo '").append(markerId).append("||$?||$(pwd)'");
        return sb.toString();
    }

    @Override
    public ParseResult parseOutput(String rawOutput, String markerId, String fallbackCwd, boolean timedOut) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return new ParseResult("", -1, nvl(fallbackCwd));
        }

        int idx = rawOutput.indexOf(markerId);
        if (idx < 0) {
            log.debug("[UnixShellExecutor] marker '{}' not found in output", markerId);
            return new ParseResult(rawOutput, -1, nvl(fallbackCwd));
        }

        String output = rawOutput.substring(0, idx);
        int lineEnd = rawOutput.indexOf('\n', idx);
        String markerLine = lineEnd >= 0 ? rawOutput.substring(idx, lineEnd) : rawOutput.substring(idx);

        String[] parts = markerLine.split("\\|\\|");
        int exitCode = -1;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            try {
                exitCode = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                log.debug("[UnixShellExecutor] bad exit code: {}", parts[1]);
            }
        }
        String cwd = parts.length > 2 ? parts[2].trim() : nvl(fallbackCwd);

        String cleaned = output.replace("\r\n", "\n")
                .replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .strip();

        return new ParseResult(cleaned, exitCode, cwd);
    }

    @Override
    public String platformName() {
        return "Unix (bash)";
    }

    @Override
    public boolean isWindows() {
        return false;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
