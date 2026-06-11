package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.WorkspaceConfig;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.command.CommandTool;
import cn.bitloom.agentic.tool.command.ProcessTool;
import cn.bitloom.agentic.tool.cron.CronCreateTool;
import cn.bitloom.agentic.tool.cron.CronDeleteTool;
import cn.bitloom.agentic.tool.cron.CronListTool;
import cn.bitloom.agentic.tool.cron.CronTriggerTool;
import cn.bitloom.agentic.tool.file.EditTool;
import cn.bitloom.agentic.tool.file.ReadTool;
import cn.bitloom.agentic.tool.file.WriteTool;
import cn.bitloom.agentic.tool.interaction.AskUserQuestionTool;
import cn.bitloom.agentic.tool.interaction.TodoWriteTool;
import cn.bitloom.agentic.tool.manage.app.AppConfigGetTool;
import cn.bitloom.agentic.tool.manage.app.AppConfigPathTool;
import cn.bitloom.agentic.tool.manage.app.AppConfigReadTool;
import cn.bitloom.agentic.tool.manage.app.AppConfigSetIsolationTool;
import cn.bitloom.agentic.tool.manage.mcp.McpConfigListTool;
import cn.bitloom.agentic.tool.manage.mcp.McpConfigPathTool;
import cn.bitloom.agentic.tool.manage.mcp.McpConfigUpdateTool;
import cn.bitloom.agentic.tool.manage.memory.MemoryManageDeleteTool;
import cn.bitloom.agentic.tool.manage.memory.MemoryManageListTool;
import cn.bitloom.agentic.tool.manage.memory.MemoryManageReadTool;
import cn.bitloom.agentic.tool.manage.memory.MemoryManageWriteTool;
import cn.bitloom.agentic.tool.manage.skill.SkillConfigDeleteTool;
import cn.bitloom.agentic.tool.manage.skill.SkillConfigGetTool;
import cn.bitloom.agentic.tool.manage.skill.SkillConfigListTool;
import cn.bitloom.agentic.tool.manage.skill.SkillConfigReloadTool;
import cn.bitloom.agentic.tool.search.BochaSearchProvider;
import cn.bitloom.agentic.tool.search.GlobTool;
import cn.bitloom.agentic.tool.search.GrepTool;
import cn.bitloom.agentic.tool.search.WebSearchTool;
import cn.bitloom.agentic.tool.skill.SkillTool;
import cn.bitloom.agentic.tool.task.TaskOutputTool;
import cn.bitloom.agentic.tool.task.TaskTool;
import cn.bitloom.agentic.tool.web.WebFetchTool;
import cn.bitloom.agentic.util.GuiQuestionHandler;
import cn.bitloom.agentic.util.GuiTodoEventHandler;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.cron.CronManager;
import cn.bitloom.store.ToolUIBridge;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具容器，统一管理工具注册和构建。
 * <p>
 * 注册所有可用工具，根据智能体配置（白名单/黑名单）选择工具。
 */
@Slf4j
@Component
public class Toolkit {

