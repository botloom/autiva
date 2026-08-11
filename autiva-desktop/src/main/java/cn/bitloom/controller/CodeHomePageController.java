package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.DiffService;
import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.store.Store;
import cn.bitloom.vm.AbstractHomePageViewModel;
import cn.bitloom.vm.CodeHomePageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Coder 模式首页控制器。
 * <p>
 * 在通用对话逻辑基础上增加：
 * - Diff 审查条（文件卡片列表 + 全部撤销/全部保留）
 * - 项目选择菜单 + 分支显示按钮
 * - 终端按钮配置
 */
@Slf4j
@Component
public class CodeHomePageController extends AbstractHomePageController {

    @FXML
    private VBox diffReviewBar;
    @FXML
    private HBox diffReviewHeader;
    @FXML
    private Label diffReviewCount;
    @FXML
    private Button diffReviewRejectAllBtn;
    @FXML
    private Button diffReviewApproveAllBtn;
    @FXML
    private ScrollPane diffReviewListScroll;
    @FXML
    private VBox diffReviewList;
    @FXML
    private VBox approvalBar;
    @FXML
    private MenuButton projectSelectButton;
    @FXML
    private Button branchDisplayButton;

    private final CodeHomePageViewModel viewModel;
    private final DiffService diffService;

    private boolean diffReviewExpanded = false;

    public CodeHomePageController(ToolUIBridge toolUIBridge,
                                  WindowManager windowManager,
                                  CodeHomePageViewModel viewModel,
                                  DiffService diffService) {
        super(toolUIBridge, windowManager);
        this.viewModel = viewModel;
        this.diffService = diffService;
    }

