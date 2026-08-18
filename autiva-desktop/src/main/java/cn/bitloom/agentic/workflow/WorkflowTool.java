package cn.bitloom.agentic.workflow;

import cn.bitloom.agentic.agent.SubAgentFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/**
 * Workflow 执行工具 — 模型入口（对标 learn-claude-code s16）。
 *
 * <p>输入 {@code {name, args, resume_from_run_id?}}：执行编排函数并回报进度；
 * resume 时 journal 语义 key 命中直接回放缓存结果，只有变更过的调用及其下游真正执行。
 * 前台同步执行（编排本身是长任务，调用方等待最终报告）。
 */
@Slf4j
public class WorkflowTool extends AbstractTool<WorkflowTool.Input> {

    private static final String DESCRIPTION_TEMPLATE =
            "执行预置工作流（编排固定，进度自动回报）。可用工作流：\n%s";

    private final WorkflowRegistry registry;
    private final FileSystemSessionManager sessionManager;
    private final SubAgentFactory subAgentFactory;
    private final ToolUIBridge toolUIBridge;

    private WorkflowTool(String description, WorkflowRegistry registry,
            FileSystemSessionManager sessionManager, SubAgentFactory subAgentFactory,
            ToolUIBridge toolUIBridge) {
        super("Workflow", description, Input.class);
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.subAgentFactory = subAgentFactory;
        this.toolUIBridge = toolUIBridge;
    }

    public record Input(
            @ToolParam(description = "工作流名") String name,
            @ToolParam(description = "工作流参数（JSON 对象字符串，如 {\"focus\":\"src/auth\"}；无参数传 {}）") String args,
            @ToolParam(description = "从上次中断的 run 恢复（run ID；可选）", required = false) String resume_from_run_id) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        String projectPath = extractString(toolContext, "projectPath");
        var meta = registry.find(input.name());
        if (meta.isEmpty()) {
            return ToolResult.error("工作流不存在: " + input.name() + "\n可用: " + registry.describeAll());
        }
        Map<String, Object> args = parseArgs(input.args());

        Session session = sessionManager.getById(sessionId);
        if (session == null) {
            return ToolResult.error("会话不存在: " + sessionId);
        }

        boolean resume = input.resume_from_run_id() != null && !input.resume_from_run_id().isBlank();
        String runId = resume ? input.resume_from_run_id() : newRunId();
        String taskId = "workflow." + runId;
        log.info("[Workflow] 启动: name={}, runId={}, resume={}", input.name(), runId, resume);

        try {
            WorkflowJournal journal = resume
                    ? WorkflowJournal.open(sessionId, runId)
                    : WorkflowJournal.create(sessionId, runId);
            if (toolUIBridge != null) {
                String cardSessionId = sessionId;
                javafx.application.Platform.runLater(() -> toolUIBridge.createTaskCard(cardSessionId, taskId,
                        JsonUtils.toJson(Map.of("subagentName", "workflow:" + input.name(),
                                "description", meta.get().description(), "taskId", taskId))));
            }
            // 进度：日志（UI 卡片由会话事件流自然更新，phase/log 落 journal）
            WorkflowContext ctx = new WorkflowContext(session, runId, projectPath, subAgentFactory,
                    journal, msg -> log.info("[Workflow:{}] {}", runId, msg));

            ctx.phase("启动" + (resume ? "（resume from " + runId + "）" : "") + ": " + input.name());
            Object result = meta.get().function().apply(ctx, args);
            String report = result != null ? result.toString() : "（无输出）";
            journal.snapshot(input.name(), "completed");
            if (toolUIBridge != null) {
                javafx.application.Platform.runLater(() -> toolUIBridge.completeTaskCard(taskId, null));
            }
            return ToolResult.success("工作流 " + input.name() + " 完成（run " + runId + "）",
                    Map.of("run_id", runId), report);
        }
        catch (Exception e) {
            log.error("[Workflow] 执行失败: name={}, runId={}", input.name(), runId, e);
            if (toolUIBridge != null) {
                javafx.application.Platform.runLater(() -> toolUIBridge.failTaskCard(taskId, e.getMessage()));
            }
            return ToolResult.error("工作流执行失败（run " + runId + "，可用 resume_from_run_id="
                    + runId + " 恢复）: " + e.getMessage());
        }
    }

    private Map<String, Object> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JsonUtils.fromJson(argsJson,
                    new TypeReference<Map<String, Object>>() {});
            return parsed != null ? parsed : Map.of();
        }
        catch (Exception e) {
            return Map.of();
        }
    }

    private static String newRunId() {
        byte[] bytes = new byte[4];
        new SecureRandom().nextBytes(bytes);
        return "wf_" + HexFormat.of().formatHex(bytes);
    }

    static String extractString(ToolContext context, String key) {
        if (context != null) {
            Object value = context.getContext().get(key);
            if (value instanceof String s) {
                return s;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private WorkflowRegistry registry;
        private FileSystemSessionManager sessionManager;
        private SubAgentFactory subAgentFactory;
        private ToolUIBridge toolUIBridge;

        private Builder() {
        }

        public Builder registry(WorkflowRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder sessionManager(FileSystemSessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder subAgentFactory(SubAgentFactory subAgentFactory) {
            this.subAgentFactory = subAgentFactory;
            return this;
        }

        public Builder toolUIBridge(ToolUIBridge toolUIBridge) {
            this.toolUIBridge = toolUIBridge;
            return this;
        }

        public WorkflowTool build() {
            Assert.notNull(this.registry, "必须提供registry");
            Assert.notNull(this.sessionManager, "必须提供sessionManager");
            Assert.notNull(this.subAgentFactory, "必须提供subAgentFactory");
            String description = DESCRIPTION_TEMPLATE.formatted(registry.describeAll());
            return new WorkflowTool(description, this.registry, this.sessionManager, this.subAgentFactory,
                    this.toolUIBridge);
        }
    }
}
