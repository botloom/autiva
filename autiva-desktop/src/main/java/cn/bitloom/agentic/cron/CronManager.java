package cn.bitloom.agentic.cron;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.advisor.AutoMemoryToolsAdvisor;
import cn.bitloom.agentic.agent.advisor.SessionMemoryAdvisor;
import cn.bitloom.agentic.evolve.EvolveAgentEnricher;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.MessageFilter;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.compaction.RecursiveSummarizationCompactionStrategy;
import cn.bitloom.agentic.session.compaction.TokenCountTrigger;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.command.ShellSession;
import cn.bitloom.agentic.tool.session.ConversationSearchTool;
import cn.bitloom.agentic.tool.session.CrossSessionSearchTool;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.store.Store;
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
import org.springframework.context.annotation.Lazy;
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

    private final TaskScheduler taskScheduler;
    private final FileSystemSessionManager fileSystemSessionManager;
    private final AgentDefinitionManager definitionManager;
    private final ModelFactory modelFactory;
    private final Toolkit toolkit;
    private final EvolveAgentEnricher evolveEnricher;
    private final Map<String, CronTaskInfo> taskMap = new ConcurrentHashMap<>();

    public CronManager(@Qualifier("taskScheduler") TaskScheduler taskScheduler,
                       @Lazy FileSystemSessionManager fileSystemSessionManager,
                       @Lazy AgentDefinitionManager definitionManager,
                       @Lazy ModelFactory modelFactory,
                       @Lazy Toolkit toolkit,
                       @Lazy EvolveAgentEnricher evolveEnricher) {
        this.taskScheduler = taskScheduler;
        this.fileSystemSessionManager = fileSystemSessionManager;
        this.definitionManager = definitionManager;
        this.modelFactory = modelFactory;
        this.toolkit = toolkit;
        this.evolveEnricher = evolveEnricher;
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

        log.info("[CronManager] 创建成功: name={}", name);
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

        log.info("[CronManager] 删除成功: name={}", name);
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
        log.info("[CronManager] 触发成功: name={}", name);
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
                triggerTaskInternal(taskInfo);

                if ("once".equals(taskInfo.getType())) {
                    taskMap.remove(name);
                    log.info("[CronManager] 一次性任务已完成并移除: name={}", name);
                }
            }
        } catch (Exception e) {
            log.error("[CronManager] 任务执行失败: name={}", name, e);
        }
    }

    /**
     * 直接触发 Agent.runStream。
     * per-session 锁保证串行，事件通过 SessionMemoryAdvisor 自动持久化。
     */
    private void triggerTaskInternal(CronTaskInfo taskInfo) {
        Session session = fileSystemSessionManager.getById(taskInfo.getSessionId());
        if (session == null) {
            log.warn("[CronManager] session 不存在: sessionId={}", taskInfo.getSessionId());
            return;
        }
        MessageEvent inputEvent = MessageEvent.userMessage(session.id(), taskInfo.getMessage());
        String agentId = taskInfo.getAgentId();

        fileSystemSessionManager.withLock(session.id(), () -> {
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
            return null;
        });
        log.info("[CronManager] 定时任务执行完成: name={}, sessionId={}", taskInfo.getName(), taskInfo.getSessionId());
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

        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(fileSystemSessionManager)
                .defaultUserId(uid)
                .messageFilter(MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL)
                        .and(MessageFilter.skipEmptyMessages()))
                .compactionTrigger(TokenCountTrigger.builder()
                        .threshold(100000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build())
                .compactionStrategy(RecursiveSummarizationCompactionStrategy.builder(
                                ChatClient.builder(chatModel).build())
                        .build())
                .build();
        advisors.add(sessionMemoryAdvisor);

        Path memoriesDir = AppConstants.Memory.workMemoryDir();
        AutoMemoryToolsAdvisor autoMemoryToolsAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoriesRootDirectory(memoriesDir.toString())
                .build();
        advisors.add(autoMemoryToolsAdvisor);

        // 进化系统：条件注入 GeneInjector Advisor
        evolveEnricher.enrichAdvisors(advisors);

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
                .hooks(evolveEnricher.buildHooks())
                .advisors(advisors)
                .build();
        log.info("构建定时任务智能体: agentId={}", agentId);
        return agent;
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
        private ScheduledFuture<?> scheduledFuture;
        private Instant createTime;

    }
}
