package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.skill.SkillManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillTool implements ITool {

    private final SkillManager skillManager;

    @Tool(name = "loadSkill", description = "按名称载入专业知识")
    public ToolResult load(@ToolParam(description = "技能名称") String name) {
        log.info("[ToolCall] loadSkill - 加载技能: {}", name);
        try {
            String result = skillManager.getContent(name);
            if (result == null) {
                return ToolResult.failure("未找到技能: " + name);
            }
            log.info("[ToolCall] loadSkill - 加载完成, 内容长度: {}", result.length());
            return ToolResult.success("加载技能成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] loadSkill - 加载失败: {}", name, e);
            return ToolResult.failure("加载技能失败: " + e.getMessage());
        }
    }

}
