package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessTool implements cn.bitloom.agentic.tool.ITool {

    private final ExecTool execTool;

    @Tool(name = "process_list", description = "列出所有后台进程")
    public ToolResult list() {
        log.info("[ToolCall] process_list - 列出所有后台进程");
        Map<String, ExecTool.ProcessInfo> processes = execTool.getProcesses();

        if (processes.isEmpty()) {
            return ToolResult.success("当前没有运行中的后台进程");
        }

        StringBuilder sb = new StringBuilder("运行中的后台进程：\n\n");
        for (Map.Entry<String, ExecTool.ProcessInfo> entry : processes.entrySet()) {
            ExecTool.ProcessInfo info = entry.getValue();
            boolean isAlive = info.getProcess().isAlive();
            sb.append("- ID: ").append(entry.getKey())
              .append(", 命令: ").append(info.getCommand())
              .append(", 状态: ").append(isAlive ? "运行中" : "已结束")
              .append("\n");
        }

        log.info("[ToolCall] process_list - 查询完成, 共 {} 个进程", processes.size());
        return ToolResult.success("查询成功", sb.toString());
    }

    @Tool(name = "process_kill", description = "终止指定的后台进程")
    public ToolResult kill(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_kill - 终止进程: processId={}", processId);
        Map<String, ExecTool.ProcessInfo> processes = execTool.getProcesses();
        ExecTool.ProcessInfo info = processes.get(processId);

        if (info == null) {
            return ToolResult.failure("错误：未找到进程: " + processId);
        }

        try {
            Process process = info.getProcess();
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
                log.info("[ToolCall] process_kill - 终止成功: processId={}", processId);
                return ToolResult.success("已终止进程: " + processId);
            } else {
                execTool.removeProcess(processId);
                log.info("[ToolCall] process_kill - 进程已结束: processId={}", processId);
                return ToolResult.success("进程已结束: " + processId);
            }
        } catch (Exception e) {
            log.error("[ToolCall] process_kill - 终止失败: processId={}", processId, e);
            return ToolResult.failure("终止进程失败: " + e.getMessage());
        }
    }

    @Tool(name = "process_log", description = "获取进程的输出日志（仅支持正在运行的进程）")
    public ToolResult log(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_log - 获取进程日志: processId={}", processId);
        Map<String, ExecTool.ProcessInfo> processes = execTool.getProcesses();
        ExecTool.ProcessInfo info = processes.get(processId);

        if (info == null) {
            return ToolResult.failure("错误：未找到进程: " + processId);
        }

        Process process = info.getProcess();
        if (!process.isAlive()) {
            return ToolResult.failure("进程已结束，无法获取实时日志");
        }

        try {
            String output = readProcessOutput(process);
            log.info("[ToolCall] process_log - 获取成功: processId={}", processId);
            String result = "进程 " + processId + " 的输出：\n\n" + output;
            return ToolResult.success("获取进程日志成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] process_log - 获取失败: processId={}", processId, e);
            return ToolResult.failure("获取进程日志失败: " + e.getMessage());
        }
    }

    @Tool(name = "process_status", description = "查看进程的详细状态")
    public ToolResult status(@ToolParam(description = "进程ID") String processId) {
        log.info("[ToolCall] process_status - 查看进程状态: processId={}", processId);
        Map<String, ExecTool.ProcessInfo> processes = execTool.getProcesses();
        ExecTool.ProcessInfo info = processes.get(processId);

        if (info == null) {
            return ToolResult.failure("错误：未找到进程: " + processId);
        }

        Process process = info.getProcess();
        boolean isAlive = process.isAlive();
        int exitCode = isAlive ? -1 : process.exitValue();

        log.info("[ToolCall] process_status - 查询完成: processId={}, isAlive={}", processId, isAlive);
        String result = "进程状态: " + processId + "\n" +
               "- 命令: " + info.getCommand() + "\n" +
               "- 状态: " + (isAlive ? "运行中" : "已结束") + "\n" +
               "- 退出码: " + (isAlive ? "N/A" : exitCode);
        return ToolResult.success("查询进程状态成功", result);
    }

    private String readProcessOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 100) {
                sb.append(line).append("\n");
                count++;
            }
        }
        if (sb.isEmpty()) {
            return "(暂无输出)";
        }
        return sb.toString();
    }
}