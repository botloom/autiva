package cn.bitloom.agentic.tool.team;

import cn.bitloom.agentic.team.MailboxService;
import cn.bitloom.agentic.team.TeammateRuntime;
import cn.bitloom.agentic.team.TeammateRegistry;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 发送消息工具（Lead 与队友通用）— MessageBus 邮箱投递。
 *
 * <p>to=队友名：消息投递到该队友 branch（其下次唤醒时随上下文加载），并异步触发唤醒；
 * to=lead：root + notification 事件，主智能体下一轮自动消费（push 模型）。
 */
@Slf4j
public class SendMessageTool extends AbstractTool<SendMessageTool.Input> {

    private static final String DESCRIPTION = """
            向队友或 lead 发送消息（邮箱投递）。
            to=队友名：队友异步唤醒并处理；to=lead：主智能体下一轮收到通知。
            汇报结果时说清楚做了什么、验证了什么。""";

    private final MailboxService mailbox;
    private final TeammateRegistry registry;
    private final TeammateRuntime.Waker waker;

    private SendMessageTool(MailboxService mailbox, TeammateRegistry registry, TeammateRuntime.Waker waker) {
        super("SendMessage", DESCRIPTION, Input.class);
        this.mailbox = mailbox;
        this.registry = registry;
        this.waker = waker;
    }

    public record Input(
            @ToolParam(description = "收件人：队友名或 lead") String to,
            @ToolParam(description = "消息正文（自包含，包含所有必要上下文）") String message) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = SpawnTeammateTool.extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        if (input.to() == null || input.to().isBlank()) {
            return ToolResult.error("缺少收件人（to）");
        }
        log.info("[ToolCall] SendMessage - to={}", input.to());

        String sender = resolveSender(toolContext);

        if (MailboxService.LEAD_ADDRESS.equals(input.to())) {
            mailbox.deliverToLead(sessionId, sender, input.message());
            return ToolResult.success("已投递给 lead（主智能体下一轮对话可见）");
        }

        var teammate = registry.get(sessionId, input.to());
        if (teammate.isEmpty()) {
            return ToolResult.error("队友不存在: " + input.to() + "，用 ListTeammates 查看活跃队友");
        }
        if (teammate.get().isShutdown()) {
            return ToolResult.error("队友已关闭: " + input.to());
        }
        mailbox.deliverToTeammate(sessionId, sender, input.to(), input.message());
        waker.wake(sessionId, input.to());
        return ToolResult.success("已投递给队友 " + input.to()
                + (teammate.get().isWorking() ? "（其当前回合结束后处理）" : "（已触发唤醒）"));
    }

    /** 发件人：root 事件上下文 = lead；branch 上下文 = 队友名（从 branch 解析） */
    private String resolveSender(ToolContext toolContext) {
        if (toolContext != null) {
            Object branch = toolContext.getContext().get("branch");
            if (branch instanceof String b && b.startsWith("teammate.")) {
                return b.substring("teammate.".length());
            }
        }
        return MailboxService.LEAD_ADDRESS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailboxService mailbox;
        private TeammateRegistry registry;
        private TeammateRuntime.Waker waker;

        private Builder() {
        }

        public Builder mailbox(MailboxService mailbox) {
            this.mailbox = mailbox;
            return this;
        }

        public Builder registry(TeammateRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder waker(TeammateRuntime.Waker waker) {
            this.waker = waker;
            return this;
        }

        public SendMessageTool build() {
            Assert.notNull(this.mailbox, "必须提供mailbox");
            Assert.notNull(this.registry, "必须提供registry");
            Assert.notNull(this.waker, "必须提供waker");
            return new SendMessageTool(this.mailbox, this.registry, this.waker);
        }
    }
}
