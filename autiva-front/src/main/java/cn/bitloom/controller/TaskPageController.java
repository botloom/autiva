package cn.bitloom.controller;

import cn.bitloom.cron.CronManager;
import cn.bitloom.cron.CronManager.CronTaskInfo;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    private VBox taskPage;
    @FXML
    private VBox taskListContainer;

    @Getter
    @Setter
    private IndexController indexController;

    private final CronManager cronManager;
    private final ObservableList<CronTaskInfo> taskList = FXCollections.observableArrayList();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadTasks();
    }

    private void loadTasks() {
        taskListContainer.getChildren().clear();
        taskList.clear();

        Map<String, CronTaskInfo> tasks = cronManager.getAllTasks(null);
        taskList.addAll(tasks.values());

        if (taskList.isEmpty()) {
            return;
        }

        Map<String, List<CronTaskInfo>> tasksBySessionId = taskList.stream()
                .collect(Collectors.groupingBy(CronTaskInfo::getSessionId));

        VBox cardsContainer = new VBox();
        cardsContainer.getStyleClass().add("task-page__cards-container");
        cardsContainer.setSpacing(16);

        for (Map.Entry<String, List<CronTaskInfo>> entry : tasksBySessionId.entrySet()) {
            String sessionId = entry.getKey();
            List<CronTaskInfo> sessionTasks = entry.getValue();

            VBox sessionCard = new VBox();
            sessionCard.getStyleClass().add("task-page__session-card");

            HBox sessionHeader = new HBox();
            sessionHeader.getStyleClass().add("task-page__session-header");
            sessionHeader.setAlignment(Pos.CENTER_LEFT);

            Label sessionLabel = new Label(String.format("Session: %s (%d 个任务)", sessionId, sessionTasks.size()));
            sessionLabel.getStyleClass().add("task-page__session-title");
            sessionHeader.getChildren().add(sessionLabel);
            sessionCard.getChildren().add(sessionHeader);

            VBox sessionTaskList = new VBox();
            sessionTaskList.setSpacing(12);
            sessionTaskList.getStyleClass().add("task-page__session-list");

            for (CronTaskInfo task : sessionTasks) {
                VBox card = createTaskCard(task);
                sessionTaskList.getChildren().add(card);
            }

            sessionCard.getChildren().add(sessionTaskList);
            cardsContainer.getChildren().add(sessionCard);
        }

        taskListContainer.getChildren().add(cardsContainer);
    }

    private VBox createTaskCard(CronTaskInfo task) {
        VBox card = new VBox();
        card.getStyleClass().add("task-page__card");
        card.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox();
        header.getStyleClass().add("task-page__card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);

        Label nameLabel = new Label(task.getName());
        nameLabel.getStyleClass().add("task-page__card-title");

        Label typeLabel = new Label(getTypeLabel(task.getType()));
        typeLabel.getStyleClass().add("task-page__card-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button triggerButton = new Button("触发");
        triggerButton.getStyleClass().add("task-page__card-btn");
        triggerButton.setOnAction(e ->  cronManager.triggerTask(task.getSessionId(), task.getName()));

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("task-page__card-btn");
        deleteButton.setOnAction(e -> {
            cronManager.deleteTask(task.getSessionId(), task.getName());
            loadTasks();
        });

        header.getChildren().addAll(nameLabel, typeLabel, spacer, triggerButton, deleteButton);

        VBox content = new VBox();
        content.setSpacing(8);

        Label statusLabel = new Label("状态: " + (task.getScheduledFuture().isCancelled() ? "已取消" : "运行中"));
        statusLabel.getStyleClass().add("task-page__card-info");

        Label timeLabel = new Label("创建时间: " + formatter.format(task.getCreateTime()));
        timeLabel.getStyleClass().add("task-page__card-info");

        Label configLabel = new Label(getTaskConfig(task));
        configLabel.getStyleClass().add("task-page__card-info");

        Label messageLabel = new Label("消息: " + truncateMessage(task.getMessage()));
        messageLabel.getStyleClass().add("task-page__card-description");

        content.getChildren().addAll(statusLabel, timeLabel, configLabel, messageLabel);

        card.getChildren().addAll(header, content);

        return card;
    }

    private String getTypeLabel(String type) {
        return switch (type.toLowerCase()) {
            case "once" -> "一次性任务";
            case "interval" -> "周期性任务";
            case "cron" -> "Cron任务";
            default -> type;
        };
    }

    private String getTaskConfig(CronTaskInfo task) {
        return switch (task.getType().toLowerCase()) {
            case "once" -> "延迟: " + task.getDelaySeconds() + "秒";
            case "interval" -> {
                StringBuilder sb = new StringBuilder("间隔: " + task.getIntervalSeconds() + "秒");
                if (task.getDelaySeconds() != null && task.getDelaySeconds() > 0) {
                    sb.append(", 初始延迟: ").append(task.getDelaySeconds()).append("秒");
                }
                yield sb.toString();
            }
            case "cron" -> "Cron表达式: " + task.getCronExpression();
            default -> "";
        };
    }

    private String truncateMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= 50) {
            return message;
        }
        return message.substring(0, 50) + "...";
    }

    @Override
    public void show() {
        this.taskPage.setVisible(true);
        this.taskPage.setManaged(true);
        loadTasks();
    }

    @Override
    public void hide() {
        this.taskPage.setVisible(false);
        this.taskPage.setManaged(false);
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "refreshTaskButton",
                        "刷新",
                        "dynamic-btn",
                        event -> loadTasks()
                )
        );
    }
}
