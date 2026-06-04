package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class SkillConfigTool {

    private final SkillManager skillManager;

    private SkillConfigTool(SkillManager skillManager) {
        Assert.notNull(skillManager, "skillManager不能为null");
        this.skillManager = skillManager;
    }

    @Tool(name = "skill_config_list", description = "列出所有已安装的技能，显示技能名称和描述")
    public ToolResult listSkills() {
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
                .data(java.util.Map.of("count", skills.size()))
                .rawOutput(sb.toString())
                .build();
    }

    @Tool(name = "skill_config_get", description = "获取指定技能的详细内容")
    public ToolResult getSkill(
            @ToolParam(description = "技能名称") String name
    ) {
        log.info("[ToolCall] skill_config_get - 获取技能详情: {}", name);
        String content = skillManager.getContent(name);
        if (content == null) {
            return ToolResult.error("技能不存在: " + name);
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("技能详情: " + name)
                .rawOutput(content)
                .build();
    }

    @Tool(name = "skill_config_delete", description = "删除指定技能（破坏性操作，需确认）")
    public ToolResult deleteSkill(
            @ToolParam(description = "要删除的技能名称") String name
    ) {
        log.info("[ToolCall] skill_config_delete - 删除技能: {}", name);
        try {
            skillManager.deleteSkill(name);
            return ToolResult.success("技能已删除: " + name);
        } catch (Exception e) {
            log.error("[ToolCall] skill_config_delete - 删除失败: {}", name, e);
            return ToolResult.error("删除技能失败: " + e.getMessage());
        }
    }

    @Tool(name = "skill_config_reload", description = "重新加载所有技能配置（修改技能文件后使用）")
    public ToolResult reloadSkills() {
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

        public Builder skillManager(SkillManager skillManager) {
            this.skillManager = skillManager;
            return this;
        }

        public SkillConfigTool build() {
            return new SkillConfigTool(skillManager);
        }
    }
}
