package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Getter
@Component
public class ExecTool implements cn.bitloom.agentic.tool.ITool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_LINES = 1000;
    private static final int MAX_OUTPUT_BYTES = 100 * 1024;
    private static final int MAX_LINE_LENGTH = 500;

    private final Map<String, ProcessInfo> processes = new ConcurrentHashMap<>();
    private final AtomicInteger processCounter = new AtomicInteger(0);

    @Tool(name = "exec", description = "在宿主机上执行 shell 命令。支持后台运行、设置超时时间、工作目录设置。")
    public ToolResult exec(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "是否在后台运行，默认为 false", required = false) Boolean background,
            @ToolParam(description = "超时时间（秒），默认 60 秒", required = false) Integer timeoutSeconds,
            @ToolParam(description = "工作目录，默认当前目录", required = false) String workingDir) {
        
        log.info("[ToolCall] exec - 执行命令: command={}, background={}, workingDir={}", command, background, workingDir);
        
        if (StringUtils.isBlank(command)) {
            return ToolResult.failure("错误：命令不能为空");
        }

        boolean isBackground = Boolean.TRUE.equals(background);
        int timeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        try {
            ProcessBuilder processBuilder = createProcessBuilder(command, workingDir);
            processBuilder.redirectErrorStream(true);

            if (isBackground) {
                return executeBackground(processBuilder, command, timeout);
            }

            return executeForeground(processBuilder, command, timeout);

        } catch (Exception e) {
            log.error("[ToolCall] exec - 执行失败: command={}", command, e);
            return ToolResult.failure("执行命令失败: " + e.getMessage());
        }
    }

    private ProcessBuilder createProcessBuilder(String command, String workingDir) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }

        if (StringUtils.isNotBlank(workingDir)) {
            File workDir = new File(workingDir);
            if (workDir.exists() && workDir.isDirectory()) {
                processBuilder.directory(workDir);
            }
        }

        Map<String, String> env = processBuilder.environment();
        env.put("LANG", "en_US.UTF-8");
        env.put("LC_ALL", "en_US.UTF-8");

        return processBuilder;
    }

    private ToolResult executeBackground(ProcessBuilder processBuilder, String command, int timeout) throws Exception {
        Process process = processBuilder.start();
        String processId = generateProcessId();
        
        ProcessInfo processInfo = new ProcessInfo(processId, command, process, processBuilder.directory());
        processes.put(processId, processInfo);
        
        log.info("[ToolCall] exec - 后台进程启动: processId={}, command={}", processId, command);
        
        Thread outputThread = new Thread(() -> {
            try {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (output.length() > MAX_OUTPUT_BYTES) {
                            output.append("\n... [输出过长，已截断] ...");
                            break;
                        }
                    }
                }
                processInfo.setOutput(output.toString());
                processInfo.setCompleted(true);
                processInfo.setExitCode(process.exitValue());
                log.info("[ToolCall] exec - 后台进程完成: processId={}, exitCode={}", processId, process.exitValue());
            } catch (Exception e) {
                log.error("[ToolCall] exec - 后台进程异常: processId={}", processId, e);
                processInfo.setError(e.getMessage());
                processInfo.setCompleted(true);
            }
        }, "process-" + processId);
        outputThread.setDaemon(true);
        outputThread.start();

        String result = String.format("已在后台启动进程\n- 进程ID: %s\n- 命令: %s\n- 超时: %d秒\n\n" +
                "提示：使用 process_status 查看状态，process_log 获取输出，process_kill 终止进程",
                processId, command, timeout);
        return ToolResult.success("后台进程启动成功", result);
    }

    private ToolResult executeForeground(ProcessBuilder processBuilder, String command, int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        StringBuilder truncatedOutput = new StringBuilder();
        int lineCount = 0;
        boolean truncated = false;
        long totalBytes = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            boolean timedOut = !process.waitFor(timeout, TimeUnit.SECONDS);

            if (timedOut) {
                process.destroyForcibly();
                log.info("[ToolCall] exec - 命令超时: command={}", command);
                return ToolResult.failure(
                    String.format("命令执行超时（%d 秒）\n\n" +
                        "提示：对于长时间运行的命令，请使用 background=true 参数在后台运行",
                        timeout));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                totalBytes += line.getBytes(StandardCharsets.UTF_8).length;
                
                if (!truncated) {
                    String truncatedLine = truncateLine(line);
                    truncatedOutput.append(truncatedLine).append("\n");
                    lineCount++;
                    
                    if (lineCount >= MAX_OUTPUT_LINES || truncatedOutput.length() > MAX_OUTPUT_BYTES) {
                        truncated = true;
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int exitCode = process.exitValue();
        log.info("[ToolCall] exec - 命令完成: command={}, exitCode={}, duration={}ms", command, exitCode, duration);

        StringBuilder result = new StringBuilder();
        result.append(String.format("命令: %s\n", command));
        result.append(String.format("退出码: %d\n", exitCode));
        result.append(String.format("执行时间: %dms\n", duration));
        if (truncated) {
            result.append(String.format("输出大小: %d bytes (已截断)\n\n", totalBytes));
        } else {
            result.append("\n");
        }
        result.append("输出:\n").append(truncatedOutput);
        
        if (truncated) {
            result.append("\n... [输出已截断，显示前 ").append(lineCount).append(" 行] ...");
        }

        if (exitCode != 0) {
            result.append("\n\n注意：命令以非零退出码结束");
        }

        return ToolResult.success(
            exitCode == 0 ? "命令执行成功" : "命令执行完成（非零退出码）",
            result.toString());
    }

    @Tool(name = "process_list", description = "列出所有后台进程")
    public ToolResult listProcesses() {
        log.info("[ToolCall] process_list - 列出所有后台进程");
        
        if (processes.isEmpty()) {
            return ToolResult.success("没有后台进程", "当前没有运行中的后台进程");
        }

        StringBuilder result = new StringBuilder("后台进程列表:\n\n");
        for (ProcessInfo info : processes.values()) {
            result.append(String.format("- ID: %s\n", info.getId()));
            result.append(String.format("  命令: %s\n", info.getCommand()));
            result.append(String.format("  状态: %s\n", info.isCompleted() ? "已完成" : "运行中"));
            if (info.isCompleted()) {
                result.append(String.format("  退出码: %d\n", info.getExitCode()));
            }
            result.append("\n");
        }

        log.info("[ToolCall] process_list - 查询完成, 共 {} 个进程", processes.size());
        return ToolResult.success("查询成功", result.toString());
    }

    @Tool(name = "process_status", description = "查看进程详细状态")
    public ToolResult processStatus(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_status - 查看进程状态: processId={}", processId);
        
        ProcessInfo info = processes.get(processId);
        if (info == null) {
            return ToolResult.failure("错误：进程不存在: " + processId);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("进程ID: %s\n", info.getId()));
        result.append(String.format("命令: %s\n", info.getCommand()));
        result.append(String.format("状态: %s\n", info.isCompleted() ? "已完成" : "运行中"));
        result.append(String.format("工作目录: %s\n", info.getWorkingDir()));
        
        if (info.isCompleted()) {
            result.append(String.format("退出码: %d\n", info.getExitCode()));
            if (info.getError() != null) {
                result.append(String.format("错误: %s\n", info.getError()));
            }
        }

        return ToolResult.success("查询成功", result.toString());
    }

    @Tool(name = "process_log", description = "获取进程输出日志")
    public ToolResult processLog(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_log - 获取进程日志: processId={}", processId);
        
        ProcessInfo info = processes.get(processId);
        if (info == null) {
            return ToolResult.failure("错误：进程不存在: " + processId);
        }

        if (!info.isCompleted()) {
            return ToolResult.success("进程运行中", "进程仍在运行中，输出尚未完成");
        }

        String output = info.getOutput();
        if (output == null || output.isEmpty()) {
            return ToolResult.success("无输出", "进程没有输出");
        }

        return ToolResult.success("获取日志成功", "进程输出:\n" + output);
    }

    @Tool(name = "process_kill", description = "终止进程")
    public ToolResult killProcess(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_kill - 终止进程: processId={}", processId);
        
        ProcessInfo info = processes.get(processId);
        if (info == null) {
            return ToolResult.failure("错误：进程不存在: " + processId);
        }

        if (info.isCompleted()) {
            return ToolResult.failure("错误：进程已完成，无需终止");
        }

        Process process = info.getProcess();
        process.destroyForcibly();
        info.setCompleted(true);
        info.setExitCode(-1);
        
        log.info("[ToolCall] process_kill - 终止成功: processId={}", processId);
        return ToolResult.success("进程已终止", "已强制终止进程: " + processId);
    }

    private String generateProcessId() {
        return "proc-" + System.currentTimeMillis() + "-" + processCounter.incrementAndGet();
    }

    private String truncateLine(String line) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        return line.substring(0, MAX_LINE_LENGTH) + "... [截断]";
    }

    @Getter
    public static class ProcessInfo {
        private final String id;
        private final String command;
        private final Process process;
        private final File workingDir;
        @Setter
        private volatile boolean completed = false;
        @Setter
        private volatile int exitCode = -1;
        @Setter
        private volatile String output;
        @Setter
        private volatile String error;

        public ProcessInfo(String id, String command, Process process, File workingDir) {
            this.id = id;
            this.command = command;
            this.process = process;
            this.workingDir = workingDir;
        }

    }
}
