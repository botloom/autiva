package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.project.FileTreeService;
import cn.bitloom.agentic.project.ProjectInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 右侧边栏控制器
 * 管理项目文件树和修改文件列表（diff），是主区域的一部分
 */
@Slf4j
@Component
public class RightSidebarController implements Initializable {

    @FXML
    @Getter
    private VBox rightSidebar;
    @FXML
    private SplitPane splitPane;
    @FXML
    private TreeView<Path> fileTree;
    @FXML
    private ListView<FileDiff> diffList;

    @Getter
    @Setter
    private IndexController indexController;

    private final FileTreeService fileTreeService;
    private final DiffService diffService;

    private ProjectInfo currentProject;
    private Disposable diffEventSubscription;

    public RightSidebarController(FileTreeService fileTreeService, DiffService diffService) {
        this.fileTreeService = fileTreeService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFileTree();
        setupDiffList();
        subscribeDiffEvents();
    }

    /**
     * 设置文件树
     */
    private void setupFileTree() {
        fileTree.setCellFactory(tree -> new cn.bitloom.node.project.FileTreeCell());
        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selected = fileTree.getSelectionModel().getSelectedItem();
                if (selected != null && Files.isRegularFile(selected.getValue())) {
                    if (indexController != null) {
                        indexController.showFileInPanel(selected.getValue());
                    }
                }
            }
        });
    }

    /**
     * 设置 diff 列表
     */
    private void setupDiffList() {
        diffList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileDiff item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("right-sidebar__diff-item--add",
                            "right-sidebar__diff-item--modify", "right-sidebar__diff-item--delete");
                } else {
                    setText(item.filePath());
                    getStyleClass().add("right-sidebar__diff-item");
                }
            }
        });
        diffList.setOnMouseClicked(event -> {
            FileDiff selected = diffList.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 1) {
                if (indexController != null) {
                    indexController.showDiffView(selected);
                }
            }
        });
    }

    /**
     * 订阅 DiffEvent 自动刷新 diff 列表
     */
    private void subscribeDiffEvents() {
        this.diffEventSubscription = EventBus.outBoxFlux()
                .filter(event -> event instanceof DiffEvent)
                .subscribe(event -> {
                    if (event instanceof DiffEvent) {
                        List<FileDiff> pendingDiffs = diffService.getPendingDiffs();
                        Platform.runLater(() -> updateDiffList(pendingDiffs));
                    }
                });
    }

    /**
     * 显示右侧边栏
     */
    public void show() {
        rightSidebar.setVisible(true);
        rightSidebar.setManaged(true);
    }

    /**
     * 隐藏右侧边栏
     */
    public void hide() {
        rightSidebar.setVisible(false);
        rightSidebar.setManaged(false);
    }

    /**
     * 切换显示状态
     */
    public void toggle() {
        if (rightSidebar.isVisible()) {
            hide();
        } else {
            show();
        }
    }

    /**
     * 检查边栏是否可见
     */
    public boolean isVisible() {
        return rightSidebar.isVisible();
    }

    /**
     * 设置当前项目，构建目录树
     */
    public void setCurrentProject(ProjectInfo project) {
        this.currentProject = project;
        if (project != null) {
            Platform.runLater(() -> buildFileTree(project));
        } else {
            fileTree.setRoot(null);
        }
    }

    /**
     * 构建文件树
     */
    private void buildFileTree(ProjectInfo project) {
        try {
            Path projectPath = Paths.get(project.path());
            TreeItem<Path> root = fileTreeService.buildFileTree(projectPath);
            fileTree.setRoot(root);
            root.setExpanded(true);
        } catch (Exception e) {
            log.error("构建文件树失败: {}", project.path(), e);
        }
    }

    /**
     * 更新 diff 列表
     */
    public void updateDiffList(List<FileDiff> diffs) {
        Platform.runLater(() -> {
            diffList.getItems().clear();
            diffList.getItems().addAll(diffs);
        });
    }
}
