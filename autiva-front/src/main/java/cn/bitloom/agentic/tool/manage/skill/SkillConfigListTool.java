package cn.bitloom.agentic.tool.manage.skill;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * 列出所有已安装技能的工具。
 */
@Slf4j
public class SkillConfigListTool extends AbstractTool<SkillConfigListTool.Input> {

    private static final String DESCRIPTION = "列出所有已安装的技能，显示技能名称和描述";

    private final SkillManager skillManager;

    private SkillConfigListTool(SkillManager skillManager) {
        super("skill_config_list", DESCRIPTION, Input.class);
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
        log.info("[ToolCall] skill_config_list - 列出所有技能");
        var skills = skillManager.getAllSkills();
        if (skills.isEmpty()) {
            return ToolResult.success("当前没有安装任何技能。");
        }
        StringBuilder sb = new StringBuilder("已安装技能列表：\n\n");
        for (Skill skill : skills) {
            sb.append("- **").append(skill.name()).append("**: ").append(skill.description()).append("\n");
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message(skills.size() + " 个已安装技能")
                .data(Map.of("count", skills.size()))
                .rawOutput(sb.toString())
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

        public SkillConfigListTool build() {
            return new SkillConfigListTool(this.skillManager);
        }
    }
}
