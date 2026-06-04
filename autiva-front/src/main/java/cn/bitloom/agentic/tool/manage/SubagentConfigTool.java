package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.agent.AgentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class SubagentConfigTool {

    private final AgentManager agentManager;

    private SubagentConfigTool(AgentManager agentManager) {
        Assert.notNull(agentManager, "agentManager不能为null");
        this.agentManager = agentManager;
    }

    @Tool(name = "subagent_config_list", description = "列出所有子智能体配置")
    public ToolResult listSubagents() {
        log.info("[ToolCall] subagent_config_list - 列出子智能体");
        var subagents = agentManager.listSubagents();
        if (subagents.isEmpty()) {
            return ToolResult.success("当前没有配置任何子智能体。");
        }
        StringBuilder sb = new StringBuilder("子智能体列表：\n\n");
        for (var info : subagents) {
            sb.append("- **").append(info.name()).append("** (类型: ").append(info.type()).append(")\n");
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message(subagents.size() + " 个子智能体")
                .data(java.util.Map.of("count", subagents.size()))
                .rawOutput(sb.toString())
                .build();
    }

    @Tool(name = "subagent_config_get", description = "获取指定子智能体的配置内容")
    public ToolResult getSubagent(
            @ToolParam(description = "子智能体名称") String name
    ) {
        log.info("[ToolCall] subagent_config_get - 获取子智能体配置: {}", name);
        String content = agentManager.getSubagentContent(name);
        if (content == null) {
            return ToolResult.error("子智能体不存在或不是子智能体类型: " + name);
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("子智能体配置: " + name)
                .rawOutput(content)
                .build();
    }

    @Tool(name = "subagent_config_create", description = "创建新的子智能体配置")
    public ToolResult createSubagent(
            @ToolParam(description = "子智能体名称") String name,
            @ToolParam(description = "子智能体描述") String description,
            @ToolParam(description = "子智能体配置内容（Markdown格式，包含YAML前置元数据）") String content
    ) {
        log.info("[ToolCall] subagent_config_create - 创建子智能体: {}", name);
        try {
            agentManager.createSubagentConfig(name, description, content);
            return ToolResult.success("子智能体已创建: " + name);
        } catch (Exception e) {
            log.error("[ToolCall] subagent_config_create - 创建失败: {}", name, e);
            return ToolResult.error("创建子智能体失败: " + e.getMessage());
        }
    }

    @Tool(name = "subagent_config_save", description = "保存/更新子智能体配置内容")
    public ToolResult saveSubagent(
            @ToolParam(description = "子智能体名称") String name,
            @ToolParam(description = "完整的子智能体配置内容（Markdown格式）") String content
    ) {
        log.info("[ToolCall] subagent_config_save - 保存子智能体配置: {}", name);
        try {
            agentManager.saveSubagentConfig(name, content);
            return ToolResult.success("子智能体配置已保存: " + name);
        } catch (Exception e) {
            log.error("[ToolCall] subagent_config_save - 保存失败: {}", name, e);
            return ToolResult.error("保存子智能体配置失败: " + e.getMessage());
        }
    }

    @Tool(name = "subagent_config_delete", description = "删除子智能体配置（破坏性操作，需确认）")
    public ToolResult deleteSubagent(
            @ToolParam(description = "要删除的子智能体名称") String name
    ) {
        log.info("[ToolCall] subagent_config_delete - 删除子智能体: {}", name);
        try {
            agentManager.deleteSubagentConfig(name);
            return ToolResult.success("子智能体已删除: " + name);
        } catch (Exception e) {
            log.error("[ToolCall] subagent_config_delete - 删除失败: {}", name, e);
            return ToolResult.error("删除子智能体失败: " + e.getMessage());
        }
    }

    @Tool(name = "subagent_config_reload", description = "重新加载所有子智能体配置")
    public ToolResult reloadSubagents() {
        log.info("[ToolCall] subagent_config_reload - 重新加载子智能体");
        try {
            agentManager.reloadSubagents();
            return ToolResult.success("子智能体配置已重新加载。");
        } catch (Exception e) {
            log.error("[ToolCall] subagent_config_reload - 重新加载失败", e);
            return ToolResult.error("重新加载子智能体失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentManager agentManager;

        public Builder agentManager(AgentManager agentManager) {
            this.agentManager = agentManager;
            return this;
        }

        public SubagentConfigTool build() {
            return new SubagentConfigTool(agentManager);
        }
    }
}
