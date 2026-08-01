package cn.bitloom.controller;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.ProjectRegistry;
import cn.bitloom.router.RouteConfig;
import cn.bitloom.store.Store;
import cn.bitloom.vm.AbstractHomePageViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import cn.bitloom.agentic.session.Session;

@Component
@RequiredArgsConstructor
public class SideBarController implements Initializable, PageHolder {

    private static final String ACTIVE_CSS_CLASS = "sidebar__option--active";
    private static final String HISTORY_ACTIVE_CSS_CLASS = "sidebar__history-item--active";

    private final FileSystemSessionManager fileSystemSessionManager;
    private final ProjectRegistry projectRegistry;

    @FXML
    private VBox sideBar;
    @FXML
    private HBox modeSwitcher;
    @FXML
    private ToggleButton defaultModeBtn;
    @FXML
    private ToggleButton coderModeBtn;
    @FXML
    private HBox homeOption;
    @FXML
    private HBox agentOption;
    @FXML
    private HBox settingsOption;
    @FXML
    private HBox skillOption;
    @FXML
    private HBox taskOption;
    @FXML
    private ScrollPane historyScrollPane;
    @FXML
    private VBox historyList;

    @Getter
    @Setter
    private IndexController indexController;

    private Map<String, HBox> routeOptionMap;
    private HBox activeHistoryItem = null;
    private final Map<String, HBox> historyItemMap = new LinkedHashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.hide();

