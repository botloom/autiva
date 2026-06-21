package cn.bitloom.agentic.tool.skill;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能调用工具，替代原 SkillManager.buildToolCallback()。
 * <p>
 * 技能信息通过系统提示词注入，此工具仅负责按名称加载技能内容。
 */
@Slf4j
public class SkillTool extends AbstractTool<SkillTool.Input> {

    private static final String DESCRIPTION = """
            执行技能。可用技能列表已在系统提示词中列出。

            如何使用技能：
            - 使用此工具仅传入技能名称调用技能（不带参数）
            - 当你调用技能时，你将看到 <command-message>"{name}"技能正在加载</command-message>
            - 技能的提示将展开并提供关于如何完成任务的详细说明

            重要：
            - 每个技能的响应都会包含其根目录（basePath），执行脚本或引用技能内的文件时必须使用该根目录作为基准路径
            - 仅使用系统提示词中列出的可用技能
            - 不要调用已经在运行的技能
            """;

    private final SkillManager skillManager;

    private SkillTool(SkillManager skillManager) {
        super("Skill", DESCRIPTION, Input.class);
        Assert.notNull(skillManager, "skillManager不能为null");
        this.skillManager = skillManager;
    }

    public record Input(
            @ToolParam(description = "技能名称（不带参数）。例如，\"pdf\"或\"xlsx\"") String command
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] Skill - 调用技能: {}", input.command());
        Skill skill = skillManager.getSkill(input.command());

        if (skill != null) {
            String content = """
                    技能根目录: %s
                    
                    重要：执行脚本或引用技能内的文件时，请使用以上根目录作为基准路径。
                    例如，如果技能内容中提到 script.py，实际路径为: %s/script.py
                    
                    %s
                    """.formatted(skill.basePath(), skill.basePath(), skill.content());
            return ToolResult.success("技能已加载: " + input.command(),
                    Map.of("basePath", skill.basePath()), content);
        }

        return ToolResult.error("未找到技能：" + input.command());
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

        public SkillTool build() {
            return new SkillTool(this.skillManager);
        }
    }
}
