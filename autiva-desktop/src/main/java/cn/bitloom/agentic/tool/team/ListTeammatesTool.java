package cn.bitloom.agentic.tool.team;

import cn.bitloom.agentic.team.TeammateRecord;
import cn.bitloom.agentic.team.TeammateRegistry;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 列出队友工具 — 名字 / 状态（spawned/work/idle/shutdown 不展示）/ 职责 / workVersion。
 */
@Slf4j
public class ListTeammatesTool extends AbstractTool<ListTeammatesTool.Input> {

    private static final String DESCRIPTION = """
            列出当前会话的所有活跃队友（名字、状态、职责）。
            状态含义：work=执行中，idle=空闲可接受任务。""";

    private final TeammateRegistry registry;

    private ListTeammatesTool(TeammateRegistry registry) {
        super("ListTeammates", DESCRIPTION, Input.class);
        this.registry = registry;
    }

    public record Input(@ToolParam(description = "预留参数（无实际用途）", required = false) String unused) {
    }

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        String sessionId = SpawnTeammateTool.extractString(toolContext, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ToolResult.error("无法解析会话ID");
        }
        List<TeammateRecord> teammates = registry.listActive(sessionId);
        if (teammates.isEmpty()) {
            return ToolResult.success("当前没有活跃队友。用 SpawnTeammate 创建。");
        }
        StringBuilder sb = new StringBuilder("活跃队友（" + teammates.size() + "）：\n");
        for (TeammateRecord teammate : teammates) {
            sb.append("- ").append(teammate.getName())
                    .append(" [").append(teammate.getStatus()).append("]")
                    .append("（").append(teammate.getDefinition()).append("）")
                    .append(": ").append(teammate.getDescription().isBlank() ? "通用协作" : teammate.getDescription())
                    .append("\n");
        }
        sb.append("用 SendMessage(to=队友名) 发消息；to=lead 发给主智能体（队友视角）。");
        return ToolResult.success(sb.toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TeammateRegistry registry;

        private Builder() {
        }

        public Builder registry(TeammateRegistry registry) {
            this.registry = registry;
            return this;
        }

        public ListTeammatesTool build() {
            Assert.notNull(this.registry, "必须提供registry");
            return new ListTeammatesTool(this.registry);
        }
    }
}
