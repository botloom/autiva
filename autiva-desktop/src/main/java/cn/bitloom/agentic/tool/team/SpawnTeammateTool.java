package cn.bitloom.agentic.tool.team;

import cn.bitloom.agentic.team.MailboxService;
import cn.bitloom.agentic.team.TeammateRecord;
import cn.bitloom.agentic.team.TeammateRegistry;
import cn.bitloom.agentic.team.TeammateRuntime;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.askuser.AskUserQuestionTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * 创建队友工具（仅 Lead 使用）— spawn 前先经用户确认（AskUserQuestion 同款机制）。
 *
 * <p>队友 = 长生命周期子智能体：专属 branch {@code teammate.{name}}，跨任务保留上下文，
 * 通过 MessageBus 邮箱双向通信，空闲时自动轮询共享任务板认领任务。
 */
@Slf4j
public class SpawnTeammateTool extends AbstractTool<SpawnTeammateTool.Input> {

    private static final String DESCRIPTION = """
            创建一个持久队友（长生命周期协作智能体）。队友跨任务保留上下文，可接收消息、
            认领共享任务板任务、向 lead 汇报结果。创建前会请求用户确认。
            适合需要多轮往返协作的并行工作流；一次性独立任务用 Task 工具更合适。""";

    private final TeammateRegistry registry;
    private final MailboxService mailbox;
    private final TeammateRuntime.Waker waker;
    private final AskUserQuestionTool.QuestionHandler questionHandler;

    private SpawnTeammateTool(TeammateRegistry registry, MailboxService mailbox, TeammateRuntime.Waker waker,
            AskUserQuestionTool.QuestionHandler questionHandler) {
        super("SpawnTeammate", DESCRIPTION, Input.class);
        this.registry = registry;
        this.mailbox = mailbox;
        this.waker = waker;
        this.questionHandler = questionHandler;
    }

    public record Input(
            @ToolParam(description = "队友名（唯一标识，字母数字下划线连字符）") String name,
            @ToolParam(description = "队友职责描述（一句话，注入其系统提示）") String description,
            @ToolParam(description = "初始任务指令（队友启动后的第一件工作）") String initial_prompt,
            @ToolParam(description = "底层智能体类型（可选，默认 General）", required = false) String definition) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        String projectPath = extractString(toolContext, "projectPath");
        log.info("[ToolCall] SpawnTeammate - name={}, definition={}", input.name(), input.definition());

        // 用户确认（spawn 是重操作：长生命周期智能体 + 持续 token 消耗）
        if (questionHandler != null) {
            Map<String, String> answers = questionHandler.handle(
                    List.of(new AskUserQuestionTool.Question(
                            "是否创建队友「%s」？（%s）".formatted(input.name(),
                                    input.description() != null ? input.description() : "通用协作"),
                            "创建队友",
                            List.of(new AskUserQuestionTool.Question.Option("确认创建", "创建该队友并开始工作"),
                                    new AskUserQuestionTool.Question.Option("取消", "不创建，改用其它方式")),
                            false)),
                    sessionId);
            String answer = answers != null
                    ? answers.values().stream().findFirst().orElse("确认创建")
                    : "确认创建";
            if (answer != null && answer.contains("取消")) {
                return ToolResult.success("用户取消了创建队友 " + input.name());
            }
        }

        try {
            TeammateRecord record = registry.spawn(sessionId, input.name(), input.description(),
                    input.definition(), projectPath);
            // 初始任务投递到邮箱并异步唤醒
            mailbox.deliverToTeammate(sessionId, MailboxService.LEAD_ADDRESS, input.name(),
                    input.initial_prompt());
            waker.wake(sessionId, input.name());
            return ToolResult.success("已创建队友 " + input.name() + "（" + record.getDefinition() + "），"
                    + "初始任务已投递。用 ListTeammates 查看状态，SendMessage 发消息，TeammateShutdown 关闭。");
        }
        catch (IllegalArgumentException | IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        }
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
        private TeammateRegistry registry;
        private MailboxService mailbox;
        private TeammateRuntime.Waker waker;
        private AskUserQuestionTool.QuestionHandler questionHandler;

        private Builder() {
        }

        public Builder registry(TeammateRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder mailbox(MailboxService mailbox) {
            this.mailbox = mailbox;
            return this;
        }

        public Builder waker(TeammateRuntime.Waker waker) {
            this.waker = waker;
            return this;
        }

        public Builder questionHandler(AskUserQuestionTool.QuestionHandler questionHandler) {
            this.questionHandler = questionHandler;
            return this;
        }

        public SpawnTeammateTool build() {
            Assert.notNull(this.registry, "必须提供registry");
            Assert.notNull(this.mailbox, "必须提供mailbox");
            Assert.notNull(this.waker, "必须提供waker");
            return new SpawnTeammateTool(this.registry, this.mailbox, this.waker, this.questionHandler);
        }
    }
}
