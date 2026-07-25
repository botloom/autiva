package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.project.FileTreeService;
import cn.bitloom.agentic.project.ProjectInfo;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 编辑器面板控制器
 * 通过 StackPane 管理四个视图：终端、项目（文件树+文件内容）、工具调用、待办。
 * Diff 不再独立成视图，而是注入到项目视图的右侧内容区。
 */
@Slf4j
@Component
public class EditorPanelController implements Initializable {

    public enum ViewType { TERMINAL, PROJECT, TOOL_CALLS, TODO }

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
    private VBox toolCallsView;
    @FXML
    private VBox toolCallsContainer;
    @FXML
    private VBox todoView;
    @FXML
    private VBox todoContainer;

    @Setter
    private IndexController indexController;

    private final FileTreeService fileTreeService;
    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalWidget;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;
    private ProjectInfo currentProject;

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
        setupRoundedClip();
    }

    /**
     * 给 viewContainer 设置圆角裁剪，确保终端/项目/工具/待办视图的方角都被裁剪到圆角形状
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

    // ===== 视图切换 =====

    /**
     * 隐藏所有视图
     */
    private void hideAllViews() {
        terminalView.setVisible(false);
        terminalView.setManaged(false);
        projectSplit.setVisible(false);
        projectSplit.setManaged(false);
        toolCallsView.setVisible(false);
        toolCallsView.setManaged(false);
        todoView.setVisible(false);
        todoView.setManaged(false);
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
     * 显示工具调用视图
     */
    public void showToolCallsView() {
        hideAllViews();
        toolCallsView.setVisible(true);
        toolCallsView.setManaged(true);
        currentViewType = ViewType.TOOL_CALLS;
    }

    /**
     * 显示待办视图
     */
    public void showTodoView() {
        hideAllViews();
        todoView.setVisible(true);
        todoView.setManaged(true);
        currentViewType = ViewType.TODO;
    }

    /**
     * 添加工具调用卡片到工具调用视图
     */
    public void addToolCallCard(javafx.scene.Node card) {
        if (card instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        toolCallsContainer.getChildren().add(card);
    }

    /**
     * 添加待办卡片到待办视图
     */
    public void addTodoCard(javafx.scene.Node card) {
        if (card instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        todoContainer.getChildren().add(card);
    }

    /**
     * 清空工具调用卡片
     */
    public void clearToolCalls() {
        toolCallsContainer.getChildren().clear();
    }

    /**
     * 清空待办卡片
     */
    public void clearTodos() {
        todoContainer.getChildren().clear();
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

    // ===== Diff 视图（注入到项目视图右侧内容区） =====

    /**
     * 在项目视图中显示指定文件的 diff（切换到项目视图，右侧内容区渲染 diff）
     */
    public void showDiffInProjectView(FileDiff diff) {
        show();
        showProjectView();
        renderDiffIntoPanel(diff, fileContentPanel);
    }

    /**
     * 渲染 diff 到指定面板（左右双栏对比视图，类似 IDEA）
     * - 左侧 CodeArea 显示旧版本内容，REMOVE 行红色背景
     * - 右侧 CodeArea 显示新版本内容，ADD 行绿色背景
     * - 两侧段落按 hunk 对齐（ADD 行在左侧留空行，REMOVE 行在右侧留空行）
     * - 双列行号：左侧旧版本行号，右侧新版本行号
     * - 两侧分别应用语法高亮，同步滚动
     * 直接使用 DiffEvent 中的原始 FileDiff，不重新计算（diff 显示工具调用时的快照）
     */
    private void renderDiffIntoPanel(FileDiff diff, VBox targetPanel) {
        FileDiff freshDiff = diff;

        // 构建左右两侧的内容、行号、段落样式
        StringBuilder leftText = new StringBuilder();
        StringBuilder rightText = new StringBuilder();
        List<Integer> leftLineNumbers = new ArrayList<>();
        List<Integer> rightLineNumbers = new ArrayList<>();
        List<String> leftParagraphStyles = new ArrayList<>();
        List<String> rightParagraphStyles = new ArrayList<>();

        if (freshDiff.hunks() != null) {
            for (FileDiff.Hunk hunk : freshDiff.hunks()) {
                int currentOldLine = hunk.oldStart() - 1; // oldStart 是 1-based
                int currentNewLine = hunk.newStart() - 1; // newStart 是 1-based
                if (hunk.lines() != null) {
                    for (FileDiff.DiffLine line : hunk.lines()) {
                        switch (line.type()) {
                            case REMOVE -> {
                                currentOldLine++;
                                leftText.append(line.content()).append("\n");
                                leftLineNumbers.add(currentOldLine);
                                leftParagraphStyles.add("diff-line-remove-left");
                                rightText.append("\n");
                                rightLineNumbers.add(0);
                                rightParagraphStyles.add("diff-line-empty");
                            }
                            case ADD -> {
                                currentNewLine++;
                                leftText.append("\n");
                                leftLineNumbers.add(0);
                                leftParagraphStyles.add("diff-line-empty");
                                rightText.append(line.content()).append("\n");
                                rightLineNumbers.add(currentNewLine);
                                rightParagraphStyles.add("diff-line-add-right");
                            }
                            case CONTEXT -> {
                                currentOldLine++;
                                currentNewLine++;
                                leftText.append(line.content()).append("\n");
                                leftLineNumbers.add(currentOldLine);
                                leftParagraphStyles.add(null);
                                rightText.append(line.content()).append("\n");
                                rightLineNumbers.add(currentNewLine);
                                rightParagraphStyles.add(null);
                            }
                        }
                    }
                }
            }
        }

        CodeArea leftArea = buildDiffCodeArea(leftText.toString(), leftLineNumbers, leftParagraphStyles, freshDiff.filePath());
        CodeArea rightArea = buildDiffCodeArea(rightText.toString(), rightLineNumbers, rightParagraphStyles, freshDiff.filePath());

        // 同步滚动：用标志位防止循环触发
        final boolean[] syncing = {false};
        leftArea.estimatedScrollYProperty().addListener((obs, old, val) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            rightArea.estimatedScrollYProperty().setValue(val.doubleValue());
            syncing[0] = false;
        });
        rightArea.estimatedScrollYProperty().addListener((obs, old, val) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            leftArea.estimatedScrollYProperty().setValue(val.doubleValue());
            syncing[0] = false;
        });

        VirtualizedScrollPane<CodeArea> leftScroll = new VirtualizedScrollPane<>(leftArea);
        leftScroll.getStyleClass().add("editor-panel__code-scroll");
        VirtualizedScrollPane<CodeArea> rightScroll = new VirtualizedScrollPane<>(rightArea);
        rightScroll.getStyleClass().add("editor-panel__code-scroll");

        VBox leftBox = new VBox(leftScroll);
        leftBox.getStyleClass().add("editor-panel__diff-left");
        VBox.setVgrow(leftScroll, Priority.ALWAYS);
        VBox rightBox = new VBox(rightScroll);
        rightBox.getStyleClass().add("editor-panel__diff-right");
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftBox, rightBox);
        splitPane.getStyleClass().add("editor-panel__diff-split");
        splitPane.setDividerPositions(0.5);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // 悬浮横幅（文件名 + 撤销/保留）叠加在右侧 CodeArea 上
        HBox banner = createDiffBanner(freshDiff);
        banner.setMaxWidth(Region.USE_PREF_SIZE);
        banner.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane stack = new StackPane(splitPane, banner);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new javafx.geometry.Insets(8, 12, 0, 12));
        VBox.setVgrow(stack, Priority.ALWAYS);

        targetPanel.getChildren().setAll(stack);
        VBox.setVgrow(stack, Priority.ALWAYS);

        // 右键菜单复用 CodeArea 版本
        setupCodeAreaContextMenu(leftArea);
        setupCodeAreaContextMenu(rightArea);
    }

    /**
     * 构建 diff 用的 CodeArea：应用行号工厂、段落样式、语法高亮
     */
    private CodeArea buildDiffCodeArea(String text, List<Integer> lineNumbers, List<String> paragraphStyles, String filePath) {
        CodeArea codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setShowCaret(Caret.CaretVisibility.ON);
        codeArea.getStyleClass().add("editor-panel__diff-area");

        // 一次性设置所有文本，避免逐行追加导致的段落索引对齐问题
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        codeArea.replaceText(text);

        // 设置段落样式（背景高亮）
        int paragraphCount = codeArea.getParagraphs().size();
        for (int i = 0; i < paragraphCount && i < paragraphStyles.size(); i++) {
            String style = paragraphStyles.get(i);
            if (style != null) {
                codeArea.setParagraphStyle(i, List.of(style));
            }
        }

        codeArea.moveTo(0);

        // 行号工厂
        codeArea.setParagraphGraphicFactory(idx -> {
            Label label = new Label();
            if (idx >= 0 && idx < lineNumbers.size()) {
                int lineNo = lineNumbers.get(idx);
                if (lineNo > 0) {
                    label.setText(String.valueOf(lineNo));
                }
            }
            label.getStyleClass().addAll("diff-lineno", "diff-lineno--single");
            label.setAlignment(Pos.CENTER_RIGHT);
            label.setPrefWidth(38);
            label.setMinWidth(38);
            return label;
        });

        // 语法高亮
        try {
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(Paths.get(filePath));
            highlighter.apply(codeArea, codeArea.getText());
        } catch (Exception e) {
            log.warn("diff 语法高亮失败: {}", filePath, e);
        }

        return codeArea;
    }

    /**
     * 创建 diff 悬浮横幅：左侧文件名，右侧 撤销/保留 按钮。
     */
    private HBox createDiffBanner(FileDiff diff) {
        String filePath = diff.filePath();
        String fileName = Paths.get(filePath).getFileName().toString();

        Label pathLabel = new Label(fileName);
        pathLabel.getStyleClass().add("diff-banner__path");
        pathLabel.setTooltip(new Tooltip(filePath));

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--reject");
        Button approveBtn = new Button("保留");
        approveBtn.getStyleClass().addAll("diff-banner__btn", "diff-banner__btn--approve");

        rejectBtn.setOnAction(e -> {
            diffService.rejectFileDiff(diff);
            // 撤销后恢复项目视图占位符
            fileContentPanel.getChildren().setAll(fileContentPlaceholder);
            VBox.setVgrow(fileContentPlaceholder, Priority.ALWAYS);
        });
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            // 保留后恢复项目视图占位符
            fileContentPanel.getChildren().setAll(fileContentPlaceholder);
            VBox.setVgrow(fileContentPlaceholder, Priority.ALWAYS);
        });

        HBox banner = new HBox(pathLabel, spring, rejectBtn, approveBtn);
        banner.getStyleClass().add("diff-banner");
        return banner;
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
}
