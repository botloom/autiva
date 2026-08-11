package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.DiffService;
import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.node.editor.syntax.SyntaxHighlighter;
import cn.bitloom.node.editor.syntax.SyntaxHighlighterFactory;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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
 * Coder 模式编辑器面板控制器。
 * <p>
 * 继承通用 {@link EditorPanelController}（TOOL_CALLS / TODO 视图），
 * 扩展 coder 专有的 TERMINAL / DIFF 视图。
 * <p>
 * DIFF 视图为独立的左右对比 diff 看板，左侧文件列表 + 右侧 IDEA 风格双栏对比，
 * 支持差异导航（上一个/下一个）和多文件切换。
 * <p>
 * 项目目录树已迁移至 SideBarController 中展示，本面板不再包含 PROJECT 视图。
 */
@Slf4j
@Component
public class CoderEditorPanelController extends EditorPanelController implements Initializable {

    @FXML
    private VBox terminalView;
    @FXML
    private VBox fileView;

    // ===== Diff 视图字段 =====
    @FXML
    private SplitPane diffView;
    @FXML
    private ListView<FileDiff> diffFileListView;
    @FXML
    private Label diffToolbarPath;
    @FXML
    private Button diffRejectBtn;
    @FXML
    private Button diffApproveBtn;
    @FXML
    private StackPane diffContentStack;

    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalWidget;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;

    // Diff 视图运行时状态
    private final ObservableList<FileDiff> diffFileList = FXCollections.observableArrayList();
    private FileDiff currentDiff;
    private CodeArea currentLeftArea;
    private CodeArea currentRightArea;

    public CoderEditorPanelController(PtyTerminalService ptyTerminalService,
                                      DiffService diffService) {
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        setupDiffFileListView();
        setupDiffToolbarButtons();
    }

    // ===== 视图切换扩展 =====

    @Override
    protected void hideAllViews() {
        super.hideAllViews();
        terminalView.setVisible(false);
        terminalView.setManaged(false);
        fileView.setVisible(false);
        fileView.setManaged(false);
        diffView.setVisible(false);
        diffView.setManaged(false);
    }

    @Override
    public void showTerminalView() {
        hideAllViews();
        terminalView.setVisible(true);
        terminalView.setManaged(true);
        currentViewType = ViewType.TERMINAL;
    }

    @Override
    public void showDiffView() {
        hideAllViews();
        diffView.setVisible(true);
        diffView.setManaged(true);
        currentViewType = ViewType.DIFF;
    }

    @Override
    public void showFileView() {
        hideAllViews();
        fileView.setVisible(true);
        fileView.setManaged(true);
        currentViewType = ViewType.FILE;
    }

    // ===== 文件内容视图 =====

    /**
     * 在右侧面板显示文件内容（支持语法高亮、行号、Ctrl+S 保存）。
     * 由侧边栏目录树双击文件触发。
     */
    @Override
    public void showFileContent(Path filePath) {
        show();
        showFileView();

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

            codeArea.setOnKeyPressed(e -> {
                if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                    e.consume();
                    saveFileContent(filePath, codeArea.getText());
                }
            });

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");

            fileView.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            Platform.runLater(codeArea::requestFocus);
            setupCodeAreaContextMenu(codeArea);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
            fileView.getChildren().setAll(createErrorContent("读取文件失败: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
            fileView.getChildren().setAll(createErrorContent("显示文件内容失败: " + e.getMessage(), null));
        }
    }

    private void saveFileContent(Path filePath, String content) {
        try {
            Files.writeString(filePath, content);
            log.info("文件已保存: {}", filePath);
        } catch (IOException e) {
            log.error("保存文件失败: {}", filePath, e);
        }
    }

    // ===== 终端 =====

    @Override
    public void openTerminal(Path workingDir) {
        this.lastTerminalWorkingDir = workingDir;
        show();
        showTerminalView();
        ensureTerminalStarted(workingDir);
    }

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

    @Override
    public void closeTerminal() {
        closeTerminalInternal();
        if (terminalView != null) {
            terminalView.getChildren().setAll(createLoadingContent("终端已关闭"));
        }
    }

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
     * 在 diff 视图中显示指定文件的 diff（点击对话框上方的 diff 文件卡片时调用）
     */
    @Override
    public void showDiffInProjectView(FileDiff diff) {
        show();
        showDiffView();
        refreshDiffFileList(diff);
        renderDiffIntoDiffView(diff);
    }

    /**
     * 刷新左侧文件列表，确保传入的 diff 被选中
     */
    private void refreshDiffFileList(FileDiff selectedDiff) {
        List<FileDiff> pending = diffService.getPendingDiffs();
        // 确保传入的 diff 在列表中
        if (selectedDiff != null && pending.stream().noneMatch(d -> d.id().equals(selectedDiff.id()))) {
            pending.add(selectedDiff);
        }
        diffFileList.setAll(pending);
        if (selectedDiff != null) {
            diffFileListView.getSelectionModel().select(selectedDiff);
        }
    }

