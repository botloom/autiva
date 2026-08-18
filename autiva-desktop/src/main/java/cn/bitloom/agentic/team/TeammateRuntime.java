package cn.bitloom.agentic.team;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.taskboard.TaskBoardRepository;
import cn.bitloom.agentic.taskboard.TaskRecord;
import cn.bitloom.agentic.tool.command.ShellSession;
import cn.bitloom.agentic.tool.team.SendMessageTool;
import cn.bitloom.agentic.tool.taskboard.TaskClaimTool;
import cn.bitloom.agentic.tool.taskboard.TaskCompleteTool;
import cn.bitloom.agentic.tool.taskboard.TaskListTool;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 队友唤醒运行时（对标 learn-claude-code s13）。
 *
 * <p>唤醒链路：检测 branch {@code teammate.{name}} 内未消费的 mailbox 事件 / 共享任务板
 * 可认领任务 → 原子抢占状态（idle→work，防重入）→ 构建 Agent（EventFilter.forBranch，
 * 跨任务保留 branch 上下文）→ runStream → 双事件写回（result + idle_notification，root
 * + notification 自动注入 Lead 上下文）。
 *
 * <p>并发模型：root 主循环维持现有 per-session 串行锁；队友各自 branch 的 runStream
 * 并行执行，事件写入靠 JSONL 追加的原子性。唤醒本身不阻塞调用方（工具线程/轮询线程）。
 */
@Slf4j
@Component
public class TeammateRuntime {

    /** 唤醒触发器（解耦 SendMessageTool ↔ TeammateRuntime 的构造循环） */
    @FunctionalInterface
    public interface Waker {
        void wake(String sessionId, String teammateName);
    }

    private final TeammateRegistry registry;
    private final MailboxService mailbox;
    private final FileSystemSessionManager sessionManager;
    private final AgentDefinitionManager definitionManager;
    private final cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory;
    private final ToolUIBridge toolUIBridge;
    private final TaskBoardRepository taskBoard;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;

    /** 唤醒去重：正在执行 wake 检查的队友（极短临界区，仅防轮询与消息触发并发竞争） */
    private final ConcurrentHashMap<String, Boolean> waking = new ConcurrentHashMap<>();

    public TeammateRuntime(TeammateRegistry registry,
            MailboxService mailbox,
            FileSystemSessionManager sessionManager,
            AgentDefinitionManager definitionManager,
            cn.bitloom.agentic.agent.SubAgentFactory subAgentFactory,
            ToolUIBridge toolUIBridge,
            TaskBoardRepository taskBoard,
            @Qualifier("taskScheduler") org.springframework.scheduling.TaskScheduler taskScheduler) {
        this.registry = registry;
        this.mailbox = mailbox;
        this.sessionManager = sessionManager;
        this.definitionManager = definitionManager;
        this.subAgentFactory = subAgentFactory;
        this.toolUIBridge = toolUIBridge;
        this.taskBoard = taskBoard;
        this.taskScheduler = taskScheduler;
        // 手动注册轮询（项目未启用 @EnableScheduling，与 CronManager 同风格）
        taskScheduler.scheduleWithFixedDelay(this::pollIdleTeammates,
                java.time.Instant.now().plusSeconds(20), java.time.Duration.ofSeconds(15));
    }

