package cn.bitloom.controller;

import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.ProjectRegistry;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.window.WindowManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * 项目选择对话框控制器
 */
@Slf4j
@Component
public class ProjectPickerDialogController implements Initializable, WindowManager.StageAware, DialogHolder {

    @FXML
    private ListView<ProjectInfo> projectListView;
    @FXML
    private Button newProjectButton;
    @FXML
    private Button openLocalButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button confirmButton;

    private final ProjectRegistry projectRegistry;
    private Stage stage;
    @Setter
    private Consumer<ProjectInfo> onProjectSelected;

    private ProjectInfo selectedProject;

    public ProjectPickerDialogController(ProjectRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupProjectList();
        setupButtons();
        loadProjects();
    }

    private void setupProjectList() {
        projectListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox cell = new HBox(8);
                    cell.setAlignment(Pos.CENTER_LEFT);

                    Label nameLabel = new Label(item.name());
                    nameLabel.getStyleClass().add("project-picker__item-name");

                    Label pathLabel = new Label(item.path());
                    pathLabel.getStyleClass().add("project-picker__item-path");

                    Label branchLabel = new Label(item.gitBranch() != null ? "  [" + item.gitBranch() + "]" : "");
                    branchLabel.getStyleClass().add("project-picker__item-branch");

                    cell.getChildren().addAll(nameLabel, pathLabel, branchLabel);
                    setGraphic(cell);
                }
            }
        });

        projectListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedProject = newVal;
            confirmButton.setDisable(newVal == null);
        });

        projectListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleConfirm();
            }
        });
    }

    private void setupButtons() {
        newProjectButton.setOnAction(event -> handleNewProject());
        openLocalButton.setOnAction(event -> handleOpenLocal());
        cancelButton.setOnAction(event -> handleCancel());
        confirmButton.setOnAction(event -> handleConfirm());
    }

    private void loadProjects() {
        List<ProjectInfo> projects = projectRegistry.listProjects();
        projectListView.getItems().clear();
        projectListView.getItems().addAll(projects);
    }

    private void handleNewProject() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("新建项目");
        dialog.setHeaderText("请输入项目名称");
        dialog.setContentText("项目名:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.trim().isEmpty()) {
                return;
            }
            try {
                ProjectInfo project = projectRegistry.createProject(name.trim());
                loadProjects();
                projectListView.getSelectionModel().select(project);
            } catch (Exception e) {
                log.error("创建项目失败", e);
                showError("创建项目失败: " + e.getMessage());
            }
        });
    }

    private void handleOpenLocal() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择本地文件夹");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            String name = selected.getName();
            try {
                ProjectInfo project = projectRegistry.registerLocal(selected.getAbsolutePath(), name);
                loadProjects();
                projectListView.getSelectionModel().select(project);
            } catch (Exception e) {
                log.error("注册本地项目失败", e);
                showError("注册本地项目失败: " + e.getMessage());
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void handleCancel() {
        if (stage != null) {
            stage.close();
        }
    }

    private void handleConfirm() {
        if (selectedProject != null) {
            if (onProjectSelected != null) {
                onProjectSelected.accept(selectedProject);
            }
            if (stage != null) {
                stage.close();
            }
        }
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public double getWidth() {
        return 500;
    }

    @Override
    public double getHeight() {
        return 400;
    }

    @Override
    public boolean isResizable() {
        return true;
    }
}
