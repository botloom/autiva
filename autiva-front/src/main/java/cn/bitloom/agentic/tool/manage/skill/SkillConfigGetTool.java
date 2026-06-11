package cn.bitloom.agentic.tool.manage.skill;

import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 获取指定技能详细内容的工具。
 */
@Slf4j
public class SkillConfigGetTool extends AbstractTool<SkillConfigGetTool.Input> {

    private static final String DESCRIPTION = "获取指定技能的详细内容";

    private final SkillManager skillManager;

    private SkillConfigGetTool(SkillManager skillManager) {
        super("skill_config_get", DESCRIPTION, Input.class);
        Assert.notNull(skillManager, "skillManager不能为null");
        this.skillManager = skillManager;
    }

    public record Input(
            @ToolParam(description = "技能名称") String name
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] skill_config_get - 获取技能详情: {}", input.name());
        String content = skillManager.getContent(input.name());
        if (content == null) {
            return ToolResult.error("技能不存在: " + input.name());
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("技能详情: " + input.name())
                .rawOutput(content)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SkillManager skillManager;

        private Builder() {}

        public Builder skillManager(SkillManager skillManager) {
            this.skillManager = skillManager;
            return this;
        }

        public SkillConfigGetTool build() {
            return new SkillConfigGetTool(this.skillManager);
        }
    }
}
