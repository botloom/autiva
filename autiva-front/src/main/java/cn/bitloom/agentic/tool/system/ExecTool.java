package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
@Component
public class ExecTool implements cn.bitloom.agentic.tool.ITool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_LINES = 1000;

    private final Map<String, ProcessInfo> processes = new ConcurrentHashMap<>();

    @Tool(name = "exec", description = "在宿主机上执行 shell 命令。支持后台运行、设置超时时间。")
    public ToolResult exec(@ToolParam(description = "要执行的命令") String command,
                      @ToolParam(description = "是否在后台运行，默认为 false", required = false) Boolean background,
                      @ToolParam(description = "超时时间（秒），默认 60 秒", required = false) Integer timeoutSeconds) {
        log.info("[ToolCall] exec - 执行命令: command={}, background={}", command, background);
        if (StringUtils.isBlank(command)) {
            return ToolResult.failure("错误：命令不能为空");
        }

        boolean isBackground = Boolean.TRUE.equals(background);
        int timeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }
            processBuilder.redirectErrorStream(true);

            if (isBackground) {
                ProcessBuilder bgBuilder = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    bgBuilder.command("cmd.exe", "/c", "start", "cmd.exe", "/c", command);
                } else {
                    bgBuilder.command("bash", "-c", command + " &");
                }
                Process process = bgBuilder.start();
                String processId = "proc-" + System.currentTimeMillis();
                processes.put(processId, new ProcessInfo(processId, command, process));
                log.info("[ToolCall] exec - 后台进程启动: processId={}, command={}", processId, command);
                String result = "已在后台启动进程: " + processId + "\n命令: " + command;
                return ToolResult.success("后台进程启动成功", result);
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                boolean timedOut = !process.waitFor(timeout, TimeUnit.SECONDS);

                if (timedOut) {
                    process.destroyForcibly();
                    log.info("[ToolCall] exec - 命令超时: command={}", command);
                    return ToolResult.failure("命令执行超时（" + timeout + " 秒）\n\n" +
                           "提示：对于长时间运行的命令，请使用 background=true 参数在后台运行");
                }

                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < MAX_OUTPUT_LINES) {
                    output.append(line).append("\n");
                    lineCount++;
                }

                if (lineCount >= MAX_OUTPUT_LINES) {
                    output.append("\n... [输出已截断，剩余行数未显示] ...\n");
                }
            }

            int exitCode = process.exitValue();
            log.info("[ToolCall] exec - 命令完成: command={}, exitCode={}", command, exitCode);

            String result = "命令: " + command + "\n" +
                           "退出码: " + exitCode + "\n\n" +
                           "输出:\n" + output;

            if (exitCode != 0) {
                result += "\n注意：命令以非零退出码结束";
                return ToolResult.success("命令执行完成（非零退出码）", result);
            }

            return ToolResult.success("命令执行成功", result);

        } catch (Exception e) {
            log.error("[ToolCall] exec - 执行失败: command={}", command, e);
            return ToolResult.failure("执行命令失败: " + e.getMessage());
        }
    }

    public ProcessInfo getProcess(String processId) {
        return processes.get(processId);
    }

    public void removeProcess(String processId) {
        processes.remove(processId);
    }

    public static class ProcessInfo {
        private final String id;
        private final String command;
        private final Process process;

        public ProcessInfo(String id, String command, Process process) {
            this.id = id;
            this.command = command;
            this.process = process;
        }

        public String getId() { return id; }
        public String getCommand() { return command; }
        public Process getProcess() { return process; }
    }
}