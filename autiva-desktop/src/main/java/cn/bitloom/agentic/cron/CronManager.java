package cn.bitloom.agentic.cron;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.MemoryRecallAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.PermissionHook;
import cn.bitloom.agentic.hook.TodoReminderHook;
import cn.bitloom.agentic.hook.ToolCallBudgetHook;
import cn.bitloom.agentic.hook.ToolResultOffloadHook;
import cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.memory.FileSystemAgentMemoryStore;
import cn.bitloom.agentic.memory.MemoryConsolidator;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.StagedCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.command.ShellSession;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.store.Store;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class CronManager {

    /** pending_delivery 重试间隔（秒） */
    private static final int PENDING_RETRY_SECONDS = 30;

    private final TaskScheduler taskScheduler;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final List<ToolApprovalStrategy> approvalStrategies;
    private final ConfigManager configManager;
    private final CronTaskStore cronTaskStore;
    private final Map<String, CronTaskInfo> taskMap = new ConcurrentHashMap<>();

    public CronManager(@Qualifier("taskScheduler") TaskScheduler taskScheduler,
                       @Lazy FileSystemSessionManager fileSystemSessionManager,
                       @Lazy AgentDefinitionManager definitionManager,
                       @Lazy ModelFactory modelFactory,
                       @Lazy Toolkit toolkit,
                       List<ToolApprovalStrategy> approvalStrategies,
                       ConfigManager configManager,
                       @Lazy CronTaskStore cronTaskStore) {
        this.taskScheduler = taskScheduler;
        this.fileSystemSessionManager = fileSystemSessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.approvalStrategies = approvalStrategies;
        this.configManager = configManager;
        this.cronTaskStore = cronTaskStore;
        // pending_delivery 重试调度器：扫描待投递任务并重试
        taskScheduler.scheduleAtFixedRate(this::retryPendingDeliveries,
                Instant.now().plusSeconds(PENDING_RETRY_SECONDS), Duration.ofSeconds(PENDING_RETRY_SECONDS));
    }

    /**
     * 启动恢复：扫描所有 session 的持久化任务，重新注册调度。
     * <ul>
     *   <li>once 类型：触发时刻已过，直接丢弃（不补跑）</li>
     *   <li>interval 类型：从恢复时刻重新起算</li>
     *   <li>cron 类型：按表达式重新注册</li>
     *   <li>pendingDelivery=true：恢复后立即投递一次</li>
     * </ul>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restorePersistedTasks() {
        try {
            Map<String, List<CronTaskInfo>> all = cronTaskStore.loadAllSessions();
            int restored = 0;
            for (List<CronTaskInfo> tasks : all.values()) {
                for (CronTaskInfo task : tasks) {
                    if (!task.isDurable() || taskMap.containsKey(task.getName())) {
                        continue;
                    }
                    if ("once".equals(task.getType())) {
                        // 一次性任务的触发时刻在停机期间已过，不补跑
                        log.info("[CronManager] 恢复时丢弃过期 once 任务: name={}", task.getName());
                        continue;
                    }
                    try {
                        ScheduledFuture<?> future = scheduleTask(task.getName(), task.getType(),
                                task.getIntervalSeconds(), null, task.getCronExpression());
                        task.setScheduledFuture(future);
                        taskMap.put(task.getName(), task);
                        restored++;
                        if (task.isPendingDelivery()) {
                            log.info("[CronManager] 恢复待投递任务: name={}", task.getName());
                        }
                    } catch (Exception e) {
                        log.warn("[CronManager] 恢复任务失败: name={}, error={}", task.getName(), e.getMessage());
                    }
                }
            }
            // 持久化一次，清掉丢弃的 once 任务
            all.keySet().forEach(this::persistSessionTasks);
            if (restored > 0) {
                log.info("[CronManager] 启动恢复完成: 恢复 {} 个定时任务", restored);
            }
        } catch (Exception e) {
            log.error("[CronManager] 启动恢复失败", e);
        }
    }

    /**
     * 持久化指定 session 的全部任务到 {sessionDir}/cron-tasks.json。
     */
    private void persistSessionTasks(String sessionId) {
        List<CronTaskInfo> tasks = taskMap.values().stream()
                .filter(t -> sessionId.equals(t.getSessionId()) && t.isDurable())
                .toList();
        cronTaskStore.save(sessionId, tasks);
    }

    public void createTask(String name, String type, Integer intervalSeconds,
                           Integer delaySeconds, String cronExpression, String message,
                           String sessionId, String agentId) {
        log.info("[CronManager] 创建定时任务: name={}, type={}", name, type);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        if (taskMap.containsKey(name)) {
            throw new IllegalArgumentException("任务名称已存在: " + name);
        }

        if (StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("任务类型不能为空");
        }

        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ScheduledFuture<?> scheduledFuture = scheduleTask(name, type, intervalSeconds, delaySeconds, cronExpression);

        CronTaskInfo taskInfo = new CronTaskInfo();
        taskInfo.setSessionId(sessionId);
        taskInfo.setAgentId(agentId);
        taskInfo.setName(name);
        taskInfo.setType(type);
        taskInfo.setIntervalSeconds(intervalSeconds);
        taskInfo.setDelaySeconds(delaySeconds);
        taskInfo.setCronExpression(cronExpression);
        taskInfo.setMessage(message);
        taskInfo.setScheduledFuture(scheduledFuture);
        taskInfo.setCreateTime(Instant.now());

        taskMap.put(name, taskInfo);
        persistSessionTasks(sessionId);
    }

    public Map<String, CronTaskInfo> getAllTasks(String sessionId) {
        if (sessionId == null) {
            return new ConcurrentHashMap<>(taskMap);
        }

        Map<String, CronTaskInfo> result = new ConcurrentHashMap<>();
        taskMap.forEach((name, taskInfo) -> {
            if (sessionId.equals(taskInfo.getSessionId())) {
                result.put(name, taskInfo);
            }
        });
        return result;
    }

    public CronTaskInfo getTask(String sessionId, String name) {
        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo != null && sessionId.equals(taskInfo.getSessionId())) {
            return taskInfo;
        }
        return null;
    }

    public void deleteTask(String sessionId, String name) {
        log.info("[CronManager] 删除定时任务: name={}, sessionId={}", name, sessionId);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo == null) {
            throw new IllegalArgumentException("未找到任务: " + name);
        }

        if (!sessionId.equals(taskInfo.getSessionId())) {
            throw new IllegalArgumentException("无权操作该任务: " + name);
        }

        taskInfo.getScheduledFuture().cancel(false);
        taskMap.remove(name);
        persistSessionTasks(sessionId);
    }

    public boolean taskExists(String name) {
        return taskMap.containsKey(name);
    }

    public void triggerTask(String sessionId, String name) {
        log.info("[CronManager] 手动触发定时任务: name={}, sessionId={}", name, sessionId);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("任务名称不能为空");
        }

        CronTaskInfo taskInfo = taskMap.get(name);
        if (taskInfo == null) {
            throw new IllegalArgumentException("未找到任务: " + name);
        }

        if (!sessionId.equals(taskInfo.getSessionId())) {
            throw new IllegalArgumentException("无权操作该任务: " + name);
        }

        triggerTaskInternal(taskInfo);
    }

    private ScheduledFuture<?> scheduleTask(String name, String type, Integer intervalSeconds,
                                            Integer delaySeconds, String cronExpression) {
        return switch (type.toLowerCase()) {
            case "once" -> {
                if (delaySeconds == null || delaySeconds <= 0) {
                    throw new IllegalArgumentException("一次性任务必须指定有效的延迟秒数");
                }
                Instant startTime = Instant.now().plusSeconds(delaySeconds);
                yield taskScheduler.schedule(() -> executeTask(name), startTime);
            }
            case "interval" -> {
                if (intervalSeconds == null || intervalSeconds <= 0) {
                    throw new IllegalArgumentException("周期性任务必须指定有效的间隔秒数");
                }
                Instant startTime = delaySeconds != null && delaySeconds > 0
                        ? Instant.now().plusSeconds(delaySeconds)
                        : Instant.now();
                yield taskScheduler.scheduleAtFixedRate(() -> executeTask(name), startTime,
                        Duration.ofSeconds(intervalSeconds));
            }
            case "cron" -> {
                if (StringUtils.isBlank(cronExpression)) {
                    throw new IllegalArgumentException("Cron任务必须指定cron表达式");
                }
                yield taskScheduler.schedule(() -> executeTask(name), new CronTrigger(cronExpression));
            }
            default -> throw new IllegalArgumentException("不支持的任务类型: " + type);
        };
    }

    private void executeTask(String name) {
        log.info("[CronManager] 定时任务触发: name={}", name);
        try {
            CronTaskInfo taskInfo = taskMap.get(name);
            if (taskInfo != null) {
                boolean delivered = triggerTaskInternal(taskInfo);

                if ("once".equals(taskInfo.getType()) && delivered) {
                    taskMap.remove(name);
                    persistSessionTasks(taskInfo.getSessionId());
                    log.info("[CronManager] 一次性任务已完成并移除: name={}", name);
                }
            }
        } catch (Exception e) {
            log.error("[CronManager] 任务执行失败: name={}", name, e);
        }
    }

    /**
     * pending_delivery 重试：扫描待投递任务，锁空闲时补投。
     */
    private void retryPendingDeliveries() {
        for (CronTaskInfo taskInfo : taskMap.values()) {
            if (!taskInfo.isPendingDelivery()) {
                continue;
            }
            try {
                boolean delivered = triggerTaskInternal(taskInfo);
                if (delivered) {
                    log.info("[CronManager] 待投递任务补投成功: name={}", taskInfo.getName());
                    if ("once".equals(taskInfo.getType())) {
                        taskMap.remove(taskInfo.getName());
                        persistSessionTasks(taskInfo.getSessionId());
                    }
                }
            } catch (Exception e) {
                log.warn("[CronManager] 待投递任务补投失败: name={}, error={}", taskInfo.getName(), e.getMessage());
            }
        }
    }

    /**
     * 直接触发 Agent.runStream（pending_delivery 语义）。
     * <p>
     * 使用 tryWithLock 非阻塞获取 per-session 锁：锁被用户回合占用时不等待，
     * 标记 pendingDelivery=true 落盘，由重试调度器稍后补投。
     *
     * @return 是否成功投递（false 表示 pending，等待重试）
     */
    private boolean triggerTaskInternal(CronTaskInfo taskInfo) {
        Session session = fileSystemSessionManager.getById(taskInfo.getSessionId());
        if (session == null) {
            log.warn("[CronManager] session 不存在，放弃投递: sessionId={}", taskInfo.getSessionId());
            return true; // session 已删除，无需重试
        }
        MessageEvent inputEvent = MessageEvent.userMessage(session.id(), taskInfo.getMessage());
        String agentId = taskInfo.getAgentId();

        Boolean executed = fileSystemSessionManager.tryWithLock(session.id(), () -> {
            try {
                Agent agent = buildAgent(session, agentId);
                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(session.id())
                        .userId(session.userId())
                        .build();
                agent.runStream(inputEvent, ctx)
                        .doOnError(e -> log.error("[CronManager] agent run error: name={}", taskInfo.getName(), e))
                        .blockLast();
            } catch (Exception e) {
                log.error("[CronManager] triggerTaskInternal error: name={}", taskInfo.getName(), e);
            }
            return Boolean.TRUE;
        });

        if (executed == null) {
            // 锁被占用 → 标记 pending，稍后重试
            taskInfo.setPendingDelivery(true);
            persistSessionTasks(taskInfo.getSessionId());
            log.info("[CronManager] session 忙，任务标记待投递: name={}", taskInfo.getName());
            return false;
        }

        taskInfo.setPendingDelivery(false);
        taskInfo.setLastFiredAt(Instant.now());
        persistSessionTasks(taskInfo.getSessionId());
        return true;
    }

    /**
     * 构建 Agent（各调用方各自实现，不新建 AgentFactory）。
     */
    private Agent buildAgent(Session session, String agentId) {
        AgentDefinition definition = definitionManager.getOrLoadMainDefinition(agentId);
        ModelTypeEnum modelType = Store.selectedModel.get() != null ? Store.selectedModel.get() : ModelTypeEnum.DEEPSEEK;
        ChatModel chatModel = modelFactory.model(modelType);
        String uid = session.userId() != null ? session.userId() : "default-user";

        List<Advisor> advisors = new ArrayList<>();

        // 四步压缩管线（低成本优先：滑动窗口裁剪 → 旧工具结果占位符化 → 水位检查 → LLM 摘要）
        StagedCompactionStrategy stagedStrategy = StagedCompactionStrategy.builder(
                        RecursiveSummarizationCompactionStrategy.builder(
                                ChatClient.builder(chatModel).build())
                        .build())
                .tokenThreshold(100000)
                .build();

        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(fileSystemSessionManager)
                .defaultUserId(uid)
                .messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL)
                        .and(MessageFilter.skipEmptyMessages()))
                .compactionTrigger(TokenCountTrigger.builder()
                        .threshold(100000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build())
                .compactionStrategy(stagedStrategy)
                .build();
        advisors.add(sessionMemoryAdvisor);

        Path memoriesDir = AppConstants.Memory.workMemoryDir();
        // 记忆自动化（cron 智能体）：选择式召回 + 整理触发器（提取 Hook 仅主智能体）
        FileSystemAgentMemoryStore memoryStore = new FileSystemAgentMemoryStore(memoriesDir);
        AutoMemoryToolsAdvisor autoMemoryToolsAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoryStore(memoryStore)
                .memoriesRootDirectory(memoriesDir.toString())
                .memoryConsolidationTrigger(
                        MemoryConsolidator.triggerWhen(memoryStore, MemoryConsolidator.DEFAULT_THRESHOLD))
                .build();
        advisors.add(autoMemoryToolsAdvisor);

        advisors.add(MemoryRecallAdvisor.builder()
                .sessionManager(fileSystemSessionManager)
                .memoryStore(memoryStore)
                .chatClient(ChatClient.builder(chatModel).build())
                .build());

        List<ToolCallback> allTools = new ArrayList<>(toolkit.buildToolCallbacks(definition));
        allTools.addAll(Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(ConversationSearchTool.builder(fileSystemSessionManager).build())
                .build()
                .getToolCallbacks()));
        allTools.addAll(Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(CrossSessionSearchTool.builder(fileSystemSessionManager, uid).build())
                .build()
                .getToolCallbacks()));

        Agent agent = Agent.builder()
                .name(agentId)
                .definition(definition)
                .model(chatModel)
                .systemPrompt(definition.content() + ShellSession.envBlock())
                .tools(allTools)
                .hooks(buildBaseHooks())
                .advisors(advisors)
                // reactive_compact：上下文超长被 API 拒绝时强制压缩（绕过触发器）后重试一次
                .reactiveCompactor(sid -> fileSystemSessionManager.compact(sid, req -> true, stagedStrategy))
                .build();
        log.info("构建定时任务智能体: agentId={}", agentId);
        return agent;
    }

    /**
     * 基础 Hook 集：预算保护 / 权限审批 / Todo 提醒 / 工具结果落盘。
     * 每次构建 Agent 都 new 新实例（内部持有 per-session 可变状态，避免多智能体共享串扰）。
     */
    private List<IAgentHook> buildBaseHooks() {
        List<IAgentHook> hooks = new ArrayList<>();
        hooks.add(new ToolCallBudgetHook(configManager.getMaxToolCalls()));
        hooks.add(new PermissionHook(approvalStrategies));
        hooks.add(new TodoReminderHook());
        hooks.add(new ToolResultOffloadHook());
        return hooks;
    }

    @Setter
    @Getter
    public static class CronTaskInfo {
        private String sessionId;
        private String agentId;
        private String name;
        private String type;
        private Integer intervalSeconds;
        private Integer delaySeconds;
        private String cronExpression;
        private String message;
        @JsonIgnore
        private ScheduledFuture<?> scheduledFuture;
        private Instant createTime;
        /** 是否持久化（重启恢复），默认 true */
        private boolean durable = true;
        /** 已到期但尚未成功投递（session 锁被占用时标记，稍后重试） */
        private boolean pendingDelivery = false;
        /** 最近一次成功触发时间 */
        private Instant lastFiredAt;
    }
}