    /**
     * 唤醒队友（异步，不阻塞调用方 — 工具线程/轮询线程立即返回）。
     * 防重入：仅 idle/spawned 状态可进入 work；work 中收到的新消息留在邮箱，
     * 由本轮结束后的轮询再次唤醒。
     */
    public void wake(String sessionId, String teammateName) {
        Optional<TeammateRecord> found = registry.get(sessionId, teammateName);
        if (found.isEmpty() || found.get().isShutdown()) {
            return;
        }
        String dedupKey = sessionId + "/" + teammateName;
        if (waking.putIfAbsent(dedupKey, Boolean.TRUE) != null) {
            return;
        }
        try {
            TeammateRecord teammate = found.get();
            // 原子抢占：idle→work 或 spawned→work；work 中直接返回（邮箱消息由下轮轮询处理）
            boolean acquired = registry.transition(sessionId, teammateName, teammate.getStatus(),
                    TeammateRecord.STATUS_WORK);
            if (!acquired) {
                return;
            }
            // 回合在 boundedElastic 上异步执行，本方法立即返回
            Mono.fromRunnable(() -> runTeammateRound(sessionId, teammate))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
        finally {
            waking.remove(dedupKey);
        }
    }

    /**
     * 执行一个队友回合：邮箱消费 → 任务板认领 → runStream → 双事件写回。
     * 无邮件且无可认领任务时直接转 idle（空转保护）。
     */
    private void runTeammateRound(String sessionId, TeammateRecord teammate) {
        try {
            // 1. 邮箱：取出未消费消息并标记（消息已在 branch 历史中，队友上下文自然可见）
            List<MessageEvent> messages = mailbox.unconsumed(sessionId, teammate.getName());
            mailbox.markConsumed(sessionId, messages);

            // 2. 无邮件时尝试任务板认领（work 模式无任务板，跳过）
            TaskRecord claimed = null;
            if (messages.isEmpty()) {
                claimed = tryClaimTask(sessionId, teammate);
                if (claimed == null) {
                    registry.transition(sessionId, teammate.getName(), TeammateRecord.STATUS_WORK,
                            TeammateRecord.STATUS_IDLE);
                    return;
                }
            }

            // 3. UI：队友工作卡片
            String taskId = teammate.branch();
            if (toolUIBridge != null) {
                ObjectNode card = JsonUtils.createObject();
                card.put("subagentName", "teammate:" + teammate.getName());
                card.put("description", claimed != null ? claimed.getSubject()
                        : "处理 " + messages.size() + " 条新消息");
                card.put("taskId", taskId);
                javafx.application.Platform.runLater(() -> toolUIBridge.createTaskCard(sessionId, taskId,
                        JsonUtils.toJson(card)));
            }

            // 4. runStream（跨任务保留 branch 上下文）
            String trigger = buildTrigger(teammate, messages, claimed);
            Session session = sessionManager.getById(sessionId);
            String projectPath = teammate.effectiveProjectPath();
            Agent agent = buildAgent(session, teammate, projectPath);
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId(session.userId())
                    .branch(teammate.branch())
                    .projectPath(projectPath)
                    .build();
            StringBuilder output = new StringBuilder();
            MessageEvent inputEvent = MessageEvent.userMessage(sessionId, trigger);
            agent.runStream(inputEvent, ctx)
                    .doOnNext(event -> {
                        if (toolUIBridge != null) {
                            javafx.application.Platform.runLater(() -> toolUIBridge.processEvent(taskId, event));
                        }
                        if (event instanceof MessageEvent me && me.isAssistantMessage() && me.getText() != null) {
                            output.append(me.getText());
                        }
                    })
                    .blockLast();

            // 5. 双事件纪律：result 与 idle_notification 两条独立通知（均 push 注入 Lead）
            String result = output.toString().isBlank() ? "（无输出）" : output.toString();
            mailbox.deliverToLead(sessionId, teammate.getName(),
                    "任务结果：" + truncate(result, 500));
            mailbox.deliverToLead(sessionId, teammate.getName(), "Waiting for more work");
            if (toolUIBridge != null) {
                String summary = truncate(result, 200);
                javafx.application.Platform.runLater(() -> {
                    toolUIBridge.completeTaskCard(taskId, null);
                    toolUIBridge.showNotification("队友 " + teammate.getName() + " 已完成工作，等待新任务",
                            sessionId);
                });
            }
        }
        catch (Exception e) {
            log.error("[Teams] 队友回合执行失败: session={}, teammate={}", sessionId, teammate.getName(), e);
            mailbox.deliverToLead(sessionId, teammate.getName(),
                    "执行失败：" + truncate(e.getMessage() != null ? e.getMessage() : e.toString(), 500));
        }
        finally {
            // work→idle（workVersion+1，使旧的类型化请求失效）；已 shutdown 则保持
            registry.transition(sessionId, teammate.getName(), TeammateRecord.STATUS_WORK,
                    TeammateRecord.STATUS_IDLE);
        }
    }

    /** 空闲队友轮询：邮箱消息优先，其次共享任务板原子认领（每 15s，手动调度与 CronManager 同风格） */
    public void pollIdleTeammates() {
        for (String sessionId : registry.activeSessionIds()) {
            try {
                for (TeammateRecord teammate : registry.listActive(sessionId)) {
                    if (!teammate.isIdle()) {
                        continue;
                    }
                    boolean hasMail;
                    try {
                        hasMail = !mailbox.unconsumed(sessionId, teammate.getName()).isEmpty();
                    }
                    catch (Exception e) {
                        continue;
                    }
                    if (hasMail || findClaimableTask(teammate) != null) {
                        wake(sessionId, teammate.getName());
                    }
                }
            }
            catch (Exception e) {
                log.debug("[Teams] 轮询 session 失败: {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /** 任务板原子认领（owner=队友名）；认领带 worktree 的任务时绑定 worktree 到队友 */
    private TaskRecord tryClaimTask(String sessionId, TeammateRecord teammate) {
        TaskRecord candidate = findClaimableTask(teammate);
        if (candidate == null) {
            return null;
        }
        String projectDir = teammate.getProjectPath();
        TaskBoardRepository.ClaimResult result = taskBoard.claim(projectDir, candidate.getId(), teammate.getName());
        if (!result.success()) {
            log.debug("[Teams] 认领失败: teammate={}, task={}, reason={}", teammate.getName(), candidate.getId(),
                    result.message());
            return null;
        }
        TaskRecord claimed = result.task();
        if (claimed.getWorktree() != null && !claimed.getWorktree().isBlank()) {
            registry.bindWorktree(sessionId, teammate.getName(), claimed.getWorktree());
        }
        return claimed;
    }

    /** 查找第一个可认领任务：pending、无 owner、依赖全部完成 */
    private TaskRecord findClaimableTask(TeammateRecord teammate) {
        String projectDir = teammate.getProjectPath();
        if (projectDir == null || projectDir.isBlank()) {
            return null;
        }
        try {
            for (TaskRecord task : taskBoard.list(projectDir, TaskRecord.STATUS_PENDING)) {
                if (task.getOwner() == null && taskBoard.incompleteDependencies(projectDir, task).isEmpty()) {
                    return task;
                }
            }
        }
        catch (Exception e) {
            log.debug("[Teams] 任务板查询失败: {}", e.getMessage());
        }
        return null;
    }

    /** 组装唤醒触发消息（邮箱正文与任务描述已在 branch 历史/系统提示中，这里只给驱动指令） */
    private String buildTrigger(TeammateRecord teammate, List<MessageEvent> messages, TaskRecord claimed) {
        if (claimed != null) {
            return "<teammate_wake>\n你已从共享任务板认领任务 " + claimed.getId() + "：「" + claimed.getSubject()
                    + "」。\n任务描述：" + claimed.getDescription() + "\n请立即执行，完成后用 SendMessage 向 lead 汇报结果。\n"
                    + "</teammate_wake>";
        }
        return "<teammate_wake>\n邮箱有 " + messages.size()
                + " 条新消息（见上下文中的 teammate_message）。\n请处理后用 SendMessage 向 lead 汇报结果。\n"
                    + "</teammate_wake>";
    }

    /**
     * 构建队友 Agent（委托 SubAgentFactory）：EventFilter.forBranch 跨任务保留 branch 上下文 +
     * 追加团队协作工具（SendMessage / 任务板三件套）。
     */
    private Agent buildAgent(Session parentSession, TeammateRecord teammate, String projectPath) {
        AgentDefinition definition = definitionManager.getDefinition(teammate.getDefinition());
        if (definition == null) {
            throw new IllegalStateException("队友底层智能体定义不存在: " + teammate.getDefinition());
        }
        // 队友工具 = 团队协作工具（不含 SpawnTeammate/TeammateShutdown，防递归 spawn）
        List<ToolCallback> teamTools = List.of(
                SendMessageTool.builder()
                        .mailbox(mailbox)
                        .registry(registry)
                        .waker(this::wake)
                        .build()
                        .toToolCallback(),
                TaskListTool.builder().repository(taskBoard).build().toToolCallback(),
                TaskClaimTool.builder().repository(taskBoard).build().toToolCallback(),
                TaskCompleteTool.builder().repository(taskBoard).build().toToolCallback());
        Agent agent = subAgentFactory.build(parentSession, teammate.getDefinition(), teammate.branch(),
                projectPath, buildSystemPrompt(teammate, definition, projectPath), teamTools);
        log.info("[Teams] 构建队友 Agent: name={}, branch={}, definition={}", teammate.getName(),
                teammate.branch(), teammate.getDefinition());
        return agent;
    }

    /** 队友系统提示 = 底层定义 + 团队协作协议（职责/汇报/双事件纪律/任务板规则） */
    private String buildSystemPrompt(TeammateRecord teammate, AgentDefinition definition, String projectPath) {
        return definition.content() + ShellSession.envBlock() + """


                # 团队协作协议（你是持久队友，不是一次性子智能体）

                你是队友「%s」。职责：%s
                你的上下文跨任务保留 —— 记住之前的工作与沟通，不要重复询问已知信息。

                ## 工作规则
                1. **汇报优先**：完成当前工作后，必须用 SendMessage（to="lead"）汇报结果 —— 说清楚做了什么、验证了什么。
                2. **结果与空闲分开**：一条消息汇报结果（result），结束时空闲等待（"Waiting for more work" 由系统自动通知，无需你发）。
                3. **共享任务板**：可用 TaskList 查看项目任务；TaskClaim（owner="%s"）原子认领无主任务；完成后 TaskComplete 标记完成并解锁依赖任务。
                4. **邮箱协作**：上下文中的 <teammate_message> 是发给你的消息；你也可以用 SendMessage 联系其他队友或 lead。
                5. **收敛**：每回合处理完当前工作即汇报收尾，不要无限展开新工作。
                %s""".formatted(teammate.getName(),
                teammate.getDescription().isBlank() ? "通用协作" : teammate.getDescription(),
                teammate.getName(),
                projectPath != null ? "项目目录：" + projectPath : "");
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
