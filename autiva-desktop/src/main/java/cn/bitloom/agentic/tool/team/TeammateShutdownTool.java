package cn.bitloom.agentic.tool.team;

import cn.bitloom.agentic.team.MailboxService;
import cn.bitloom.agentic.team.TeammateRegistry;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 关闭队友工具（仅 Lead 使用）— 类型化协议校验（requestId + workVersion）。
 *
 * <p>防伪造回复（s13 关键纪律）：携带 work_version 时与注册表比对，不匹配（旧版本请求）
 * 直接拒绝；work→idle 每次转换 workVersion+1，使过期请求自然失效。
 */
@Slf4j
public class TeammateShutdownTool extends AbstractTool<TeammateShutdownTool.Input> {

    private static final String DESCRIPTION = """
            关闭队友（终态，不可恢复）。工作中的队友会完成当前回合后停止。
            关闭前请确认其工作已汇报完毕。""";

    private final TeammateRegistry registry;
    private final MailboxService mailbox;

    private TeammateShutdownTool(TeammateRegistry registry, MailboxService mailbox) {
        super("TeammateShutdown", DESCRIPTION, Input.class);
        this.registry = registry;
        this.mailbox = mailbox;
    }

    public record Input(
            @ToolParam(description = "队友名") String name,
            @ToolParam(description = "请求 ID（类型化协议，防伪造；可选）", required = false) String requestId,
            @ToolParam(description = "关机请求发起时的 workVersion（类型化协议；不匹配则拒绝；可选）", required = false) Long workVersion) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = SpawnTeammateTool.extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        log.info("[ToolCall] TeammateShutdown - name={}, requestId={}, workVersion={}",
                input.name(), input.requestId(), input.workVersion());

        var teammate = registry.get(sessionId, input.name());
        if (teammate.isEmpty() || teammate.get().isShutdown()) {
            return ToolResult.error("队友不存在或已关闭: " + input.name());
        }

        // 类型化协议：workVersion 不匹配说明请求基于过期状态（可能被伪造/重放），拒绝
        if (input.workVersion() != null && input.workVersion() != teammate.get().getWorkVersion()) {
            return ToolResult.error("workVersion 不匹配（请求 " + input.workVersion() + "，当前 "
                    + teammate.get().getWorkVersion() + "），请求已失效，请用 ListTeammates 获取最新状态后重试");
        }

        boolean ok = registry.shutdown(sessionId, input.name());
        if (!ok) {
            return ToolResult.error("关闭失败: " + input.name());
        }
        // 双事件纪律：终态通告独立于结果消息
        mailbox.deliverToLead(sessionId, input.name(), "已关闭（shutdown），不再接收任务");
        return ToolResult.success("已关闭队友 " + input.name()
                + (teammate.get().isWorking() ? "（其当前回合完成后停止）" : ""));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TeammateRegistry registry;
        private MailboxService mailbox;

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

        public TeammateShutdownTool build() {
            Assert.notNull(this.registry, "必须提供registry");
            Assert.notNull(this.mailbox, "必须提供mailbox");
            return new TeammateShutdownTool(this.registry, this.mailbox);
        }
    }
}