    @Override
    public AbstractHomePageViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);

        // 智能体切换联动：控制项目/分支按钮可见性
        updateProjectButtonBarVisibility(Store.currentAgent.get());
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> updateProjectButtonBarVisibility(newVal));
        });

        this.setupProjectMenu();
        this.branchDisplayButton.setText("");
        this.viewModel.currentProjectProperty()
                .addListener((obs, oldVal, newVal) -> {
                    refreshBranchDisplay(newVal);
                    refreshProjectMenuText(newVal);
                });

        // 向 viewModel 注入 diffHandler，DiffEvent 通过 agent 事件流传入
        this.viewModel.setDiffHandler(diffEvent -> Platform.runLater(() -> addDiffFileCard(diffEvent.getDiff())));

        // 批准框：显示在输入框上方的 approvalBar（不持久化到聊天历史）
        this.toolUIBridge.setOnShowApproval(card -> {
            approvalBar.getChildren().clear();
            approvalBar.getChildren().add(card);
            approvalBar.setVisible(true);
            approvalBar.setManaged(true);
        });

        // diff 审查条按钮事件
        this.diffReviewHeader.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button || e.getTarget() instanceof Label parentLabel
                    && parentLabel.getParent() instanceof Button) return;
            toggleDiffReviewExpand();
        });
        this.diffReviewHeader.setStyle("-fx-cursor: hand;");
        this.diffReviewRejectAllBtn.setOnAction(e -> handleRejectAllDiff());
        this.diffReviewApproveAllBtn.setOnAction(e -> handleApproveAllDiff());

        // 监听会话切换：清空 diff 文件卡片条和批准框
        Store.currentSessionId.addListener((obs, oldVal, newVal) -> {
            if (oldVal != null && !oldVal.equals(newVal)) {
                diffReviewList.getChildren().clear();
                diffReviewExpanded = false;
                diffReviewListScroll.setVisible(false);
                diffReviewListScroll.setManaged(false);
                updateDiffReviewBar();
                approvalBar.getChildren().clear();
                approvalBar.setVisible(false);
                approvalBar.setManaged(false);
            }
        });
    }

    @Override
    protected void onMessagesChanged(boolean hasMessages) {
        updateSelectorLockState(hasMessages);
    }

    // ===== Diff 审查条 =====

    /**
     * 从 DiffService 刷新 diff 审查条（diff 看板中撤销/保留后调用）
     * 重建卡片列表，仅保留 diffService 中仍 pending 的 diff
     */
    @Override
    public void refreshDiffReviewBarFromService() {
        java.util.List<FileDiff> pending = diffService.getPendingDiffs();
        java.util.Set<String> pendingIds = pending.stream()
                .map(FileDiff::id)
                .collect(java.util.stream.Collectors.toSet());

        // 移除已不在 pending 中的卡片
        diffReviewList.getChildren().removeIf(node ->
                !(node.getUserData() instanceof FileDiff diff) || !pendingIds.contains(diff.id()));

        // 添加新增的卡片（pending 中有但卡片列表中没有的）
        java.util.Set<String> existingIds = diffReviewList.getChildren().stream()
                .filter(node -> node.getUserData() instanceof FileDiff)
                .map(node -> ((FileDiff) node.getUserData()).id())
                .collect(java.util.stream.Collectors.toSet());

        for (FileDiff diff : pending) {
            if (!existingIds.contains(diff.id())) {
                diffReviewList.getChildren().add(createDiffFileCard(diff));
            }
        }

        updateDiffReviewBar();
    }

    private void addDiffFileCard(FileDiff diff) {
        diffReviewList.getChildren().add(createDiffFileCard(diff));
        updateDiffReviewBar();
    }

    private void updateDiffReviewBar() {
        int count = diffReviewList.getChildren().size();
        diffReviewCount.setText(count + " 个文件待审查");
        boolean hasDiffs = count > 0;
        diffReviewBar.setVisible(hasDiffs);
        diffReviewBar.setManaged(hasDiffs);
    }

    @FXML
    private void toggleDiffReviewExpand() {
        diffReviewExpanded = !diffReviewExpanded;
        diffReviewListScroll.setVisible(diffReviewExpanded);
        diffReviewListScroll.setManaged(diffReviewExpanded);
    }

    @FXML
    private void handleRejectAllDiff() {
        for (var node : diffReviewList.getChildren()) {
            if (node.getUserData() instanceof FileDiff diff) {
                diffService.rejectFileDiff(diff);
            }
        }
        diffReviewList.getChildren().clear();
        diffReviewExpanded = false;
        diffReviewListScroll.setVisible(false);
        diffReviewListScroll.setManaged(false);
        updateDiffReviewBar();
    }

    @FXML
    private void handleApproveAllDiff() {
        for (var node : diffReviewList.getChildren()) {
            if (node.getUserData() instanceof FileDiff diff) {
                diffService.approveFileDiff(diff);
            }
        }
        diffReviewList.getChildren().clear();
        diffReviewExpanded = false;
        diffReviewListScroll.setVisible(false);
        diffReviewListScroll.setManaged(false);
        updateDiffReviewBar();
    }

    private Node createDiffFileCard(FileDiff diff) {
        HBox card = new HBox(6);
        card.getStyleClass().add("home-page__diff-review-file-row");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setUserData(diff);

        String filePath = diff.filePath();
        String fileName = filePath;
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSep >= 0 && lastSep < filePath.length() - 1) {
            fileName = filePath.substring(lastSep + 1);
        }
        Label nameLabel = new Label(fileName);
        nameLabel.getStyleClass().add("home-page__diff-review-file-name");
        nameLabel.setTooltip(new Tooltip(filePath));
        card.getChildren().add(nameLabel);

        int[] stats = computeDiffStats(diff);
        Label statsLabel = new Label("+" + stats[0] + " -" + stats[1]);
        statsLabel.getStyleClass().add("home-page__diff-review-file-stats");
        if (stats[0] > 0) {
            statsLabel.getStyleClass().add("home-page__diff-review-file-stats--add");
        }
        if (stats[1] > 0) {
            statsLabel.getStyleClass().add("home-page__diff-review-file-stats--remove");
        }
        card.getChildren().add(statsLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        card.getChildren().add(spacer);

        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().addAll("home-page__diff-review-file-btn", "home-page__diff-review-file-btn--reject");
        rejectBtn.setOnAction(e -> {
            diffService.rejectFileDiff(diff);
            diffReviewList.getChildren().remove(card);
            updateDiffReviewBar();
        });
        card.getChildren().add(rejectBtn);

        Button approveBtn = new Button("保留");
        approveBtn.getStyleClass().addAll("home-page__diff-review-file-btn", "home-page__diff-review-file-btn--approve");
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            diffReviewList.getChildren().remove(card);
            updateDiffReviewBar();
        });
        card.getChildren().add(approveBtn);

        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button) return;
            if (indexController != null) {
                indexController.showDiffInProjectView(diff);
            }
        });

        return card;
    }

    private static int[] computeDiffStats(FileDiff diff) {
        int added = 0, removed = 0;
        if (diff.hunks() == null) return new int[]{0, 0};
        for (FileDiff.Hunk hunk : diff.hunks()) {
            if (hunk.lines() == null) continue;
            for (FileDiff.DiffLine line : hunk.lines()) {
                if (line.type() == FileDiff.Type.ADD) added++;
                else if (line.type() == FileDiff.Type.REMOVE) removed++;
            }
        }
        return new int[]{added, removed};
    }

    // ===== 项目菜单与分支显示 =====

    private void updateProjectButtonBarVisibility(String agentId) {
        boolean show = AgentMode.CODE.matches(agentId);
        projectSelectButton.setVisible(show);
        projectSelectButton.setManaged(show);
        branchDisplayButton.setVisible(show);
        branchDisplayButton.setManaged(show);
    }

    private void updateSelectorLockState(boolean locked) {
        projectSelectButton.setDisable(locked);
    }

    private void setupProjectMenu() {
        refreshProjectMenu();
        refreshProjectMenuText(viewModel.getCurrentProject());
    }

    private void refreshProjectMenu() {
        projectSelectButton.getItems().clear();

        MenuItem openFolderItem = new MenuItem("选择文件夹...");
        openFolderItem.setOnAction(e -> handleOpenLocalFolder());
        projectSelectButton.getItems().add(openFolderItem);

        projectSelectButton.getItems().add(new SeparatorMenuItem());

        List<ProjectInfo> projects = viewModel.listProjects();
        for (ProjectInfo project : projects) {
            MenuItem item = new MenuItem(project.name());
            item.setOnAction(e -> {
                viewModel.setCurrentProject(project);
                refreshBranchDisplay(project);
            });
            projectSelectButton.getItems().add(item);
        }
    }

    private void refreshProjectMenuText(ProjectInfo project) {
        if (project != null) {
            projectSelectButton.setText(project.name());
        } else {
            projectSelectButton.setText("选择项目");
        }
    }

    private void handleOpenLocalFolder() {
        try {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle("选择项目文件夹");
            javafx.stage.Stage stage = (javafx.stage.Stage) projectSelectButton.getScene().getWindow();
            File selectedDir = dirChooser.showDialog(stage);
            if (selectedDir != null) {
                String path = selectedDir.getAbsolutePath();
                String name = selectedDir.getName();
                viewModel.registerLocalProject(path, name);
                refreshProjectMenu();
            }
        } catch (Exception e) {
            log.error("打开文件夹选择器失败", e);
        }
    }

    private void refreshBranchDisplay(ProjectInfo project) {
        if (project == null || project.gitBranch() == null || project.gitBranch().isBlank()) {
            branchDisplayButton.setText("");
        } else {
            branchDisplayButton.setText(project.gitBranch());
        }
    }

    // ===== 按钮配置 =====

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        List<ButtonBarHolder.ButtonConfig> configs = new ArrayList<>(createCommonButtons());
        // 插入 coder 专有的终端按钮（tool 按钮之前）
        configs.add(0, new ButtonBarHolder.ButtonConfig(
                "terminalButton",
                "终端",
                "button-bar__icon-btn",
                "/cn/bitloom/images/terminal.svg",
                ButtonBarHolder.Alignment.RIGHT,
                _ -> {
                    if (indexController != null) {
                        indexController.toggleTerminalPanel();
                    }
                }));
        return configs;
    }

    @Override
    protected void onResetForNewSession() {
        // 清空 diff 审查卡片条
        this.diffReviewList.getChildren().clear();
        this.diffReviewExpanded = false;
        this.diffReviewListScroll.setVisible(false);
        this.diffReviewListScroll.setManaged(false);
        this.updateDiffReviewBar();
    }

    /**
     * 取消事件订阅（模式切换时调用）
     */
    @Override
    public void dispose() {
        super.dispose();
    }
}
