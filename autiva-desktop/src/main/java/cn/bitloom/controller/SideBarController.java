package cn.bitloom.controller;

import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.SvgImageView;
import cn.bitloom.router.RouteConfig;
import cn.bitloom.store.Store;
import cn.bitloom.vm.HomePageViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Component
@RequiredArgsConstructor
public class SideBarController implements Initializable, PageHolder {

    private static final String ACTIVE_CSS_CLASS = "sidebar__option--active";
    private static final String HISTORY_ACTIVE_CSS_CLASS = "sidebar__history-item--active";

    private final FileSystemSessionManager fileSystemSessionManager;
    private final HomePageViewModel homePageViewModel;

    @FXML
    private VBox sideBar;
    @FXML
    private HBox homeOption;
    @FXML
    private HBox agentOption;
    @FXML
    private HBox settingsOption;
    @FXML
    private HBox skillOption;
    @FXML
    private HBox gepOption;
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

        this.routeOptionMap = new LinkedHashMap<>();
        this.routeOptionMap.put(RouteConfig.Path.HOME, this.homeOption);
        this.routeOptionMap.put(RouteConfig.Path.AGENT, this.agentOption);
        this.routeOptionMap.put(RouteConfig.Path.SKILLS, this.skillOption);
        this.routeOptionMap.put(RouteConfig.Path.GEP, this.gepOption);
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
            homePageViewModel.createNewSession();
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

        // 监听刷新信号（聊天过程中触发标题更新）
        Store.refreshHistory.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        refreshHistoryList();
    }

    private void resetChatUI() {
        HomePageController homeController = this.indexController != null
                ? this.indexController.getHomePageController() : null;
        if (homeController != null) {
            homeController.resetForNewSession();
        }
    }

    public void refreshHistoryList() {
        List<Session> sessions = fileSystemSessionManager.getDesktopSessions();
        String currentSessionId = Store.currentSessionId.get();

        // 移除已删除的 session 对应的项
        java.util.Set<String> activeIds = new java.util.HashSet<>();
        for (Session s : sessions) {
            activeIds.add(s.getId());
        }
        historyItemMap.keySet().removeIf(id -> {
            if (!activeIds.contains(id)) {
                historyList.getChildren().remove(historyItemMap.get(id));
                return true;
            }
            return false;
        });

        // 增量添加/更新
        int insertIndex = 0;
        for (Session session : sessions) {
            HBox existingItem = historyItemMap.get(session.getId());
            if (existingItem == null) {
                HBox item = createHistoryItem(session);
                historyItemMap.put(session.getId(), item);
                historyList.getChildren().add(insertIndex, item);
            } else {
                // 更新标题（如有变化）
                updateHistoryItemTitle(existingItem, session);
                // 调整顺序
                int currentIndex = historyList.getChildren().indexOf(existingItem);
                if (currentIndex != insertIndex) {
                    historyList.getChildren().remove(existingItem);
                    historyList.getChildren().add(insertIndex, existingItem);
                }
            }
            insertIndex++;
        }

        // 更新高亮
        activeHistoryItem = null;
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

    private void updateHistoryItemTitle(HBox item, Session session) {
        // 更新标题 Label（第一个子节点是 VBox textContainer，其第一个子节点是 titleLabel）
        if (!item.getChildren().isEmpty() && item.getChildren().get(0) instanceof VBox textContainer) {
            if (!textContainer.getChildren().isEmpty() && textContainer.getChildren().get(0) instanceof Label titleLabel) {
                String title = session.getTitle();
                // 仅当标题仍为默认值时，取第一条用户消息作为标题
                if ("新对话".equals(title)) {
                    List<Message> messages = session.getMessages();
                    for (Message msg : messages) {
                        if ("USER".equals(msg.getMessageType().name()) && msg.getText() != null && !msg.getText().isBlank()) {
                            String text = msg.getText().replace("\n", " ").trim();
                            String newTitle = text.length() > 20 ? text.substring(0, 20) + "..." : text;
                            // 持久化到 Session
                            fileSystemSessionManager.updateSession(session.getId(), s -> s.setTitle(newTitle));
                            title = newTitle;
                            break;
                        }
                    }
                }
                titleLabel.setText(title);
            }
        }
    }

    private HBox createHistoryItem(Session session) {
        HBox item = new HBox();
        item.getStyleClass().add("sidebar__history-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setSpacing(4);

        VBox textContainer = new VBox();
        textContainer.getStyleClass().add("sidebar__history-text");
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        Label titleLabel = new Label(session.getTitle());
        titleLabel.getStyleClass().add("sidebar__history-item-title");

        Label timeLabel = new Label(formatTime(fileSystemSessionManager.getSessionLastActiveTime(session.getId())));
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
            String sessionId = session.getId();
            fileSystemSessionManager.delete(sessionId);

            String currentId = Store.currentSessionId.get();
            if (sessionId.equals(currentId)) {
                // 删除的是当前会话，切换到初始态
                homePageViewModel.createNewSession();
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
                homePageViewModel.switchToSession(session.getId());
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