        // ===== 智能体模式分段切换按钮 =====
        ToggleGroup modeGroup = new ToggleGroup();
        this.defaultModeBtn.setToggleGroup(modeGroup);
        this.coderModeBtn.setToggleGroup(modeGroup);
        // 初始选中态：根据 Store.currentAgent 决定
        if (AgentMode.CODER.matches(Store.currentAgent.get())) {
            this.coderModeBtn.setSelected(true);
        } else {
            this.defaultModeBtn.setSelected(true);
        }
        // 点击切换：work 段 → switchAgent("work")；code 段 → switchAgent("code")
        // 同时重置 UI 并导航到首页（与"新聊天"按钮行为一致）
        this.defaultModeBtn.setOnAction(e -> {
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm != null) vm.switchAgent("work");
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });
        this.coderModeBtn.setOnAction(e -> {
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm != null) vm.switchAgent("code");
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });
        // 监听 Store.currentAgent 变化，同步选中态（切换历史会话时触发）
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                if (AgentMode.CODER.matches(newVal)) {
                    if (!coderModeBtn.isSelected()) coderModeBtn.setSelected(true);
                } else {
                    if (!defaultModeBtn.isSelected()) defaultModeBtn.setSelected(true);
                }
            });
        });

        this.routeOptionMap = new LinkedHashMap<>();
        this.routeOptionMap.put(RouteConfig.Path.HOME, this.homeOption);
        this.routeOptionMap.put(RouteConfig.Path.AGENT, this.agentOption);
        this.routeOptionMap.put(RouteConfig.Path.SKILLS, this.skillOption);
        this.routeOptionMap.put(RouteConfig.Path.TASK, this.taskOption);
        this.routeOptionMap.put(RouteConfig.Path.SETTINGS, this.settingsOption);

        this.routeOptionMap.forEach((path, option) -> {
            option.setOnMouseClicked(event -> {
                if (this.indexController != null) {
                    this.indexController.navigate(path);
                }
            });
        });

        // "新聊天"按钮：切换到初始态并导航到首页
        this.homeOption.setOnMouseClicked(event -> {
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm != null) vm.createNewSession();
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });

        this.homeOption.getStyleClass().add(ACTIVE_CSS_CLASS);

        // 监听 session 切换，刷新历史列表
        Store.currentSessionId.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        // 监听智能体切换，刷新历史列表（按 agentId 过滤）
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        // 监听刷新信号（聊天过程中触发标题更新）
        Store.refreshHistory.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        refreshHistoryList();
    }

    private void resetChatUI() {
        AbstractHomePageController homeController = this.indexController != null
                ? this.indexController.getHomePageController() : null;
        if (homeController != null) {
            homeController.resetForNewSession();
        }
    }

    /**
     * 获取当前活跃的首页 viewModel（coder 或 work）
     */
    private AbstractHomePageViewModel currentViewModel() {
        if (this.indexController != null && this.indexController.getHomePageController() != null) {
            return this.indexController.getHomePageController().getViewModel();
        }
        return null;
    }

    public void refreshHistoryList() {
        String currentAgent = Store.currentAgent.get();
        boolean isCoder = AgentMode.CODER.matches(currentAgent);
        String prefix = isCoder ? "code-" : "work-";
        List<Session> sessions = fileSystemSessionManager.findByUserId(Store.userId.get()).stream()
                .filter(s -> s.id().startsWith(prefix))
                .sorted((a, b) -> {
                    Object aUpd = a.metadata().get("updateAt");
                    Object bUpd = b.metadata().get("updateAt");
                    long aTime = aUpd instanceof Number n ? n.longValue() : (a.createdAt() != null ? a.createdAt().toEpochMilli() : 0L);
                    long bTime = bUpd instanceof Number m ? m.longValue() : (b.createdAt() != null ? b.createdAt().toEpochMilli() : 0L);
                    return Long.compare(bTime, aTime);
                })
                .toList();
        String currentSessionId = Store.currentSessionId.get();

        // 全量重建
        historyList.getChildren().clear();
        historyItemMap.clear();
        activeHistoryItem = null;

        if (isCoder) {
            // code 模式：按项目分组
            renderProjectGroupedHistory(sessions);
        } else {
            // work 模式：平铺
            for (Session session : sessions) {
                HBox item = createHistoryItem(session);
                historyItemMap.put(session.id(), item);
                historyList.getChildren().add(item);
            }
        }

        // 更新高亮
        for (Map.Entry<String, HBox> entry : historyItemMap.entrySet()) {
            HBox item = entry.getValue();
            if (entry.getKey().equals(currentSessionId)) {
                if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                    item.getStyleClass().add(HISTORY_ACTIVE_CSS_CLASS);
                }
                item.setStyle(null);
                activeHistoryItem = item;
            } else {
                item.getStyleClass().remove(HISTORY_ACTIVE_CSS_CLASS);
                item.setStyle("-fx-background-color: transparent;");
            }
        }
    }

    /**
     * code 模式：按项目分组渲染历史对话。
     * 每个项目是一个折叠卡片，展开后显示该项目下的 session 列表。
     */
    private void renderProjectGroupedHistory(List<Session> sessions) {
        List<ProjectInfo> projects = projectRegistry.listProjects();
        if (projects.isEmpty()) {
            return;
        }

        for (ProjectInfo project : projects) {
            List<Session> projectSessions = sessions.stream()
                    .filter(s -> project.id().equals(s.metadata().get("projectId")))
                    .toList();

            VBox projectCard = createProjectCard(project, projectSessions);
            historyList.getChildren().add(projectCard);
        }
    }

    /**
     * 创建项目折叠卡片。
     */
    private VBox createProjectCard(ProjectInfo project, List<Session> projectSessions) {
        VBox card = new VBox();
        card.getStyleClass().add("sidebar__project-card");

        // 项目标题行
        HBox header = new HBox();
        header.getStyleClass().add("sidebar__project-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        // 文件夹图标
        SvgImageView folderIcon = new SvgImageView();
        folderIcon.setFitWidth(16);
        folderIcon.setFitHeight(16);
        folderIcon.setSvgPath("/cn/bitloom/images/folder.svg");
        folderIcon.getStyleClass().add("sidebar__project-icon");

        Label nameLabel = new Label(project.name());
        nameLabel.getStyleClass().add("sidebar__project-name");

        // 弹性占位，把按钮推到最右
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 新建对话按钮（与上方新对话图标一致，默认隐藏，悬浮显示）
        Button newChatBtn = new Button();
        newChatBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView newChatIcon = new SvgImageView();
        newChatIcon.setFitWidth(14);
        newChatIcon.setFitHeight(14);
        newChatIcon.setSvgPath("/cn/bitloom/images/chat-new.svg");
        newChatBtn.setGraphic(newChatIcon);
        newChatBtn.setVisible(false);
        newChatBtn.setOnAction(e -> {
            e.consume();
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm instanceof cn.bitloom.vm.CoderHomePageViewModel coderVm) {
                coderVm.setCurrentProject(project);
            }
            if (vm != null) vm.createNewSession();
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });

        header.getChildren().addAll(folderIcon, nameLabel, spacer, newChatBtn);

        header.setOnMouseEntered(e -> newChatBtn.setVisible(true));
        header.setOnMouseExited(e -> newChatBtn.setVisible(false));

        // session 列表容器（默认展开）
        VBox sessionList = new VBox();
        sessionList.getStyleClass().add("sidebar__project-sessions");
        sessionList.setVisible(true);
        sessionList.setManaged(true);

        for (Session session : projectSessions) {
            HBox item = createHistoryItem(session);
            historyItemMap.put(session.id(), item);
            sessionList.getChildren().add(item);
        }

        // 点击项目名展开/折叠（按钮点击不触发）
        header.setOnMouseClicked(e -> {
            if (e.getTarget() == newChatBtn || e.getTarget() == newChatIcon) {
                return;
            }
            boolean expanded = sessionList.isVisible();
            sessionList.setVisible(!expanded);
            sessionList.setManaged(!expanded);
        });

        card.getChildren().addAll(header, sessionList);
        return card;
    }

    /**
     * 获取 session 标题：优先从 metadata 取，没有则从 events.jsonl 提取第一条 USER 消息并持久化。
     */
    private String resolveSessionTitle(Session session) {
        Object titleObj = session.metadata().get("title");
        String title = titleObj != null ? titleObj.toString() : "新对话";
        if (!"新对话".equals(title)) {
            return title;
        }
        List<AbstractEvent> events = fileSystemSessionManager.getEvents(session.id(),
                EventFilter.builder().page(0).pageSize(10).build());
        for (AbstractEvent event : events) {
            if (event instanceof MessageEvent me && me.isUserMessage()
                    && me.getText() != null && !me.getText().isBlank()) {
                String text = me.getText().replace("\n", " ").trim();
                String newTitle = text.length() > 20 ? text.substring(0, 20) + "..." : text;
                Map<String, Object> md = new HashMap<>(session.metadata());
                md.put("title", newTitle);
                Session updated = Session.builder()
                        .id(session.id())
                        .userId(session.userId())
                        .createdAt(session.createdAt())
                        .expiresAt(session.expiresAt())
                        .metadata(md)
                        .build();
                fileSystemSessionManager.persistSession(updated);
                return newTitle;
            }
        }
        return title;
    }

    private HBox createHistoryItem(Session session) {
        HBox item = new HBox();
        item.getStyleClass().add("sidebar__history-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setSpacing(4);

        VBox textContainer = new VBox();
        textContainer.getStyleClass().add("sidebar__history-text");
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        Label titleLabel = new Label(resolveSessionTitle(session));
        titleLabel.getStyleClass().add("sidebar__history-item-title");

        Object updObj = session.metadata().get("updateAt");
        long lastActiveTime = updObj instanceof Number n ? n.longValue() : (session.createdAt() != null ? session.createdAt().toEpochMilli() : 0L);
        Label timeLabel = new Label(formatTime(lastActiveTime));
        timeLabel.getStyleClass().add("sidebar__history-item-time");

        textContainer.getChildren().addAll(titleLabel, timeLabel);

        // 删除按钮
        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView deleteIcon = new SvgImageView();
        deleteIcon.setFitWidth(14);
        deleteIcon.setFitHeight(14);
        deleteIcon.setSvgPath("/cn/bitloom/images/trash.svg");
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.setVisible(false);

        deleteBtn.setOnAction(e -> {
            e.consume();
            String sessionId = session.id();
            fileSystemSessionManager.remove(sessionId);

            String currentId = Store.currentSessionId.get();
            if (sessionId.equals(currentId)) {
                // 删除的是当前会话，切换到初始态
                AbstractHomePageViewModel vm = currentViewModel();
                if (vm != null) vm.createNewSession();
                resetChatUI();
            }
            refreshHistoryList();
        });

        item.setOnMouseEntered(event -> {
            if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                item.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05);");
            }
            deleteBtn.setVisible(true);
        });
        item.setOnMouseExited(event -> {
            if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                item.setStyle("-fx-background-color: transparent;");
            }
            deleteBtn.setVisible(false);
        });

        item.getChildren().addAll(textContainer, deleteBtn);

        item.setOnMouseClicked(event -> {
            if (event.getTarget() != deleteBtn && event.getTarget() != deleteIcon) {
                AbstractHomePageViewModel vm = currentViewModel();
                if (vm != null) vm.switchToSession(session.id());
                resetChatUI();
                if (this.indexController != null) {
                    this.indexController.navigate(RouteConfig.Path.HOME);
                }
                updateHistoryActiveState(item);
            }
        });

        return item;
    }

    private void updateHistoryActiveState(HBox newActive) {
        if (activeHistoryItem != null) {
            activeHistoryItem.getStyleClass().remove(HISTORY_ACTIVE_CSS_CLASS);
            activeHistoryItem.setStyle("-fx-background-color: transparent;");
        }
        newActive.getStyleClass().add(HISTORY_ACTIVE_CSS_CLASS);
        newActive.setStyle(null);
        activeHistoryItem = newActive;
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        if (dateTime.toLocalDate().equals(now.toLocalDate())) {
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return dateTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    }

    public void updateActiveState(String path) {
        this.routeOptionMap.values().forEach(option ->
                option.getStyleClass().remove(ACTIVE_CSS_CLASS));

        HBox activeOption = this.routeOptionMap.get(path);
        if (activeOption != null) {
            activeOption.getStyleClass().add(ACTIVE_CSS_CLASS);
        }
    }

    @Override
    public void show() {
        this.sideBar.setVisible(true);
        this.sideBar.setManaged(true);
    }

    @Override
    public void hide() {
        this.sideBar.setVisible(false);
        this.sideBar.setManaged(false);
    }

    public boolean isSidebarVisible() {
        return this.sideBar != null && this.sideBar.isVisible();
    }

}
