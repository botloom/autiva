package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台进程管理工具，从 CommandTools 的 Process 方法拆出。
 * <p>
 * 支持列出、轮询、日志、写入、终止和清除后台进程。
 * </p>
 */
public class ProcessTool extends AbstractTool<ProcessTool.Input> {

    private static final String DESCRIPTION = """
            管理后台进程。操作：list(列表)、poll(增量输出)、log(完整输出)、write(写 stdin)、kill(终止)、clear(清除已完成)。配合 Command 后台模式使用。
            """;

    private final ProcessManager processManager;

    public record Input(
            @ToolParam(description = "动作：list / poll / log / write / kill / clear") String action,
            @ToolParam(description = "后台进程 ID（list 不需要）", required = false) String sessionId,
            @ToolParam(description = "write 动作要发送的数据", required = false) String data,
            @ToolParam(description = "log 动作的偏移行号，默认 0", required = false) Integer offset,
            @ToolParam(description = "log 动作返回的行数，默认 200", required = false) Integer limit
    ) {}

    private ProcessTool(Builder builder) {
        super("Process", DESCRIPTION, Input.class);
        this.processManager = builder.processManager;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        return switch (input.action()) {
            case "list" -> handleList();
            case "poll" -> handlePoll(input.sessionId());
            case "log" -> handleLog(input.sessionId(), input.offset(), input.limit());
            case "write" -> handleWrite(input.sessionId(), input.data());
            case "kill" -> handleKill(input.sessionId());
            case "clear" -> handleClear(input.sessionId());
            default -> ToolResult.error("未知动作: " + input.action() + "。支持: list / poll / log / write / kill / clear");
        };
    }

    private ToolResult handleList() {
        ProcessManager.ProcessListResult result = processManager.list();
        if (result.processes().isEmpty()) {
            return ToolResult.success("没有后台进程");
        }
        StringBuilder rawSb = new StringBuilder();
        rawSb.append("后台进程列表:\n\n");
        for (ProcessManager.ProcessInfo info : result.processes()) {
            rawSb.append("- ").append(info.sessionId())
                    .append(" [").append(info.status()).append(']');
            if (info.exitCode() != null) {
                rawSb.append(" exit=").append(info.exitCode());
            }
            rawSb.append(" name=").append(info.name());
            rawSb.append(" elapsed=").append(info.elapsedMs() / 1000).append('s');
            if (info.waitingForInput()) {
                rawSb.append(" ⚠ waiting_for_input");
            }
            rawSb.append('\n');
        }
        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(result.processes().size() + " 个后台进程")
                .data("count", result.processes().size())
                .rawOutput(rawSb.toString())
                .build();
    }

    private ToolResult handlePoll(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("poll 需要 session_id 参数");
        }
        ProcessManager.ProcessSnapshot snap = processManager.poll(sessionId, 0);
        if (snap.isError()) {
            return ToolResult.error(snap.error());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("status", snap.status());
        if (snap.exitCode() != null) {
            data.put("exit_code", snap.exitCode());
        }
        if (snap.elapsedMs() != null) {
            data.put("elapsed_ms", snap.elapsedMs());
        }

        String rawOutput = buildRawOutput(data,
                snap.output() != null && !snap.output().isEmpty() ? snap.output() : null);
        if (snap.output() == null || snap.output().isEmpty()) {
            rawOutput = rawOutput.replace("\n(no output)", "\n(no new output)");
        }

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(sessionId + " - " + snap.status())
                .data(data)
                .rawOutput(rawOutput)
                .build();
    }

    private ToolResult handleLog(String sessionId, Integer offset, Integer limit) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("log 需要 session_id 参数");
        }
        int off = offset != null ? offset : 0;
        int lim = limit != null ? limit : 200;
        ProcessManager.ProcessLog logResult = processManager.log(sessionId, off, lim);
        if (logResult.isError()) {
            return ToolResult.error(logResult.error());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("status", logResult.status());
        if (logResult.exitCode() != null) {
            data.put("exit_code", logResult.exitCode());
        }
        data.put("lines", logResult.returnedLines() + "/" + logResult.totalLines());
        data.put("offset", logResult.offset());
        if (logResult.waitingForInput()) {
            data.put("waiting_for_input", true);
        }

        StringBuilder rawSb = new StringBuilder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            rawSb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (logResult.waitingForInput()) {
            rawSb.append("⚠ waiting_for_input: true\n");
        }
        String output = logResult.output();
        if (output != null && !output.isEmpty()) {
            rawSb.append('\n').append(output);
        }
        if (logResult.offset() + logResult.returnedLines() < logResult.totalLines()) {
            rawSb.append("\n--- 更多行: Process(action=\"log\", session_id=\"").append(sessionId)
                    .append("\", offset=").append(logResult.offset() + logResult.returnedLines())
                    .append(") ---");
        }

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message(sessionId + " - " + logResult.returnedLines() + "/" + logResult.totalLines() + " 行")
                .data(data)
                .rawOutput(rawSb.toString())
                .build();
    }

    private ToolResult handleWrite(String sessionId, String data) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("write 需要 session_id 参数");
        }
        if (data == null || data.isEmpty()) {
            return ToolResult.error("write 需要 data 参数");
        }
        boolean ok = processManager.write(sessionId, data);
        if (ok) {
            return ToolResult.success("已发送输入到 " + sessionId);
        }
        return ToolResult.error("发送失败：未找到 " + sessionId + " 或进程已退出");
    }

    private ToolResult handleKill(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("kill 需要 session_id 参数");
        }
        boolean ok = processManager.kill(sessionId);
        if (ok) {
            return ToolResult.success("已终止 " + sessionId);
        }
        return ToolResult.error("未找到 " + sessionId);
    }

    private ToolResult handleClear(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ToolResult.error("clear 需要 session_id 参数");
        }
        boolean ok = processManager.clear(sessionId);
        if (ok) {
            return ToolResult.success("已清除 " + sessionId);
        }
        return ToolResult.error("清除失败：未找到 " + sessionId + " 或进程仍在运行");
    }

    /**
     * 构建键值对格式的rawOutput字符串，带可选的输出部分。
     */
    private static String buildRawOutput(Map<String, Object> entries, String output) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        if (output != null && !output.isEmpty()) {
            sb.append("\noutput:\n").append(output);
        } else {
            sb.append("\n(no output)");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ProcessManager processManager;

        private Builder() {
        }

        public Builder processManager(ProcessManager processManager) {
            this.processManager = processManager;
            return this;
        }

        public ProcessTool build() {
            if (this.processManager == null) {
                this.processManager = new ProcessManager();
            }
            return new ProcessTool(this);
        }
    }

}
