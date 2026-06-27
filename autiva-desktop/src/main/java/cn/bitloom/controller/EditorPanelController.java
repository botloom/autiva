package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.project.FileTreeService;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.node.diff.DiffListCell;
import cn.bitloom.node.editor.syntax.SyntaxHighlighter;
import cn.bitloom.node.editor.syntax.SyntaxHighlighterFactory;
import cn.bitloom.node.project.FileTreeCell;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.IntFunction;

/**
 * 编辑器面板控制器
 * 通过 StackPane 管理三个视图：终端、项目（文件树+文件内容）、变更（diff列表+diff视图）。
 */
@Slf4j
@Component
public class EditorPanelController implements Initializable {

    /** Diff 行信息，用于自定义行号渲染 */
    private record DiffLineInfo(boolean hunkHeader, int oldLine, int newLine, int hunkIndex) {}

    public enum ViewType { TERMINAL, PROJECT, CHANGES }

    @FXML
    @Getter
    private VBox editorPanel;
    @FXML
    private StackPane viewContainer;
    @FXML
    private VBox terminalView;
    @FXML
    private SplitPane projectSplit;
    @FXML
    private TreeView<Path> fileTree;
    @FXML
    private VBox fileContentPanel;
    @FXML
    private Label fileContentPlaceholder;
    @FXML
    private SplitPane changesSplit;
    @FXML
    private ListView<FileDiff> diffList;
    @FXML
    private VBox diffViewPanel;
    @FXML
    private Label diffPlaceholder;

    @Setter
    private IndexController indexController;

    private final FileTreeService fileTreeService;
    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalWidget;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;
    private ProjectInfo currentProject;
    private Disposable diffEventSubscription;

    /**
     * -- GETTER --
     *  获取当前视图类型（用于 toggle 判断）
     */
    @Getter
    private ViewType currentViewType = null;

    public EditorPanelController(FileTreeService fileTreeService,
                                 PtyTerminalService ptyTerminalService,
                                 DiffService diffService) {
        this.fileTreeService = fileTreeService;
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFileTree();
        setupDiffList();
        subscribeDiffEvents();
        setupRoundedClip();
    }

    /**
     * 给 viewContainer 设置圆角裁剪，确保终端/项目/变更三视图的方角都被裁剪到圆角形状
     */
    private void setupRoundedClip() {
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(viewContainer.widthProperty());
        clip.heightProperty().bind(viewContainer.heightProperty());
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        viewContainer.setClip(clip);
    }

