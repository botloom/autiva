package cn.bitloom.controller;

import cn.bitloom.cron.CronManager.CronTaskInfo;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.vm.TaskPageViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

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

    private final TaskPageViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderTasks();
    }

    private void renderTasks() {
        taskListContainer.getChildren().clear();
        viewModel.loadTasksAsync(() -> {
            if (viewModel.getTasks().isEmpty()) {
                return;
            }

            Map<String, List<CronTaskInfo>> tasksBySessionId = viewModel.getTasksGroupedBySessionId();

            VBox cardsContainer = new VBox();
            cardsContainer.getStyleClass().add("task-page__cards-container");
            cardsContainer.setSpacing(16);

            for (Map.Entry<String, List<CronTaskInfo>> entry : tasksBySessionId.entrySet()) {
                String sessionId = entry.getKey();
                List<CronTaskInfo> sessionTasks = entry.getValue();

                VBox sessionTaskList = new VBox();
                sessionTaskList.setSpacing(12);
                sessionTaskList.getStyleClass().add("task-page__session-list");

                for (CronTaskInfo task : sessionTasks) {
                    VBox card = createTaskCard(task);
                    sessionTaskList.getChildren().add(card);
                }

                TitledPane sessionCard = new TitledPane();
                sessionCard.setText(String.format("Session: %s (%d 个任务)", sessionId, sessionTasks.size()));
                sessionCard.setContent(sessionTaskList);
                sessionCard.getStyleClass().add("task-page__session-card");
                sessionCard.setExpanded(false);
                sessionCard.setAnimated(true);

                cardsContainer.getChildren().add(sessionCard);
            }

            taskListContainer.getChildren().add(cardsContainer);
        });
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

        Label typeLabel = new Label(viewModel.getTypeLabel(task.getType()));
        typeLabel.getStyleClass().add("task-page__card-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button triggerButton = new Button("触发");
        triggerButton.getStyleClass().add("task-page__card-btn");
        triggerButton.setOnAction(e -> viewModel.triggerTask(task.getSessionId(), task.getName()));

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("task-page__card-btn");
        deleteButton.setOnAction(e -> {
            viewModel.deleteTask(task.getSessionId(), task.getName());
            renderTasks();
        });

        header.getChildren().addAll(nameLabel, typeLabel, spacer, triggerButton, deleteButton);

        VBox content = new VBox();
        content.setSpacing(8);

        Label statusLabel = new Label("状态: " + (task.getScheduledFuture().isCancelled() ? "已取消" : "运行中"));
        statusLabel.getStyleClass().add("task-page__card-info");

        Label timeLabel = new Label("创建时间: " + viewModel.formatCreateTime(task.getCreateTime()));
        timeLabel.getStyleClass().add("task-page__card-info");

        Label configLabel = new Label(viewModel.getTaskConfig(task));
        configLabel.getStyleClass().add("task-page__card-info");

        Label messageLabel = new Label("消息: " + viewModel.truncateMessage(task.getMessage()));
        messageLabel.getStyleClass().add("task-page__card-description");

        content.getChildren().addAll(statusLabel, timeLabel, configLabel, messageLabel);

        card.getChildren().addAll(header, content);

        return card;
    }

    @Override
    public void show() {
        this.taskPage.setVisible(true);
        this.taskPage.setManaged(true);
        renderTasks();
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
                        event -> renderTasks()
                )
        );
    }
}
