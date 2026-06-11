package cn.bitloom.agentic.tool.manage.skill;

import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 重新加载所有技能配置的工具。
 */
@Slf4j
public class SkillConfigReloadTool extends AbstractTool<SkillConfigReloadTool.Input> {

    private static final String DESCRIPTION = "重新加载所有技能配置（修改技能文件后使用）";

    private final SkillManager skillManager;

    private SkillConfigReloadTool(SkillManager skillManager) {
        super("skill_config_reload", DESCRIPTION, Input.class);
        Assert.notNull(skillManager, "skillManager不能为null");
        this.skillManager = skillManager;
    }

    /**
     * 由于 FunctionToolCallback 需要至少有一个字段才能正确生成 JSON Schema，
     * 定义一个可选的 dummy 参数。
     */
    public record Input(
            @ToolParam(description = "无参数", required = false) String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] skill_config_reload - 重新加载技能");
        try {
            skillManager.loadSkills();
            return ToolResult.success("技能已重新加载，当前共 " + skillManager.getAllSkills().size() + " 个技能。");
        } catch (Exception e) {
            log.error("[ToolCall] skill_config_reload - 重新加载失败", e);
            return ToolResult.error("重新加载技能失败: " + e.getMessage());
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

        public SkillConfigReloadTool build() {
            return new SkillConfigReloadTool(this.skillManager);
        }
    }
}
