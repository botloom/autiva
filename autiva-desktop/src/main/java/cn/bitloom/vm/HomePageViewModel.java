package cn.bitloom.vm;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.agentic.project.ProjectRegistry;
import cn.bitloom.agentic.session.*;
import cn.bitloom.node.message.AssistantMessageCard;
import cn.bitloom.node.message.MessageCard;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.node.message.UserMessageCard;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class HomePageViewModel {

    private final FileSystemSessionManager fileSystemSessionManager;
    private final ProjectRegistry projectRegistry;

    @Getter
    private final ObservableList<MessageCard> messages = FXCollections.observableArrayList();

    private final ObjectProperty<ProjectInfo> currentProject = new SimpleObjectProperty<>();

    /**
     * 当前项目属性（供 Controller 监听）
     */
    public ObjectProperty<ProjectInfo> currentProjectProperty() {
        return currentProject;
    }

    private Session session;
    private AssistantMessageCard currentAssistantCard = null;
    private Disposable outBoxSubscription;
    /**
     * 历史消息加载标志：prepareHistoricalMessages 期间为 true，用于跳过工具消息（历史工具不显示）
     */
    private boolean isLoadingHistory = false;

    public HomePageViewModel(FileSystemSessionManager fileSystemSessionManager, ProjectRegistry projectRegistry) {
        this.fileSystemSessionManager = fileSystemSessionManager;
        this.projectRegistry = projectRegistry;
    }

    private void subscribeOutBox() {
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
        }
        this.outBoxSubscription = EventBus.outBoxFlux()
                .doOnNext(event -> {
                    if (event instanceof MessageEvent messageEvent
                            && this.session != null
                            && this.session.getId().equals(messageEvent.getSessionId())) {
                        Platform.runLater(() -> this.processEvent(messageEvent));
                    }
                })
                .subscribe();
    }

    public void createNewSession() {
        this.session = null;
        Store.currentSessionId.set("");
        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.outBoxSubscription != null) {
            this.outBoxSubscription.dispose();
            this.outBoxSubscription = null;
        }
    }

    public void switchToSession(String sessionId) {
        if (this.session != null && sessionId.equals(this.session.getId())) {
            return;
        }

        Session targetSession = fileSystemSessionManager.getById(sessionId);
        if (targetSession == null) {
            log.warn("切换到不存在的session: {}", sessionId);
            return;
        }

        // 同步激活（activate 只加载最近 100 条消息，速度足够快）
        fileSystemSessionManager.activate(sessionId);

        this.session = targetSession;
        Store.currentSessionId.set(sessionId);
        Store.selectedModel.set(targetSession.getModel());
        Store.currentAgent.set(targetSession.getAgentId());

        subscribeOutBox();

        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);

        if (hasHistoricalMessages()) {
            prepareHistoricalMessages();
        }
    }

    public void switchAgent(String agentId) {
        Store.currentAgent.set(agentId);
        // 非 coder 智能体时清空当前项目
        if (!"coder".equals(agentId)) {
            currentProject.set(null);
        }
        createNewSession();
    }

    /**
     * 设置当前编码项目
     */
    public void setCurrentProject(ProjectInfo project) {
        currentProject.set(project);
    }

    /**
     * 获取当前编码项目
     */
    public ProjectInfo getCurrentProject() {
        return currentProject.get();
    }

    /**
     * 列出所有已注册项目
     */
    public List<ProjectInfo> listProjects() {
        return projectRegistry.listProjects();
    }

    /**
     * 新建项目并设为当前
     */
    public ProjectInfo createNewProject(String name) throws java.io.IOException {
        ProjectInfo project = projectRegistry.createProject(name);
        currentProject.set(project);
        return project;
    }

    /**
     * 注册本地文件夹并设为当前
     */
    public void registerLocalProject(String path, String name) throws java.io.IOException {
        ProjectInfo project = projectRegistry.registerLocal(path, name);
        currentProject.set(project);
    }

    public void prepareHistoricalMessages() {
        // 从 events.jsonl 加载所有未压缩的历史事件（memoryCursor 到末尾）
        int memoryCursor = this.session.getMemoryCursor();
        List<AbstractEvent> events = fileSystemSessionManager.loadEvents(
                this.session.getId(), memoryCursor, Integer.MAX_VALUE);
        if (events.isEmpty()) {
            return;
        }

        // 历史加载期间跳过工具消息（历史工具不显示）
        isLoadingHistory = true;
        try {
            // 分批渲染避免阻塞 FX 线程
            int batchSize = 20;
            for (int i = 0; i < events.size(); i += batchSize) {
                int end = Math.min(i + batchSize, events.size());
                List<AbstractEvent> batch = new ArrayList<>(events.subList(i, end));
                boolean isLastBatch = end == events.size();
                if (i == 0) {
                    // 首批同步处理
                    for (AbstractEvent event : batch) {
                        if (event instanceof MessageEvent me) {
                            processEvent(me);
                        }
                    }
                    if (isLastBatch) {
                        isLoadingHistory = false;
                    }
                } else {
                    Platform.runLater(() -> {
                        for (AbstractEvent event : batch) {
                            if (event instanceof MessageEvent me) {
                                processEvent(me);
                            }
                        }
                        if (isLastBatch) {
                            isLoadingHistory = false;
                        }
                    });
                }
            }
        } catch (Exception e) {
            isLoadingHistory = false;
            throw e;
        }
    }

    public boolean hasHistoricalMessages() {
        return this.session != null
                && fileSystemSessionManager.countEvents(this.session.getId()) > this.session.getMemoryCursor();
    }

    // ===== 事件处理 =====

    private void processEvent(MessageEvent event) {
        if (event.isUserMessage()) {
            processUserEvent(event);
        } else if (event.isAssistantMessage()) {
            processAssistantEvent(event);
        } else if (event.isToolResponse()) {
            processToolEvent(event);
        } else {
            log.warn("未处理的事件类型: {}", event.getType());
        }
    }

    private void processUserEvent(MessageEvent e) {
        currentAssistantCard = null;
        messages.add(new UserMessageCard(e.getText()));
    }

    private void processAssistantEvent(MessageEvent e) {
        String finishReason = e.getFinishReason();
        String text = e.getText();

        if (finishReason == null || finishReason.isBlank()) {
            // 流式 chunk：直接累积
            if (Store.isPaused.get()) {
                return;
            }
            if (currentAssistantCard == null) {
                currentAssistantCard = new AssistantMessageCard();
                messages.add(currentAssistantCard);
            }
            currentAssistantCard.appendContent(text);
        } else if ("STOP".equals(finishReason)) {
            // 结束流式
            Store.isStreaming.set(false);
            Store.isPaused.set(false);

            if (currentAssistantCard != null) {
                currentAssistantCard.complete("STOP");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            } else if (text != null && !text.isBlank()) {
                // 非流式消息（历史消息或一次性输出）
                messages.add(new AssistantMessageCard(text, "STOP"));
            }
        } else if ("TOOL_CALLS".equals(finishReason)) {
            // 工具调用：结束当前流式消息
            if (currentAssistantCard != null) {
                currentAssistantCard.complete("TOOL_CALLS");
                if (currentAssistantCard.isValid()) {
                    messages.remove(currentAssistantCard);
                }
                currentAssistantCard = null;
            }

            // 创建工具调用卡片（历史加载期间跳过：历史工具不显示）
            if (e.getToolCalls() != null && !isLoadingHistory) {
                for (MessageEvent.ToolCallInfo tc : e.getToolCalls()) {
                    messages.add(new ToolMessageCard(tc.name(), tc.arguments(), true));
                }
            }
        }
    }

    private void processToolEvent(MessageEvent e) {
        // 历史加载期间跳过工具响应消息（历史工具不显示）
        if (isLoadingHistory) {
            return;
        }
        if (e.getResponses() != null && !e.getResponses().isEmpty()) {
            for (MessageEvent.ToolResponseInfo resp : e.getResponses()) {
                messages.add(new ToolMessageCard(resp.name(), resp.responseData(), false));
            }
        }
    }

    public void addUserMessage(String text) {
        messages.add(new UserMessageCard(text));
    }

    public void sendMessage(String text) {
        if (this.session == null) {
            this.session = fileSystemSessionManager.create(
                    Store.currentAgent.get(), null, SessionTypeEnum.DM,
                    SessionRespTypeEnum.STREAM, Store.selectedModel.get());
            Store.currentSessionId.set(this.session.getId());
            subscribeOutBox();
        } else {
            fileSystemSessionManager.activate(this.session.getId());
            subscribeOutBox();
        }
        Store.isStreaming.set(true);
        Store.isPaused.set(false);
        // coder 智能体且有当前项目时，将项目信息附加到消息中
        String messageText = buildMessageWithContext(text);
        EventBus.publishIn(MessageEvent.userMessage(this.session.getId(), messageText));
        // 触发侧边栏刷新（更新会话标题）
        Store.refreshHistory.set(!Store.refreshHistory.get());
    }

    /**
     * 构建带项目上下文的消息
     * coder 智能体且有 currentProject 时，在消息前添加项目信息
     */
    private String buildMessageWithContext(String text) {
        String agentId = Store.currentAgent.get();
        ProjectInfo project = currentProject.get();
        if ("coder".equals(agentId) && project != null) {
            return "[项目: " + project.name() + " @ " + project.path() + "]\n" + text;
        }
        return text;
    }

    public void clear() {
        messages.clear();
        currentAssistantCard = null;
        Store.isStreaming.set(false);
        Store.isPaused.set(false);
        if (this.session != null) {
            fileSystemSessionManager.clear(this.session.getId());
        }
    }

    public void pauseGeneration() {
        if (Store.isStreaming.get() && !Store.isPaused.get()) {
            Store.isPaused.set(true);
            if (this.session != null) {
                fileSystemSessionManager.stopSession(this.session.getId());
            }

            if (currentAssistantCard != null) {
                currentAssistantCard.setStreaming(false);
                currentAssistantCard = null;
            }

        }
    }

}
