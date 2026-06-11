package cn.bitloom.agentic.tool.manage.skill;

import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 删除指定技能的工具（破坏性操作，需确认）。
 */
@Slf4j
public class SkillConfigDeleteTool extends AbstractTool<SkillConfigDeleteTool.Input> {

    private static final String DESCRIPTION = "删除指定技能（破坏性操作，需确认）";

    private final SkillManager skillManager;

    private SkillConfigDeleteTool(SkillManager skillManager) {
        super("skill_config_delete", DESCRIPTION, Input.class);
        Assert.notNull(skillManager, "skillManager不能为null");
        this.skillManager = skillManager;
    }

    public record Input(
            @ToolParam(description = "要删除的技能名称") String name
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] skill_config_delete - 删除技能: {}", input.name());
        try {
            skillManager.deleteSkill(input.name());
            return ToolResult.success("技能已删除: " + input.name());
        } catch (Exception e) {
            log.error("[ToolCall] skill_config_delete - 删除失败: {}", input.name(), e);
            return ToolResult.error("删除技能失败: " + e.getMessage());
        }
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

        public SkillConfigDeleteTool build() {
            return new SkillConfigDeleteTool(this.skillManager);
        }
    }
}
