package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.DiffService;
import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.project.FileTreeService;
import cn.bitloom.project.ProjectInfo;
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
 * 扩展 coder 专有的 TERMINAL / PROJECT / DIFF 视图。
 * <p>
 * 通过 CoderEditorPanel.fxml 加载，基类 @FXML 字段（toolCallsView/todoView 等）
 * 由继承关系注入到父类 protected 字段。
 */
@Slf4j
@Component
public class CoderEditorPanelController extends EditorPanelController implements Initializable {

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

    private final FileTreeService fileTreeService;
    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalWidget;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;
    private ProjectInfo currentProject;

    public CoderEditorPanelController(FileTreeService fileTreeService,
                                      PtyTerminalService ptyTerminalService,
                                      DiffService diffService) {
        this.fileTreeService = fileTreeService;
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        setupFileTree();
    }

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

    // ===== 视图切换扩展 =====

    @Override
    protected void hideAllViews() {
        super.hideAllViews();
        terminalView.setVisible(false);
        terminalView.setManaged(false);
        projectSplit.setVisible(false);
        projectSplit.setManaged(false);
    }

    @Override
    public void showTerminalView() {
        hideAllViews();
        terminalView.setVisible(true);
        terminalView.setManaged(true);
        currentViewType = ViewType.TERMINAL;
    }

    @Override
    public void showProjectView() {
        hideAllViews();
        projectSplit.setVisible(true);
        projectSplit.setManaged(true);
        currentViewType = ViewType.PROJECT;
    }

    // ===== 项目与文件树 =====

    @Override
    public void setCurrentProject(ProjectInfo project) {
        this.currentProject = project;
        if (project != null) {
            Platform.runLater(() -> buildFileTree(project));
        } else {
            fileTree.setRoot(null);
        }
    }

    private void buildFileTree(ProjectInfo project) {
        try {
            Path projectPath = Paths.get(project.path());
            TreeItem<Path> root = fileTreeService.buildFileTree(projectPath);
            fileTree.setRoot(root);
        } catch (Exception e) {
            log.error("构建文件树失败: {}", project.path(), e);
        }
    }

    @Override
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

            Platform.runLater(codeArea::requestFocus);

            setupCodeAreaContextMenu(codeArea);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
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

    @Override
    public void showDiffInProjectView(FileDiff diff) {
        show();
        showProjectView();
        renderDiffIntoPanel(diff, fileContentPanel);
    }

    private void renderDiffIntoPanel(FileDiff diff, VBox targetPanel) {
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

        HBox banner = createDiffBanner(diff);
        banner.setMaxWidth(Region.USE_PREF_SIZE);
        banner.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane stack = new StackPane(splitPane, banner);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new javafx.geometry.Insets(8, 12, 0, 12));
        VBox.setVgrow(stack, Priority.ALWAYS);

        targetPanel.getChildren().setAll(stack);
        VBox.setVgrow(stack, Priority.ALWAYS);

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
            fileContentPanel.getChildren().setAll(fileContentPlaceholder);
            VBox.setVgrow(fileContentPlaceholder, Priority.ALWAYS);
        });
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            fileContentPanel.getChildren().setAll(fileContentPlaceholder);
            VBox.setVgrow(fileContentPlaceholder, Priority.ALWAYS);
        });

        HBox banner = new HBox(pathLabel, spring, rejectBtn, approveBtn);
        banner.getStyleClass().add("diff-banner");
        return banner;
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