    /**
     * 设置文件树
     */
    private void setupFileTree() {
        fileTree.setCellFactory(tree -> new FileTreeCell());
        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selected = fileTree.getSelectionModel().getSelectedItem();
                if (selected != null && Files.isRegularFile(selected.getValue())) {
                    showFileContent(selected.getValue());
                }
            }
        });
    }

    /**
     * 设置变更列表
     */
    private void setupDiffList() {
        diffList.setCellFactory(list -> new DiffListCell());
        diffList.setOnMouseClicked(event -> {
            FileDiff selected = diffList.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 1) {
                showDiffView(selected);
            }
        });
    }

    /**
     * 订阅 DiffEvent 自动刷新变更列表
     */
    private void subscribeDiffEvents() {
        this.diffEventSubscription = EventBus.outBoxFlux()
                .filter(event -> event instanceof DiffEvent)
                .subscribe(event -> refreshDiffList());
    }

    // ===== 视图切换 =====

    /**
     * 隐藏所有视图
     */
    private void hideAllViews() {
        terminalView.setVisible(false);
        terminalView.setManaged(false);
        projectSplit.setVisible(false);
        projectSplit.setManaged(false);
        changesSplit.setVisible(false);
        changesSplit.setManaged(false);
    }

    /**
     * 显示终端视图
     */
    public void showTerminalView() {
        hideAllViews();
        terminalView.setVisible(true);
        terminalView.setManaged(true);
        currentViewType = ViewType.TERMINAL;
    }

    /**
     * 显示项目视图
     */
    public void showProjectView() {
        hideAllViews();
        projectSplit.setVisible(true);
        projectSplit.setManaged(true);
        currentViewType = ViewType.PROJECT;
    }

    /**
     * 显示变更视图
     */
    public void showChangesView() {
        hideAllViews();
        changesSplit.setVisible(true);
        changesSplit.setManaged(true);
        refreshDiffList();
        currentViewType = ViewType.CHANGES;
    }

    // ===== 面板显示/隐藏 =====

    /**
     * 显示编辑器面板
     */
    public void show() {
        editorPanel.setVisible(true);
        editorPanel.setManaged(true);
    }

    /**
     * 隐藏编辑器面板
     */
    public void hide() {
        editorPanel.setVisible(false);
        editorPanel.setManaged(false);
    }

    /**
     * 检查面板是否可见
     */
    public boolean isVisible() {
        return editorPanel.isVisible();
    }

    // ===== 项目与文件树 =====

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
        } catch (Exception e) {
            log.error("构建文件树失败: {}", project.path(), e);
        }
    }

    /**
     * 显示文件内容（注入到项目视图右侧，支持编辑和 Ctrl+S 保存）
     */
    public void showFileContent(Path filePath) {
        show();
        showProjectView();

        try {
            String content = Files.readString(filePath);

            CodeArea codeArea = new CodeArea();
            codeArea.setEditable(true);
            codeArea.setShowCaret(Caret.CaretVisibility.ON);
            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            codeArea.replaceText(content);
            codeArea.getStyleClass().add("editor-panel__code-area");
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(filePath);
            highlighter.apply(codeArea, content);
            codeArea.moveTo(0);

            // Ctrl+S 保存文件
            codeArea.setOnKeyPressed(e -> {
                if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                    e.consume();
                    saveFileContent(filePath, codeArea.getText());
                }
            });

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");

            fileContentPanel.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            // 请求焦点以显示光标
            Platform.runLater(codeArea::requestFocus);

            // 右键菜单：选中文本后可"添加到对话框"
            setupCodeAreaContextMenu(codeArea);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
        }
    }

    /**
     * 保存文件内容到磁盘
     */
    private void saveFileContent(Path filePath, String content) {
        try {
            Files.writeString(filePath, content);
            log.info("文件已保存: {}", filePath);
        } catch (IOException e) {
            log.error("保存文件失败: {}", filePath, e);
        }
    }

    // ===== 终端 =====

    /**
     * 打开终端（注入到终端视图）
     */
    public void openTerminal(Path workingDir) {
        this.lastTerminalWorkingDir = workingDir;
        show();
        showTerminalView();
        ensureTerminalStarted(workingDir);
    }

    /**
     * 确保终端已启动，若未启动则异步创建
     */
    private void ensureTerminalStarted(Path workingDir) {
        if (terminalWidget != null) {
            Platform.runLater(() -> terminalWidget.requestFocus());
            return;
        }

        terminalView.getChildren().setAll(createLoadingContent("正在启动终端..."));

        new Thread(() -> {
            try {
                closeTerminalInternal();

                terminalSession = ptyTerminalService.createSession(workingDir);
                JediTerminalView newView = new JediTerminalView();
                newView.startSession(terminalSession);

                Platform.runLater(() -> {
                    terminalWidget = newView;
                    terminalView.getChildren().setAll(terminalWidget);
                    VBox.setVgrow(terminalWidget, Priority.ALWAYS);
                    // 右键菜单：选中文本后可"添加到对话框"
                    setupTerminalContextMenu(terminalWidget);
                    Platform.runLater(() -> terminalWidget.requestFocus());
                });
            } catch (IOException e) {
                log.error("创建终端会话失败", e);
                Platform.runLater(() -> terminalView.getChildren().setAll(
                        createErrorContent("终端启动失败: " + e.getMessage(),
                                () -> openTerminal(lastTerminalWorkingDir))));
            } catch (Exception e) {
                log.error("终端初始化异常", e);
                Platform.runLater(() -> terminalView.getChildren().setAll(
                        createErrorContent("终端初始化异常: " + e.getMessage(),
                                () -> openTerminal(lastTerminalWorkingDir))));
            }
        }).start();
    }

    /**
     * 关闭终端会话
     */
    public void closeTerminal() {
        closeTerminalInternal();
        if (terminalView != null) {
            terminalView.getChildren().setAll(createLoadingContent("终端已关闭"));
        }
    }

    /**
     * 内部关闭终端方法
     */
    private void closeTerminalInternal() {
        if (terminalWidget != null) {
            terminalWidget.closeSession();
            terminalWidget = null;
        }
        if (terminalSession != null) {
            ptyTerminalService.closeSession(terminalSession.getSessionId());
            terminalSession = null;
        }
    }

    // ===== Diff 视图 =====

    /**
     * 显示 diff 视图（注入到变更视图右侧）
     * - 隐藏 @@ hunk 头和 +/- 前缀，纯色覆盖
     * - 顶部无标题栏，改为 StackPane 叠加的悬浮横幅（撤销/保留 + 上/下变更导航）
     * - 每个变更块（hunk）悬浮时右上角显示局部撤销/保留按钮
     */
    public void showDiffView(FileDiff diff) {
        show();
        // 已在变更视图时不调用 showChangesView，避免 refreshDiffList 清空列表导致选中闪烁
        if (currentViewType != ViewType.CHANGES) {
            showChangesView();
        }

        // 用 JGit 重新计算 diff，因为可能打开的是历史对话，文件已被进一步修改
        FileDiff freshDiff = diffService.recomputeDiff(diff);

        StyleClassedTextArea diffArea = new StyleClassedTextArea();
        diffArea.setEditable(false);
        diffArea.setShowCaret(Caret.CaretVisibility.ON);
        diffArea.getStyleClass().add("editor-panel__diff-area");

        List<DiffLineInfo> lineInfos = new ArrayList<>();
        // 记录每个 hunk 的段落索引范围（含 hunkHeader 行及其后所有 diff 行）
        List<int[]> hunkRanges = new ArrayList<>();
        int paragraph = 0;

        if (freshDiff.hunks() != null) {
            int hunkIndex = 0;
            for (FileDiff.Hunk hunk : freshDiff.hunks()) {
                int hunkStartParagraph = paragraph;
                int currentOldLine = hunk.oldStart();
                int currentNewLine = hunk.newStart();

                // Hunk 头占位（用 0 宽度内容占位，由 CSS 隐藏背景与文字）
                diffArea.appendText("\n");
                diffArea.setParagraphStyle(paragraph, List.of("diff-hunk-header", "diff-hunk-header--hidden"));
                lineInfos.add(new DiffLineInfo(true, 0, 0, hunkIndex));
                paragraph++;

                if (hunk.lines() != null) {
                    for (FileDiff.DiffLine line : hunk.lines()) {
                        // 直接追加内容，不带 +/- 前缀
                        diffArea.appendText(line.content() + "\n");

                        // 段落级背景色（纯色覆盖）
                        String styleCls = switch (line.type()) {
                            case ADD -> "diff-line-add";
                            case REMOVE -> "diff-line-remove";
                            case CONTEXT -> "diff-line-context";
                        };
                        diffArea.setParagraphStyle(paragraph, List.of(styleCls));

                        // 记录行号信息
                        int oldLine = -1, newLine = -1;
                        switch (line.type()) {
                            case ADD -> newLine = currentNewLine++;
                            case REMOVE -> oldLine = currentOldLine++;
                            case CONTEXT -> { oldLine = currentOldLine++; newLine = currentNewLine++; }
                        }
                        lineInfos.add(new DiffLineInfo(false, oldLine, newLine, hunkIndex));
                        paragraph++;
                    }
                }
                hunkRanges.add(new int[]{hunkStartParagraph, paragraph - 1, hunkIndex});
                hunkIndex++;
            }
        }
        diffArea.moveTo(0);
        diffArea.setParagraphGraphicFactory(createDiffLineNumberFactory(lineInfos));

        VirtualizedScrollPane<StyleClassedTextArea> scrollPane = new VirtualizedScrollPane<>(diffArea);
        scrollPane.getStyleClass().add("editor-panel__code-scroll");

        // 悬浮横幅（文件名 + 上一个/下一个 + 撤销全部/保留全部）
        HBox banner = createDiffBanner(freshDiff, diffArea, hunkRanges);
        banner.setMaxWidth(Region.USE_PREF_SIZE);
        banner.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane stack = new StackPane(scrollPane, banner);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new javafx.geometry.Insets(8, 12, 0, 12));
        VBox.setVgrow(stack, Priority.ALWAYS);

        diffViewPanel.getChildren().setAll(stack);
        VBox.setVgrow(stack, Priority.ALWAYS);

        // 右键菜单：选中文本后可"添加到对话框"
        setupDiffAreaContextMenu(diffArea);
    }

    /**
     * 创建 diff 悬浮横幅：左侧文件名，右侧 上一个/下一个/撤销/保留 按钮。
     */
    private HBox createDiffBanner(FileDiff diff, StyleClassedTextArea diffArea, List<int[]> hunkRanges) {
        String filePath = diff.filePath();
        String fileName = Paths.get(filePath).getFileName().toString();

        Label pathLabel = new Label(fileName);
        pathLabel.getStyleClass().add("diff-banner__path");
        pathLabel.setTooltip(new Tooltip(filePath));

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button prevBtn = new Button("↑");
        prevBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--nav");
        prevBtn.setTooltip(new Tooltip("上一个变更"));
        Button nextBtn = new Button("↓");
        nextBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--nav");
        nextBtn.setTooltip(new Tooltip("下一个变更"));

        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--reject");
        Button approveBtn = new Button("保留");
        approveBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--approve");

        // 导航：滚动到对应 hunk 的起始段落
        prevBtn.setOnAction(e -> navigateHunk(diffArea, hunkRanges, -1));
        nextBtn.setOnAction(e -> navigateHunk(diffArea, hunkRanges, 1));

        rejectBtn.setOnAction(e -> {
            diffService.rejectFileDiff(diff);
            resetDiffViewPlaceholder();
            refreshDiffList();
        });
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            resetDiffViewPlaceholder();
            refreshDiffList();
        });

        HBox banner = new HBox(pathLabel, spring, prevBtn, nextBtn, rejectBtn, approveBtn);
        banner.getStyleClass().add("diff-banner");
        return banner;
    }

    /** 当前导航到的 hunk 索引，-1 表示未定位 */
    private int currentNavHunkIndex = -1;

    /**
     * 导航到上一个/下一个变更块（hunk）。
     * @param direction -1 上一个，1 下一个
     */
    private void navigateHunk(StyleClassedTextArea diffArea, List<int[]> hunkRanges, int direction) {
        if (hunkRanges.isEmpty()) return;
        int next;
        if (currentNavHunkIndex < 0) {
            next = direction > 0 ? 0 : hunkRanges.size() - 1;
        } else {
            next = currentNavHunkIndex + direction;
            if (next < 0) next = hunkRanges.size() - 1;
            if (next >= hunkRanges.size()) next = 0;
        }
        currentNavHunkIndex = next;
        int[] range = hunkRanges.get(next);
        int targetParagraph = range[0] + 1; // 跳过隐藏的 hunk 头行
        diffArea.moveTo(targetParagraph, 0);
        diffArea.requestFollowCaret();
        diffArea.showParagraphAtTop(targetParagraph);
    }

    /**
     * 创建自定义 diff 行号工厂，显示 old/new 双列行号和 gutter 指示器。
     * Hunk 头行返回空节点（视觉上隐藏）。
     */
    private IntFunction<Node> createDiffLineNumberFactory(List<DiffLineInfo> lineInfos) {
        final double colWidth = 38;
        final double gutterWidth = 3;

        return idx -> {
            HBox box = new HBox();
            box.getStyleClass().add("diff-lineno-box");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinWidth(gutterWidth + colWidth * 2 + 8);
            box.setPrefWidth(gutterWidth + colWidth * 2 + 8);

            if (idx < 0 || idx >= lineInfos.size()) {
                return box;
            }

            DiffLineInfo info = lineInfos.get(idx);

            if (info.hunkHeader()) {
                // Hunk 头行号区域返回空（整行由 CSS 隐藏）
                box.setPrefHeight(0);
                box.setMinHeight(0);
                box.setMaxHeight(0);
                return box;
            }

            boolean isAdd = info.oldLine() < 0;
            boolean isRemove = info.newLine() < 0;

            // Gutter 指示条
            Region gutter = new Region();
            gutter.getStyleClass().add("diff-gutter-indicator");
            gutter.setPrefWidth(gutterWidth);
            gutter.setMinWidth(gutterWidth);
            gutter.setMaxWidth(gutterWidth);
            if (isAdd) {
                gutter.getStyleClass().add("diff-gutter-indicator--add");
            } else if (isRemove) {
                gutter.getStyleClass().add("diff-gutter-indicator--remove");
            }

            // Old 行号
            Label oldLabel = new Label(info.oldLine() > 0 ? String.valueOf(info.oldLine()) : "");
            oldLabel.getStyleClass().add("diff-lineno");
            oldLabel.setAlignment(Pos.CENTER_RIGHT);
            oldLabel.setPrefWidth(colWidth);
            oldLabel.setMinWidth(colWidth);
            if (isRemove) {
                oldLabel.getStyleClass().add("diff-lineno--remove");
            }

            // New 行号
            Label newLabel = new Label(info.newLine() > 0 ? String.valueOf(info.newLine()) : "");
            newLabel.getStyleClass().add("diff-lineno");
            newLabel.setAlignment(Pos.CENTER_RIGHT);
            newLabel.setPrefWidth(colWidth);
            newLabel.setMinWidth(colWidth);
            if (isAdd) {
                newLabel.getStyleClass().add("diff-lineno--add");
            }

            box.getChildren().addAll(gutter, oldLabel, newLabel);
            return box;
        };
    }

    /**
     * 统计 diff 的 ADD/REMOVE 行数
     */
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

    /**
     * 重置 diff 视图为占位符
     */
    private void resetDiffViewPlaceholder() {
        diffViewPanel.getChildren().setAll(diffPlaceholder);
        VBox.setVgrow(diffPlaceholder, Priority.ALWAYS);
    }

    /**
     * 刷新变更列表 - 用 JGit 扫描工作区未提交变更（异步）
     */
    private void refreshDiffList() {
        if (currentProject == null) {
            diffList.getItems().clear();
            return;
        }
        new Thread(() -> {
            List<FileDiff> diffs = diffService.scanWorkingTreeDiffs(Paths.get(currentProject.path()));
            Platform.runLater(() -> {
                diffList.getItems().clear();
                diffList.getItems().addAll(diffs);
            });
        }).start();
    }

    /**
     * 更新变更列表
     */
    public void updateDiffList(List<FileDiff> diffs) {
        Platform.runLater(() -> {
            diffList.getItems().clear();
            diffList.getItems().addAll(diffs);
        });
    }

    // ===== 加载与错误状态 =====

    /**
     * 创建加载状态内容
     */
    private VBox createLoadingContent(String message) {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(32, 32);
        Label label = new Label(message);
        label.getStyleClass().add("editor-panel__loading-text");
        VBox box = new VBox(indicator, label);
        box.setAlignment(Pos.CENTER);
        box.setSpacing(8);
        return box;
    }

    /**
     * 创建错误状态内容（带重试按钮）
     */
    private VBox createErrorContent(String message, Runnable retryAction) {
        Label errorLabel = new Label(message);
        errorLabel.getStyleClass().add("editor-panel__error-text");
        errorLabel.setWrapText(true);
        VBox box = new VBox(errorLabel);
        if (retryAction != null) {
            Button retryBtn = new Button("重试");
            retryBtn.getStyleClass().add("editor-panel__retry-btn");
            retryBtn.setOnAction(e -> retryAction.run());
            box.getChildren().add(retryBtn);
        }
        box.setAlignment(Pos.CENTER);
        box.setSpacing(8);
        return box;
    }

    // ===== 右键菜单 → 对话框联动 =====

    /**
     * 为终端设置右键菜单：选中文本后右键可"添加到对话框"。
     * JediTerminalView 不是 Control，无法使用 setContextMenu，
     * 改用 setOnContextMenuRequested + ContextMenu.show() 实现。
     */
    private void setupTerminalContextMenu(JediTerminalView view) {
        ContextMenu menu = new ContextMenu();
        MenuItem addToChatItem = new MenuItem("添加到对话框");
        addToChatItem.setOnAction(e -> {
            String selected = view.getSelectedText();
            if (selected != null && !selected.isBlank() && indexController != null) {
                indexController.addTextToChat(selected);
            }
        });
        menu.getItems().add(addToChatItem);
        view.setOnContextMenuRequested(e -> {
            menu.show(view, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /**
     * 为文件内容 CodeArea 设置右键菜单：选中文本后右键可"添加到对话框"。
     */
    private void setupCodeAreaContextMenu(CodeArea codeArea) {
        ContextMenu menu = new ContextMenu();
        MenuItem addToChatItem = new MenuItem("添加到对话框");
        addToChatItem.setOnAction(e -> {
            String selected = codeArea.getSelectedText();
            if (selected != null && !selected.isBlank() && indexController != null) {
                indexController.addTextToChat(selected);
            }
        });
        menu.getItems().add(addToChatItem);
        codeArea.setContextMenu(menu);
    }

    /**
     * 为 diff StyleClassedTextArea 设置右键菜单：选中文本后右键可"添加到对话框"。
     */
    private void setupDiffAreaContextMenu(StyleClassedTextArea diffArea) {
        ContextMenu menu = new ContextMenu();
        MenuItem addToChatItem = new MenuItem("添加到对话框");
        addToChatItem.setOnAction(e -> {
            String selected = diffArea.getSelectedText();
            if (selected != null && !selected.isBlank() && indexController != null) {
                indexController.addTextToChat(selected);
            }
        });
        menu.getItems().add(addToChatItem);
        diffArea.setContextMenu(menu);
    }
}