    private final SkillManager skillManager;
    private final ModelFactory modelFactory;
    private final SessionManager sessionManager;
    private final ConfigManager configManager;
    private final CronManager cronManager;
    private final ToolUIBridge toolUIBridge;
    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public Toolkit(SkillManager skillManager,
                   ModelFactory modelFactory,
                   @Lazy SessionManager sessionManager,
                   ConfigManager configManager,
                   CronManager cronManager,
                   ToolUIBridge toolUIBridge,
                   AsyncMcpToolCallbackProvider mcpToolCallbackProvider) {
        this.skillManager = skillManager;
        this.modelFactory = modelFactory;
        this.sessionManager = sessionManager;
        this.configManager = configManager;
        this.cronManager = cronManager;
        this.toolUIBridge = toolUIBridge;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    /**
     * 根据 AgentDefinition 构建工具回调列表
     */
    public List<ToolCallback> buildToolCallbacks(AgentDefinition definition) {
        // 1. 构建所有工具（不分 scope）
        List<ToolCallback> callbacks = new ArrayList<>(
            buildAllTools().stream().map(AbstractTool::toToolCallback).toList()
        );

        // 2. 追加 MCP 工具
        callbacks.addAll(List.of(mcpToolCallbackProvider.getToolCallbacks()));

        // 3. 根据配置过滤
        return filterByConfig(callbacks, definition);
    }

    /**
     * 构建所有可用工具
     */
    private List<AbstractTool<?>> buildAllTools() {
        List<AbstractTool<?>> tools = new ArrayList<>();

        // 文件操作
        tools.add(ReadTool.builder().build());
        tools.add(WriteTool.builder().build());
        tools.add(EditTool.builder().build());
        // 搜索
        tools.add(GlobTool.builder().build());
        tools.add(GrepTool.builder().build());
        if (configManager.getBochaApiKey() != null && !configManager.getBochaApiKey().isEmpty()) {
            tools.add(WebSearchTool.builder(new BochaSearchProvider(configManager.getBochaApiKey())).build());
        }
        // 网页
        tools.add(WebFetchTool.builder().build());
        // 命令执行
        tools.add(CommandTool.builder().build());
        tools.add(ProcessTool.builder().build());
        // 交互
        tools.add(AskUserQuestionTool.builder()
                .questionHandler(new GuiQuestionHandler(toolUIBridge)).build());
        tools.add(TodoWriteTool.builder()
                .todoEventHandler(new GuiTodoEventHandler(toolUIBridge)).build());
        // 定时任务
        tools.add(CronCreateTool.builder().cronManager(cronManager).build());
        tools.add(CronListTool.builder().cronManager(cronManager).build());
        tools.add(CronDeleteTool.builder().cronManager(cronManager).build());
        tools.add(CronTriggerTool.builder().cronManager(cronManager).build());
        // 任务
        tools.add(TaskTool.builder()
                .toolkit(this)
                .modelFactory(modelFactory)
                .skillManager(skillManager)
                .sessionManager(sessionManager)
                .toolUIBridge(toolUIBridge).build());
        tools.add(TaskOutputTool.builder()
                .taskRepository(new cn.bitloom.agentic.task.repository.DefaultTaskRepository()).build());
        // 记忆管理
        tools.add(MemoryManageListTool.builder().build());
        tools.add(MemoryManageReadTool.builder().build());
        tools.add(MemoryManageWriteTool.builder().build());
        tools.add(MemoryManageDeleteTool.builder().build());
        // 技能
        tools.add(SkillTool.builder().skillManager(skillManager).build());
        // 技能配置
        tools.add(SkillConfigListTool.builder().skillManager(skillManager).build());
        tools.add(SkillConfigGetTool.builder().skillManager(skillManager).build());
        tools.add(SkillConfigDeleteTool.builder().skillManager(skillManager).build());
        tools.add(SkillConfigReloadTool.builder().skillManager(skillManager).build());
        // MCP 配置
        tools.add(McpConfigListTool.builder().build());
        tools.add(McpConfigPathTool.builder().build());
        tools.add(McpConfigUpdateTool.builder().build());
        // 应用配置
        tools.add(AppConfigGetTool.builder().configManager(configManager).build());
        tools.add(AppConfigPathTool.builder().configManager(configManager).build());
        tools.add(AppConfigReadTool.builder().configManager(configManager).build());
        tools.add(AppConfigSetIsolationTool.builder().configManager(configManager).build());

        return tools;
    }

    /**
     * 根据配置过滤工具列表
     */
    private List<ToolCallback> filterByConfig(List<ToolCallback> callbacks, AgentDefinition definition) {
        // 主智能体：从 WorkspaceConfig 加载白名单
        if (definition.kind() == AgentKind.MAIN) {
            WorkspaceConfig config = loadWorkspaceConfig(definition.name());
            List<String> whitelist = config.getTools();
            if (whitelist != null && !whitelist.isEmpty()) {
                Set<String> allowed = Set.copyOf(whitelist);
                return callbacks.stream()
                    .filter(tc -> allowed.contains(tc.getToolDefinition().name()))
                    .toList();
            }
            return callbacks;
        }

        // 子智能体：白名单 + 黑名单
        List<ToolCallback> result = callbacks;
        if (definition.tools() != null && !definition.tools().isEmpty()) {
            Set<String> allowed = Set.copyOf(definition.tools());
            result = result.stream()
                .filter(tc -> allowed.contains(tc.getToolDefinition().name()))
                .toList();
        }
        if (definition.disallowedTools() != null && !definition.disallowedTools().isEmpty()) {
            Set<String> disallowed = Set.copyOf(definition.disallowedTools());
            result = result.stream()
                .filter(tc -> !disallowed.contains(tc.getToolDefinition().name()))
                .toList();
        }
        return result;
    }

    public WorkspaceConfig loadWorkspaceConfig(String agentId) {
        WorkspaceConfig rootConfig = loadConfigFromFile(AppConstants.Base.AGENT_CONFIG_FILE);
        WorkspaceConfig agentConfig = loadConfigFromFile(AppConstants.Base.agentConfigFile(agentId));

        WorkspaceConfig merged = new WorkspaceConfig();
        merged.setTools(agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()
                ? agentConfig.getTools() : rootConfig.getTools());
        merged.setMcpServers(agentConfig.getMcpServers() != null && !agentConfig.getMcpServers().isEmpty()
                ? agentConfig.getMcpServers() : rootConfig.getMcpServers());
        merged.setSkills(agentConfig.getSkills() != null && !agentConfig.getSkills().isEmpty()
                ? agentConfig.getSkills() : rootConfig.getSkills());
        return merged;
    }

    private WorkspaceConfig loadConfigFromFile(Path configFile) {
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                return JSON.parseObject(content, WorkspaceConfig.class);
            } catch (IOException e) {
                log.error("读取配置文件失败: {}", configFile, e);
            }
        }
        return new WorkspaceConfig();
    }
}
