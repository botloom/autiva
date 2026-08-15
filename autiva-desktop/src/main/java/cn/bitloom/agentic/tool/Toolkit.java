package cn.bitloom.agentic.tool;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.cron.CronManager;
import cn.bitloom.agentic.evolve.EvolveAgentEnricher;
import cn.bitloom.agentic.evolve.EvolverAgent;
import cn.bitloom.agentic.evolve.experience.ExperienceEngine;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRepository;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.task.repository.TaskRepository;
import cn.bitloom.agentic.tool.askuser.AskUserQuestionTool;
import cn.bitloom.agentic.tool.command.*;
import cn.bitloom.agentic.tool.cron.CronCreateTool;
import cn.bitloom.agentic.tool.cron.CronDeleteTool;
import cn.bitloom.agentic.tool.cron.CronListTool;
import cn.bitloom.agentic.tool.cron.CronTriggerTool;
import cn.bitloom.agentic.tool.evolve.*;
import cn.bitloom.agentic.tool.file.*;
import cn.bitloom.agentic.tool.skill.SkillTool;
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
    private final TaskRepository taskRepository;
    private final ProcessManager processManager;
    private final PersistentShellRegistry persistentShellRegistry;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final ObjectProvider<DiffGenerator> diffGeneratorProvider;
    private final ObjectProvider<Toolkit> selfProvider;
    private final EvolveAgentEnricher evolveEnricher;
    private final GeneRepository geneRepository;
    private final ObjectProvider<TrajectoryRepository> trajectoryRepositoryProvider;
    private final ObjectProvider<ExperienceEngine> experienceEngineProvider;
    private final ObjectProvider<EvolverAgent> evolverAgentProvider;

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
                    .evolveEnricher(evolveEnricher)
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
        // 进化系统工具（仅当 app.evolve.enabled=true 时注册）
        if (configManager.isEvolveEnabled()) {
            addEvolveTools(tools);
        }
        return tools;
    }

    /**
     * 注册 8 个 Evolve 管理工具。
     * <p>
     * GeneList/Get/Create/Update/Delete/Activate 只依赖 GeneRepository；
     * EvolveStatus 依赖 TrajectoryRepository + ExperienceEngine + GeneRepository；
     * EvolveTrigger 依赖 EvolverAgent。后两者通过 ObjectProvider 获取。
     */
    private void addEvolveTools(List<AbstractTool<?>> tools) {
        tools.add(GeneListTool.builder().geneRepository(geneRepository).build());
        tools.add(GeneGetTool.builder().geneRepository(geneRepository).build());
        tools.add(GeneCreateTool.builder().geneRepository(geneRepository).build());
        tools.add(GeneUpdateTool.builder().geneRepository(geneRepository).build());
        tools.add(GeneDeleteTool.builder().geneRepository(geneRepository).build());
        tools.add(GeneActivateTool.builder().geneRepository(geneRepository).build());

        TrajectoryRepository trajectoryRepo = trajectoryRepositoryProvider.getIfAvailable();
        ExperienceEngine experienceEngine = experienceEngineProvider.getIfAvailable();
        if (trajectoryRepo != null && experienceEngine != null) {
            tools.add(EvolveStatusTool.builder()
                    .trajectoryRepository(trajectoryRepo)
                    .experienceEngine(experienceEngine)
                    .geneRepository(geneRepository)
                    .build());
        }

        EvolverAgent evolverAgent = evolverAgentProvider.getIfAvailable();
        if (evolverAgent != null) {
            tools.add(EvolveTriggerTool.builder().evolverAgent(evolverAgent).build());
        }
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
