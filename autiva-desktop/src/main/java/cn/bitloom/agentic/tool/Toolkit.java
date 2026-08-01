package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.command.CommandTool;
import cn.bitloom.agentic.tool.command.ProcessManager;
import cn.bitloom.agentic.tool.command.ProcessTool;
import cn.bitloom.agentic.tool.cron.CronCreateTool;
import cn.bitloom.agentic.tool.cron.CronDeleteTool;
import cn.bitloom.agentic.tool.cron.CronListTool;
import cn.bitloom.agentic.tool.cron.CronTriggerTool;
import cn.bitloom.agentic.tool.file.DiffGenerator;
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
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.agentic.cron.CronManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 工具容器，统一管理工具注册和构建。
 * <p>
 * 注册所有可用工具，根据智能体配置（白名单/黑名单）选择工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Toolkit {

    private final SkillManager skillManager;
    private final ConfigManager configManager;
    private final CronManager cronManager;
    private final ToolUIBridge toolUIBridge;
    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final TaskRepository taskRepository;
    private final ProcessManager processManager;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final ObjectProvider<DiffGenerator> diffGeneratorProvider;
    private final ObjectProvider<Toolkit> selfProvider;

    @PostConstruct
    public void init() {

    }

    /**
     * 根据 AgentDefinition 构建工具回调列表
     */
    public List<ToolCallback> buildToolCallbacks(AgentDefinition definition) {
        // 1. 构建所有工具（不含 TaskTool，TaskTool 按 agent 配置单独创建）
        List<ToolCallback> callbacks = new ArrayList<>(
            buildAllTools().stream().map(AbstractTool::toToolCallback).toList()
        );

        // 2. 追加 MCP 工具
        callbacks.addAll(List.of(mcpToolCallbackProvider.getToolCallbacks()));

        // 3. 根据配置过滤
        List<ToolCallback> filtered = filterByConfig(callbacks, definition);

        // 4. 按 agent 配置创建 TaskTool（如果该 agent 允许使用 Task 工具）
        if (isTaskToolAllowed(definition)) {
            TaskTool taskTool = TaskTool.builder()
                    .taskRepository(taskRepository)
                    .toolUIBridge(toolUIBridge)
                    .sessionManager(fileSystemSessionManager)
                    .definitionManager(definitionManager)
                    .modelFactory(modelFactory)
                    .toolkit(selfProvider.getIfAvailable())
                    .skillManager(skillManager)
                    .build();
            filtered = new ArrayList<>(filtered);
            filtered.add(taskTool.toToolCallback());
        }

        return filtered;
    }

    /**
     * 检查 agent 是否允许使用 Task 工具
     * - MAIN 智能体：检查 definition.tools 白名单是否包含 "Task"（已合并 config.json）
     * - SUBAGENT 智能体：检查 definition.tools 白名单是否包含 "Task"
     */
    private boolean isTaskToolAllowed(AgentDefinition definition) {
        List<String> whitelist = definition.tools();
        return whitelist.isEmpty() || whitelist.contains("Task");
    }

    /**
     * 构建所有可用工具
     */
    private List<AbstractTool<?>> buildAllTools() {
        List<AbstractTool<?>> tools = new ArrayList<>();

        // 文件操作
        DiffGenerator dg = diffGeneratorProvider.getIfAvailable();
        tools.add(ReadTool.builder().build());
        tools.add(WriteTool.builder().diffGenerator(dg).build());
        tools.add(EditTool.builder().diffGenerator(dg).build());
        // 搜索
        tools.add(GlobTool.builder().build());
        tools.add(GrepTool.builder().build());
        if (configManager.getBochaApiKey() != null && !configManager.getBochaApiKey().isEmpty()) {
            tools.add(WebSearchTool.builder(new BochaSearchProvider(configManager.getBochaApiKey())).build());
        }
        // 网页
        tools.add(WebFetchTool.builder().build());
        // 命令执行
        tools.add(CommandTool.builder().processManager(processManager).build());
        tools.add(ProcessTool.builder().processManager(processManager).build());
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
        // 任务（TaskTool 按 agent 配置在 buildToolCallbacks 中单独创建）
        tools.add(TaskOutputTool.builder()
                .taskRepository(taskRepository).build());
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
     * 根据配置过滤工具列表。
     * MAIN 和 SUBAGENT 统一使用 definition.tools 白名单（MAIN 已合并 config.json）。
     */
    private List<ToolCallback> filterByConfig(List<ToolCallback> callbacks, AgentDefinition definition) {
        List<String> whitelist = definition.tools();
        if (!whitelist.isEmpty()) {
            Set<String> allowed = Set.copyOf(whitelist);
            return callbacks.stream()
                .filter(tc -> allowed.contains(tc.getToolDefinition().name()))
                .toList();
        }
        return callbacks;
    }

}
