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
    private Button goalButton;
    @FXML
    private Button planButton;
    @FXML
    private MenuButton projectSelectButton;
    @FXML
    private Button branchDisplayButton;

    private final CodeHomePageViewModel viewModel;
    private final DiffService diffService;

    private boolean diffReviewExpanded = false;

    /**
     * Goal 目标待输入状态：点击 Goal 按钮后进入，直接在输入框输入目标描述并回车即设置，不弹窗。
     */
    private boolean goalInputPending = false;

    /** Goal 目标输入引导提示文案。 */
    private static final String GOAL_INPUT_PROMPT =
            "输入目标描述（结束状态 + 验证方式 + 限制条件），回车设置目标...";


    public CodeHomePageController(ToolUIBridge toolUIBridge,
                                  WindowManager windowManager,
                                  CodeHomePageViewModel viewModel,
                                  DiffService diffService) {
        super(toolUIBridge, windowManager);
        this.viewModel = viewModel;
        this.diffService = diffService;
    }

    /**
     * goal 按钮 toggle：进入目标输入模式（无论是否已有目标，输入新内容回车即覆盖）；再点退出。
     */
    private void handleGoalButton() {
        if (goalInputPending) {
            exitGoalInput();
            return;
        }
        // 互斥：进入目标待输入前先关闭计划模式（goal 与 plan 二选一）
        if (Boolean.TRUE.equals(this.viewModel.planModeProperty().get())) {
            this.viewModel.togglePlanMode();
        }
        // 不弹窗：进入目标待输入模式，在输入框中直接输入目标描述并回车设置
        goalInputPending = true;
        sendField.clear();
        updateSendFieldPrompt();
        updateModeButtonState();
        sendField.requestFocus();
        sendField.requestLayout();
    }

    /**
     * 统一计算输入框提示文案：目标待输入 > 计划模式 > 默认。
     */
    private void updateSendFieldPrompt() {
        if (goalInputPending) {
            sendField.setPromptText(GOAL_INPUT_PROMPT);
        } else if (Boolean.TRUE.equals(this.viewModel.planModeProperty().get())) {
            sendField.setPromptText("描述你的任务，呆芽将只读调研并制定计划...");
        } else {
            sendField.setPromptText("给呆芽发消息...");
        }
        sendField.requestLayout();
    }

    /**
     * 退出目标待输入状态并恢复提示。
     */
    private void exitGoalInput() {
        goalInputPending = false;
        updateSendFieldPrompt();
        updateModeButtonState();
    }

    /**
     * 发送拦截：目标待输入状态下，输入内容作为目标设置而非普通消息发送。
     */
    @Override
    protected void handleSendMessage() {
        if (goalInputPending) {
            String text = sendField.getText().trim();
            if (!text.isBlank()) {
                goalInputPending = false;
                sendField.clear();
                this.viewModel.setGoal(text);
            }
            return;
        }
        super.handleSendMessage();
    }

    /**
     * 更新 goal / plan 按钮状态：选中项目后启用，激活/开启时高亮（Apple 蓝选中态）。
     */
    private void updateModeButtonState() {
        boolean projectReady = this.viewModel.getCurrentProject() != null;
        this.goalButton.setDisable(!projectReady);
        this.planButton.setDisable(!projectReady);
        // goal 高亮：目标待输入中或目标已激活时均显示选中态
        boolean goalOn = this.viewModel.goalActiveProperty().get() || goalInputPending;
        boolean planOn = this.viewModel.planModeProperty().get();
        this.goalButton.getStyleClass().remove("home-page__mode-btn--active");
        if (goalOn) {
            this.goalButton.getStyleClass().add("home-page__mode-btn--active");
        }
        this.planButton.getStyleClass().remove("home-page__mode-btn--active");
        if (planOn) {
            this.planButton.getStyleClass().add("home-page__mode-btn--active");
        }
        // 未选择项目时同时锁死输入框（编码模式必须有工作目录才能发消息）
        refreshSendInputDisabled();
    }

    /**
     * Coder 模式未选择项目时锁定发送输入框，必须选择项目才能输入。
     */
    @Override
    protected boolean isSendInputLocked() {
        return this.viewModel.getCurrentProject() == null;
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

        // 计划批准卡片（Plan Mode）：同样显示在 approvalBar
        this.toolUIBridge.setOnShowPlanApproval(card -> {
            approvalBar.getChildren().clear();
            approvalBar.getChildren().add(card);
            approvalBar.setVisible(true);
            approvalBar.setManaged(true);
        });

        // approvalBar 内容为空时自动隐藏（卡片决策后 dismiss 移除自身）
        this.approvalBar.getChildren().addListener((javafx.collections.ListChangeListener<Node>) change -> {
            if (this.approvalBar.getChildren().isEmpty()) {
                this.approvalBar.setVisible(false);
                this.approvalBar.setManaged(false);
            }
        });

        // Goal / Plan 模式按钮（toggle）：点击开启，再点击关闭；选中态高亮
        this.goalButton.setOnAction(e -> handleGoalButton());
        this.planButton.setOnAction(e -> this.viewModel.togglePlanMode());
        // 互斥：进入计划模式时退出目标待输入状态；任何变化都刷新按钮态与输入提示
        this.viewModel.planModeProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) {
                exitGoalInput();
            } else {
                updateSendFieldPrompt();
            }
            updateModeButtonState();
        });
        this.viewModel.goalActiveProperty().addListener((obs, oldVal, newVal) -> {
            updateModeButtonState();
            updateSendFieldPrompt();
        });
        // 项目选中前禁用（FXML 初始 disable=true），选中项目后启用
        this.viewModel.currentProjectProperty().addListener((obs, oldVal, newVal) ->
                updateModeButtonState());
        updateModeButtonState();

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
        List<ButtonBarHolder.ButtonConfig> configs = new ArrayList<>();
        // 终端按钮（Coder 模式独有）
        configs.add(new ButtonBarHolder.ButtonConfig(
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
        configs.addAll(createCommonButtons());
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