    /**
     * 初始化文件列表 ListView 和单元格工厂
     */
    private void setupDiffFileListView() {
        diffFileListView.setItems(diffFileList);
        diffFileListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileDiff diff, boolean empty) {
                super.updateItem(diff, empty);
                if (empty || diff == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(createDiffFileCell(diff));
                    setText(null);
                }
            }
        });
        diffFileListView.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null && (currentDiff == null || !val.id().equals(currentDiff.id()))) {
                renderDiffIntoDiffView(val);
            }
        });
    }

    /**
     * 创建文件列表项（徽章 + 文件名 + 统计）
     */
    private Node createDiffFileCell(FileDiff diff) {
        HBox cell = new HBox(6);
        cell.getStyleClass().add("diff-file-cell");
        cell.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label();
        badge.getStyleClass().add("diff-file-cell__badge");
        if (diff.isCreate()) {
            badge.setText("A");
            badge.getStyleClass().add("diff-file-cell__badge--add");
        } else if (diff.isDelete()) {
            badge.setText("D");
            badge.getStyleClass().add("diff-file-cell__badge--delete");
        } else {
            badge.setText("M");
            badge.getStyleClass().add("diff-file-cell__badge--modify");
        }
        cell.getChildren().add(badge);

        String filePath = diff.filePath();
        String fileName = filePath;
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSep >= 0 && lastSep < filePath.length() - 1) {
            fileName = filePath.substring(lastSep + 1);
        }
        Label nameLabel = new Label(fileName);
        nameLabel.getStyleClass().add("diff-file-cell__name");
        nameLabel.setTooltip(new Tooltip(filePath));
        cell.getChildren().add(nameLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        cell.getChildren().add(spacer);

        int[] stats = computeDiffStats(diff);
        Label statsLabel = new Label("+" + stats[0] + " -" + stats[1]);
        statsLabel.getStyleClass().add("diff-file-cell__stats");
        cell.getChildren().add(statsLabel);

        return cell;
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

    /**
     * 初始化工具栏按钮事件
     */
    private void setupDiffToolbarButtons() {
        diffRejectBtn.setOnAction(e -> handleDiffReject());
        diffApproveBtn.setOnAction(e -> handleDiffApprove());
    }

    /**
     * 撤销当前 diff
     */
    private void handleDiffReject() {
        if (currentDiff == null) return;
        FileDiff rejected = currentDiff;
        diffService.rejectFileDiff(rejected);
        diffFileList.remove(rejected);
        // 通知首页刷新 diffReviewBar
        if (indexController != null) {
            indexController.refreshDiffReviewBar();
        }
        if (diffFileList.isEmpty()) {
            showDiffPlaceholder();
        } else {
            diffFileListView.getSelectionModel().select(0);
        }
    }

    /**
     * 保留当前 diff
     */
    private void handleDiffApprove() {
        if (currentDiff == null) return;
        FileDiff approved = currentDiff;
        diffService.approveFileDiff(approved);
        diffFileList.remove(approved);
        if (indexController != null) {
            indexController.refreshDiffReviewBar();
        }
        if (diffFileList.isEmpty()) {
            showDiffPlaceholder();
        } else {
            diffFileListView.getSelectionModel().select(0);
        }
    }

    /**
     * 显示无 diff 占位符
     */
    private void showDiffPlaceholder() {
        currentDiff = null;
        currentLeftArea = null;
        currentRightArea = null;
        diffToolbarPath.setText("");
        diffContentStack.getChildren().setAll(new Label("没有待审查的变更"));
    }

    /**
     * 渲染 diff 内容到右侧内容区（IDEA 风格左右对比）
     */
    private void renderDiffIntoDiffView(FileDiff diff) {
        currentDiff = diff;

        StringBuilder leftText = new StringBuilder();
        StringBuilder rightText = new StringBuilder();
        List<Integer> leftLineNumbers = new ArrayList<>();
        List<Integer> rightLineNumbers = new ArrayList<>();
        List<String> leftParagraphStyles = new ArrayList<>();
        List<String> rightParagraphStyles = new ArrayList<>();

        if (diff.hunks() != null) {
            for (FileDiff.Hunk hunk : diff.hunks()) {
                int currentOldLine = hunk.oldStart() - 1;
                int currentNewLine = hunk.newStart() - 1;
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

        CodeArea leftArea = buildDiffCodeArea(leftText.toString(), leftLineNumbers, leftParagraphStyles, diff.filePath());
        CodeArea rightArea = buildDiffCodeArea(rightText.toString(), rightLineNumbers, rightParagraphStyles, diff.filePath());
        currentLeftArea = leftArea;
        currentRightArea = rightArea;

        final boolean[] syncing = {false};
        leftArea.estimatedScrollYProperty().addListener((obs, old, val) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            rightArea.estimatedScrollYProperty().setValue(val);
            syncing[0] = false;
        });
        rightArea.estimatedScrollYProperty().addListener((obs, old, val) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            leftArea.estimatedScrollYProperty().setValue(val);
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

        diffContentStack.getChildren().setAll(splitPane);

        // 更新工具栏文件名
        String fileName = Paths.get(diff.filePath()).getFileName().toString();
        diffToolbarPath.setText(fileName);
        diffToolbarPath.setTooltip(new Tooltip(diff.filePath()));

        setupCodeAreaContextMenu(leftArea);
        setupCodeAreaContextMenu(rightArea);
    }

    private CodeArea buildDiffCodeArea(String text, List<Integer> lineNumbers, List<String> paragraphStyles, String filePath) {
        CodeArea codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setShowCaret(Caret.CaretVisibility.ON);
        codeArea.getStyleClass().add("editor-panel__diff-area");

        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        codeArea.replaceText(text);

        int paragraphCount = codeArea.getParagraphs().size();
        for (int i = 0; i < paragraphCount && i < paragraphStyles.size(); i++) {
            String style = paragraphStyles.get(i);
            if (style != null) {
                codeArea.setParagraphStyle(i, List.of(style));
            }
        }

        codeArea.moveTo(0);

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

        try {
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(Paths.get(filePath));
            highlighter.apply(codeArea, codeArea.getText());
        } catch (Exception e) {
            log.warn("diff 语法高亮失败: {}", filePath, e);
        }

        return codeArea;
    }

    // ===== 加载与错误状态 =====

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

    // ===== 右键菜单 =====

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
