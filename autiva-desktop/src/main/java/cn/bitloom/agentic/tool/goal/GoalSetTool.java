package cn.bitloom.agentic.tool.goal;

import cn.bitloom.agentic.goal.GoalManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 设置目标工具（对标 learn-claude-code s17 Goal Loop 入口）。
 *
 * <p>目标三要素：结束状态 + 验证方式 + 限制条件，如
 * 「pytest tests/auth 退出码为 0，且 lint 无错误」。
 * 目标激活后，每轮对话结束由独立判断器复核达成情况，未达成会自动续轮推进。
 */
@Slf4j
public class GoalSetTool extends AbstractTool<GoalSetTool.Input> {

    private static final String DESCRIPTION = """
            设置一个本轮任务的目标。设置后系统会用独立判断器在每轮对话结束时复核目标是否真实达成，\
            未达成会自动驱动你继续推进，直到达成、确认无法达成或达到连续阻止上限。\
            goal 必须包含三要素：结束状态（做成什么样算完成）、验证方式（用什么命令/检查验证）、\
            限制条件（如不许新建文件、只改某个模块）。示例："pytest tests/auth 全部通过（退出码为 0），\
            且 ruff check 无报错；只允许修改 src/auth 目录下的文件"。""";

    private static final String CONFIRM_HINT = """
            目标已激活。系统将在每轮对话结束时用独立判断器复核目标是否真实达成，未达成会自动驱动你继续。
            重要：运行验证命令后，必须把命令与结果明确写进对话（供独立判断器检查），不要只口头宣称成功。""";

    private final GoalManager goalManager;

    private GoalSetTool(GoalManager goalManager) {
        super("GoalSet", DESCRIPTION, Input.class);
        this.goalManager = goalManager;
    }

    public record Input(@ToolParam(description = "目标描述：结束状态 + 验证方式 + 限制条件") String goal) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        if (input.goal() == null || input.goal().isBlank()) {
            return ToolResult.error("goal 不能为空：需包含结束状态、验证方式、限制条件");
        }
        goalManager.setGoal(sessionId, input.goal().trim());
        return ToolResult.success("目标已设置。\n\n" + CONFIRM_HINT);
    }

    static String extractString(ToolContext context, String key) {
        if (context != null && context.getContext() != null) {
            Object value = context.getContext().get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GoalManager goalManager;

        private Builder() {
        }

        public Builder goalManager(GoalManager goalManager) {
            this.goalManager = goalManager;
            return this;
        }

        public GoalSetTool build() {
            Assert.notNull(this.goalManager, "必须提供goalManager");
            return new GoalSetTool(this.goalManager);
        }
    }
}
