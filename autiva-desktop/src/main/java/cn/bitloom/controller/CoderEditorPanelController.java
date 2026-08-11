package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.DiffService;
import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.node.editor.syntax.SyntaxHighlighter;
import cn.bitloom.node.editor.syntax.SyntaxHighlighterFactory;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.vm.CodeHomePageViewModel;
import javafx.application.Platform;
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
 * 继承通用 {@link EditorPanelController}，扩展 coder 专有的终端/文件/DIFF 视图。
 * 所有视图以 Tab 形式管理，终端/文件/DIFF 支持多开。
 */
@Slf4j
@Component
public class CoderEditorPanelController extends EditorPanelController implements Initializable {

    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    public CoderEditorPanelController(PtyTerminalService ptyTerminalService,
                                      DiffService diffService) {
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
    }

    // ===== "+" 下拉菜单 =====

    @Override
    protected ContextMenu buildAddTabMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getItems().add(createMenuItem("终端", () -> openTerminal(resolveWorkingDir())));
        menu.getItems().add(createMenuItem("工具视图", () -> openSingleTab(ViewType.TOOL_CALLS, "工具视图", toolCallsView)));
        menu.getItems().add(createMenuItem("待办事项", () -> openSingleTab(ViewType.TODO, "待办事项", todoView)));
        return menu;
    }

    /**
     * 解析当前工作目录
     */
    private Path resolveWorkingDir() {
        if (indexController != null) {
            AbstractHomePageController home = indexController.getHomePageController();
            if (home != null && home.getViewModel() instanceof CodeHomePageViewModel coderVm) {
                ProjectInfo project = coderVm.getCurrentProject();
                if (project != null) {
                    return Path.of(project.path());
                }
            }
        }
        return null;
    }

    // ===== 终端（多开） =====

    @Override
    public void openTerminal(Path workingDir) {
        show();
        VBox terminalContent = new VBox();
        terminalContent.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(terminalContent, Priority.ALWAYS);

        EditorTab tab = createTab(ViewType.TERMINAL, "终端", terminalContent, true);
        addTab(tab);
        selectTab(tab);

        terminalContent.getChildren().setAll(createLoadingContent("正在启动终端..."));

        new Thread(() -> {
            try {
                PtySession session = ptyTerminalService.createSession(workingDir);
                JediTerminalView view = new JediTerminalView();
                view.startSession(session);

                Platform.runLater(() -> {
                    tab.userData.put("session", session);
                    tab.userData.put("view", view);
                    terminalContent.getChildren().setAll(view);
                    VBox.setVgrow(view, Priority.ALWAYS);
                    setupTerminalContextMenu(view);
                    Platform.runLater(view::requestFocus);
                });
            } catch (IOException e) {
                log.error("创建终端会话失败", e);
                Platform.runLater(() -> terminalContent.getChildren().setAll(
                        createErrorContent("终端启动失败: " + e.getMessage(),
                                () -> {
                                    closeTab(tab);
                                    openTerminal(workingDir);
                                })));
            } catch (Exception e) {
                log.error("终端初始化异常", e);
                Platform.runLater(() -> terminalContent.getChildren().setAll(
                        createErrorContent("终端初始化异常: " + e.getMessage(),
                                () -> {
                                    closeTab(tab);
                                    openTerminal(workingDir);
                                })));
            }
        }).start();
    }

    @Override
    public void closeTerminal() {
        List<EditorTab> terminalTabs = tabs.stream()
                .filter(t -> t.viewType == ViewType.TERMINAL)
                .toList();
        for (EditorTab tab : terminalTabs) {
            closeTab(tab);
        }
    }

    @Override
    protected void onTabClosed(EditorTab tab) {
        if (tab.viewType == ViewType.TERMINAL) {
            JediTerminalView view = (JediTerminalView) tab.userData.get("view");
            PtySession session = (PtySession) tab.userData.get("session");
            if (view != null) {
                view.closeSession();
            }
            if (session != null) {
                ptyTerminalService.closeSession(session.getSessionId());
            }
        }
    }

    // ===== 文件内容（多开） =====

    @Override
    public void showFileContent(Path filePath) {
        show();
        String pathKey = filePath.toString();
        for (EditorTab tab : tabs) {
            if (tab.viewType == ViewType.FILE && pathKey.equals(tab.userData.get("path"))) {
                selectTab(tab);
                return;
            }
        }

        String fileName = filePath.getFileName().toString();
        VBox fileContent = new VBox();
        fileContent.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(fileContent, Priority.ALWAYS);

        EditorTab tab = createTab(ViewType.FILE, fileName, fileContent, true);
        tab.userData.put("path", pathKey);
        addTab(tab);
        selectTab(tab);

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

            fileContent.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            Platform.runLater(codeArea::requestFocus);
            setupCodeAreaContextMenu(codeArea);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
            fileContent.getChildren().setAll(createErrorContent("读取文件失败: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
            fileContent.getChildren().setAll(createErrorContent("显示文件内容失败: " + e.getMessage(), null));
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

    // ===== DIFF 视图（单 diff tab） =====

    @Override
    public void showDiffInProjectView(FileDiff diff) {
        show();
        String diffId = diff.id();
        for (EditorTab tab : tabs) {
            if (tab.viewType == ViewType.DIFF && diffId.equals(tab.userData.get("diffId"))) {
                selectTab(tab);
                return;
            }
        }

        String fileName = Paths.get(diff.filePath()).getFileName().toString();
        VBox diffContent = new VBox();
        diffContent.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(diffContent, Priority.ALWAYS);

        EditorTab tab = createTab(ViewType.DIFF, fileName, diffContent, true);
        tab.userData.put("diffId", diffId);
        tab.userData.put("diff", diff);
        addTab(tab);
        selectTab(tab);

        renderDiffIntoContent(diff, diffContent, tab);
    }

    /**
     * 渲染 diff 内容到指定容器（工具栏 + 左右对比）
     */
    private void renderDiffIntoContent(FileDiff diff, VBox container, EditorTab tab) {
        // 构建文本和行号
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

        // 滚动联动
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

        // 工具栏
        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("editor-panel__diff-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Label pathLabel = new Label(Paths.get(diff.filePath()).getFileName().toString());
        pathLabel.getStyleClass().add("editor-panel__diff-toolbar-path");
        pathLabel.setTooltip(new Tooltip(diff.filePath()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().addAll("editor-panel__diff-toolbar-btn", "editor-panel__diff-toolbar-btn--reject");
        Button approveBtn = new Button("保留");
        approveBtn.getStyleClass().addAll("editor-panel__diff-toolbar-btn", "editor-panel__diff-toolbar-btn--approve");
        toolbar.getChildren().addAll(pathLabel, spacer, rejectBtn, approveBtn);

        rejectBtn.setOnAction(e -> {
            diffService.rejectFileDiff(diff);
            closeTab(tab);
            if (indexController != null) {
                indexController.refreshDiffReviewBar();
            }
        });
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            closeTab(tab);
            if (indexController != null) {
                indexController.refreshDiffReviewBar();
            }
        });

        container.getChildren().addAll(toolbar, splitPane);

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
