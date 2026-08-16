package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.cron.CronManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.taskboard.TaskBoardRepository;
import cn.bitloom.agentic.tool.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.askuser.AskUserQuestionTool;
import cn.bitloom.agentic.tool.command.*;
import cn.bitloom.agentic.tool.cron.CronCreateTool;
import cn.bitloom.agentic.tool.cron.CronDeleteTool;
import cn.bitloom.agentic.tool.cron.CronListTool;
import cn.bitloom.agentic.tool.cron.CronTriggerTool;
import cn.bitloom.agentic.tool.file.*;
import cn.bitloom.agentic.tool.mcp.McpConnectTool;
import cn.bitloom.agentic.tool.mcp.McpConnectionManager;
import cn.bitloom.agentic.tool.mcp.McpListTool;
import cn.bitloom.agentic.tool.skill.SkillTool;
import cn.bitloom.agentic.tool.taskboard.TaskClaimTool;
import cn.bitloom.agentic.tool.taskboard.TaskCompleteTool;
import cn.bitloom.agentic.tool.taskboard.TaskCreateTool;
import cn.bitloom.agentic.tool.taskboard.TaskListTool;
import cn.bitloom.agentic.team.TeammateRuntime;
import cn.bitloom.agentic.tool.team.ListTeammatesTool;
import cn.bitloom.agentic.tool.team.SendMessageTool;
import cn.bitloom.agentic.tool.team.SpawnTeammateTool;
import cn.bitloom.agentic.tool.team.TeammateShutdownTool;
import cn.bitloom.agentic.tool.task.TaskOutputTool;
import cn.bitloom.agentic.tool.task.TaskTool;
import cn.bitloom.agentic.tool.todo.TodoWriteTool;
import cn.bitloom.agentic.tool.web.BochaSearchProvider;
import cn.bitloom.agentic.tool.web.WebFetchTool;
import cn.bitloom.agentic.tool.web.WebSearchTool;
import cn.bitloom.agentic.util.GuiQuestionHandler;
import cn.bitloom.agentic.util.GuiTodoEventHandler;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.config.ConfigManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
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
    private final McpConnectionManager mcpConnectionManager;
    private final TaskRepository taskRepository;
    private final ProcessManager processManager;
    private final PersistentShellRegistry persistentShellRegistry;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final AgentDefinitionManager definitionManager;
    private final TaskBoardRepository taskBoardRepository;
    private final TeammateRuntime teammateRuntime;
    private final cn.bitloom.agentic.team.TeammateRegistry teammateRegistry;
    private final cn.bitloom.agentic.team.MailboxService mailboxService;
    private final cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory;
    private final cn.bitloom.agentic.workflow.WorkflowRegistry workflowRegistry;
    private final cn.bitloom.agentic.goal.GoalManager goalManager;
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

        // 2. 追加 MCP 工具：启动时全量注入（spring.ai.mcp.client 配置）+ 运行时经 McpConnect 连接的
        callbacks.addAll(List.of(mcpToolCallbackProvider.getToolCallbacks()));
        callbacks.addAll(mcpConnectionManager.getRuntimeToolCallbacks());

        // 3. 根据配置过滤
        List<ToolCallback> filtered = filterByConfig(callbacks, definition);

        // 4. 按 agent 配置创建 TaskTool（如果该 agent 允许使用 Task 工具）
        if (isTaskToolAllowed(definition)) {
            TaskTool taskTool = TaskTool.builder()
                    .taskRepository(taskRepository)
                    .toolUIBridge(toolUIBridge)
                    .sessionManager(fileSystemSessionManager)
                    .subAgentFactory(subAgentFactory)
                    .goalManager(goalManager)
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

        // 文件操作（权限审批由 PermissionHook 统一处理）
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
        // 命令执行（持久化 Shell 会话 + 后台进程管理；权限审批由 PermissionHook 统一处理）
        CommandExecutor backgroundExecutor = new CommandExecutor(persistentShellRegistry.getSharedShellSession());
        tools.add(CommandTool.builder()
                .shellRegistry(persistentShellRegistry)
                .backgroundExecutor(backgroundExecutor)
                .processManager(processManager)
                .build());
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
        // MCP 运行时管理（连接/清单；调用授权由 McpHostPolicy + PermissionHook 统一处理）
        tools.add(McpConnectTool.builder().connectionManager(mcpConnectionManager).build());
        tools.add(McpListTool.builder().connectionManager(mcpConnectionManager).build());
        // 持久化任务依赖图（跨会话任务 + 原子认领；仅 code 模式 projectPath 非空时实际可用）
        tools.add(TaskCreateTool.builder().repository(taskBoardRepository).build());
        tools.add(TaskListTool.builder().repository(taskBoardRepository).build());
        tools.add(TaskClaimTool.builder().repository(taskBoardRepository).build());
        tools.add(TaskCompleteTool.builder().repository(taskBoardRepository).build());
        // Agent Teams（持久队友 + MessageBus 邮箱 + 共享任务板；Lead 与队友通用 SendMessage）
        tools.add(SpawnTeammateTool.builder()
                .registry(teammateRegistry)
                .mailbox(mailboxService)
                .waker(teammateRuntime::wake)
                .questionHandler(new GuiQuestionHandler(toolUIBridge))
                .build());
        tools.add(ListTeammatesTool.builder().registry(teammateRegistry).build());
        tools.add(SendMessageTool.builder()
                .mailbox(mailboxService)
                .registry(teammateRegistry)
                .waker(teammateRuntime::wake)
                .build());
        tools.add(TeammateShutdownTool.builder()
                .registry(teammateRegistry)
                .mailbox(mailboxService)
                .build());
        // Workflow Runtime（编排形状固定 + journal 续跑；内置 code-review）
        tools.add(cn.bitloom.agentic.workflow.WorkflowTool.builder()
                .registry(workflowRegistry)
                .sessionManager(fileSystemSessionManager)
                .subAgentFactory(subAgentFactory)
                .toolUIBridge(toolUIBridge)
                .build());
        // Goal Loop（目标闭环：GoalSet 激活 + 独立判断器复核 + 自动续轮）
        tools.add(cn.bitloom.agentic.tool.goal.GoalSetTool.builder()
                .goalManager(goalManager)
                .build());
        return tools;
    }

    /**
     * 根据配置过滤工具列表。
     * MAIN 和 SUBAGENT 统一使用 definition.tools 白名单（MAIN 已合并 config.json）。
     * MCP 工具（mcp__ 前缀 + 运行时注册表命中）与 McpConnect/McpList 豁免白名单——
     * MCP 工具集由 mcp.json 与 McpHostPolicy 宿主策略控制，不经内置工具白名单。
     */
    private List<ToolCallback> filterByConfig(List<ToolCallback> callbacks, AgentDefinition definition) {
        List<String> whitelist = definition.tools();
        if (!whitelist.isEmpty()) {
            Set<String> allowed = Set.copyOf(whitelist);
            return callbacks.stream()
                .filter(tc -> isMcpExempt(tc.getToolDefinition().name()) || allowed.contains(tc.getToolDefinition().name()))
                .toList();
        }
        return callbacks;
    }

    /**
     * 是否豁免白名单：MCP 工具与 MCP 管理工具。
     */
    private boolean isMcpExempt(String toolName) {
        return "McpConnect".equals(toolName) || "McpList".equals(toolName)
                || toolName.startsWith("mcp__") || mcpConnectionManager.isMcpTool(toolName);
    }

}
