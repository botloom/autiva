package cn.bitloom.controller;

import cn.bitloom.agentic.tool.file.DiffService;
import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.node.editor.syntax.SyntaxHighlighter;
import cn.bitloom.node.editor.syntax.SyntaxHighlighterFactory;
import cn.bitloom.node.message.InputTag;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.git.GitFileStatus;
import cn.bitloom.project.git.GitStatusService;
import cn.bitloom.project.git.ProjectStatusStore;
import cn.bitloom.vm.CodeHomePageViewModel;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.CodeArea;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

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
    private final ProjectStatusStore projectStatusStore;
    private final GitStatusService gitStatusService;
    private boolean refreshSubscribed = false;

    public CoderEditorPanelController(PtyTerminalService ptyTerminalService,
                                      DiffService diffService,
                                      ProjectStatusStore projectStatusStore,
                                      GitStatusService gitStatusService) {
        this.ptyTerminalService = ptyTerminalService;
        this.diffService = diffService;
        this.projectStatusStore = projectStatusStore;
        this.gitStatusService = gitStatusService;
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
            // 行号处按 Git 改动着色：存入可变行状态引用，外部刷新时仅换引用并重绘
            AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef =
                    new AtomicReference<>(computeLineStatus(filePath));
            tab.userData.put("lineStatus", lineStatusRef);
            applyGitGutter(codeArea, lineStatusRef);
            codeArea.replaceText(content);
            // 记录是否有未保存改动，外部变化时避免覆盖用户编辑
            tab.userData.put("dirty", false);
            codeArea.textProperty().addListener((obs, oldText, newText) ->
                    tab.userData.put("dirty", !content.equals(newText)));
            codeArea.getStyleClass().add("editor-panel__code-area");
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(filePath);
            highlighter.apply(codeArea, content);
            codeArea.moveTo(0);

            codeArea.setOnKeyPressed(e -> {
                if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                    e.consume();
                    saveFileContent(filePath, codeArea.getText());
                    tab.userData.put("dirty", false);
                }
            });

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");

            fileContent.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            // 依据项目 Git 状态着色（tab 标题 + 代码区状态色）
            applyGitStyleToTab(tab, filePath);

            Platform.runLater(codeArea::requestFocus);
            setupCodeAreaContextMenu(codeArea, filePath);
            subscribeStatusRefresh();
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
            fileContent.getChildren().setAll(createErrorContent("读取文件失败: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
            fileContent.getChildren().setAll(createErrorContent("显示文件内容失败: " + e.getMessage(), null));
        }
    }

    /**
     * 计算文件相对 HEAD 的行级改动映射（0-based 行号 → Git 状态），供行号处着色。
     * 项目根取自共享状态存储（可能为 null/非 Git，此时返回空 map，图标不标色）。
     */
    private Map<Integer, GitFileStatus> computeLineStatus(Path filePath) {
        Path root = projectStatusStore.getProjectRoot();
        if (root == null) {
            return Map.of();
        }
        Map<Integer, GitFileStatus> map = gitStatusService.diffLineStatus(root, filePath);
        return map.isEmpty() ? Map.of() : map;
    }

    /**
     * 设置代码区行号工厂：在标准行号基础上，为 Git 改动行追加状态修饰类。
     * 行状态引用可被外部替换后通过重设工厂刷新（RichTextFX 重设工厂会重建可见行图形）。
     */
    private void applyGitGutter(CodeArea codeArea, AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef) {
        IntFunction<Node> factory = idx -> {
            Label label = new Label(String.valueOf(idx + 1));
            label.getStyleClass().addAll("lineno", "git-lineno");
            GitFileStatus st = lineStatusRef.get().get(idx);
            // 新增/未跟踪行 → 绿色；修改行 → 蓝色；删除做锚定的行 → 蓝色
            if (st == GitFileStatus.ADDED) {
                label.getStyleClass().add("git-lineno--added");
            } else if (st == GitFileStatus.MODIFIED) {
                label.getStyleClass().add("git-lineno--modified");
            }
            label.setAlignment(Pos.CENTER_RIGHT);
            return label;
        };
        codeArea.setParagraphGraphicFactory(factory);
    }

    /**
     * 根据项目 Git 状态为已打开的文件 tab 标题着色，并在状态变化时同步刷新。
     * 代码区改为按行（行号处）着色，由 {@link #applyGitGutter} 处理。
     */
    private void applyGitStyleToTab(EditorTab tab, Path filePath) {
        if (tab == null || tab.viewType != ViewType.FILE) {
            return;
        }
        GitFileStatus st = projectStatusStore.statusOf(filePath);
        // 状态样式类名与变色（仅标题）
        String gitClass = null;
        if (st != null) {
            gitClass = switch (st) {
                case ADDED -> "editor-panel__tab--git-added";
                case MODIFIED -> "editor-panel__tab--git-modified";
                case UNTRACKED -> "editor-panel__tab--git-untracked";
            };
        }
        tab.header.getStyleClass().removeAll(
                "editor-panel__tab--git-added", "editor-panel__tab--git-modified", "editor-panel__tab--git-untracked");
        if (gitClass != null) {
            tab.header.getStyleClass().add(gitClass);
        }
    }

    /**
     * 订阅 Git 状态刷新信号：对已打开文件 tab 重新着色（并重读无未保存改动的文件内容）。
     */
    private void subscribeStatusRefresh() {
        if (refreshSubscribed) {
            return;
        }
        refreshSubscribed = true;
        projectStatusStore.refreshSignal.addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> {
                    List<EditorTab> fileTabs = tabs.stream()
                            .filter(t -> t.viewType == ViewType.FILE && t.userData.get("path") != null)
                            .toList();
                    for (EditorTab tab : fileTabs) {
                        Path p = Path.of((String) tab.userData.get("path"));
                        // 无未保存改动时重读文件内容，确保随外部变化更新
                        if (Boolean.FALSE.equals(tab.userData.get("dirty")) && Files.isRegularFile(p)) {
                            // 重新计算行级改动并更新行号着色引用
                            if (tab.userData.get("lineStatus") instanceof AtomicReference<?> ref) {
                                @SuppressWarnings("unchecked")
                                AtomicReference<Map<Integer, GitFileStatus>> lineRef =
                                        (AtomicReference<Map<Integer, GitFileStatus>>) ref;
                                lineRef.set(computeLineStatus(p));
                            }
                            try {
                                String fresh = Files.readString(p);
                                if (tab.content instanceof VBox vbox) {
                                    vbox.lookupAll(".editor-panel__code-area").forEach(n -> {
                                        if (n instanceof CodeArea ca) {
                                            if (tab.userData.get("lineStatus") instanceof AtomicReference<?> lineRef) {
                                                @SuppressWarnings("unchecked")
                                                AtomicReference<Map<Integer, GitFileStatus>> lr =
                                                        (AtomicReference<Map<Integer, GitFileStatus>>) lineRef;
                                                applyGitGutter(ca, lr);
                                            }
                                            if (!ca.getText().equals(fresh)) {
                                                ca.replaceText(fresh);
                                            }
                                        }
                                    });
                                }
                            } catch (IOException e) {
                                log.warn("重新读取文件失败: {}", p, e);
                            }
                        }
                        applyGitStyleToTab(tab, p);
                    }
                }));
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

        setupCodeAreaContextMenu(leftArea, null);
        setupCodeAreaContextMenu(rightArea, null);
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

    private void setupCodeAreaContextMenu(CodeArea codeArea, Path filePath) {
        ContextMenu menu = new ContextMenu();
        MenuItem addToChatItem = new MenuItem("添加到对话框");
        addToChatItem.setOnAction(e -> {
            String selected = codeArea.getSelectedText();
            if (selected == null || selected.isBlank() || indexController == null) {
                return;
            }
            if (filePath != null) {
                // 文件编辑器选中内容 → 以文件引用 tag 加入对话框
                int[] range = selectedLineRange(codeArea);
                indexController.addFileRefToChat(filePath, range[0], range[1]);
            } else {
                // 终端/diff 等无从定位的选区 → 以文本片段 tag 加入对话框
                indexController.addTextToChat(selected);
            }
        });
        menu.getItems().add(addToChatItem);
        codeArea.setContextMenu(menu);
        // 同时为 CodeArea 注册拖拽源：选中后拖拽到对话框输入框生成 tag
        setupCodeAreaDragSource(codeArea, filePath);
    }

    /**
     * 为 CodeArea 注册拖拽源，支持将选中文本拖拽到对话框输入框。
     * <ul>
     *   <li>文件编辑器（filePath != null）：携带文件引用自定义 MIME</li>
     *   <li>终端/diff（filePath == null）：携带纯文本</li>
     * </ul>
     *
     * <p>交互保护：通过 addEventFilter 在鼠标按下时记录已有选区（IndexRange），
     * 只在按下时已有选区的情形下启动 DnD，避免破坏"拖拽新建选择"的常规体验。
     * 选区文本与行号均基于按下时记录的 IndexRange 计算，避免 CodeArea 内部
     * 在鼠标按下后清空选区导致取值丢失。
     */
    private void setupCodeAreaDragSource(CodeArea codeArea, Path filePath) {
        final IndexRange[] selectionOnPress = {null};
        codeArea.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            IndexRange sel = codeArea.getSelection();
            selectionOnPress[0] = (sel != null && sel.getLength() > 0) ? sel : null;
        });
        codeArea.setOnDragDetected(event -> {
            IndexRange sel = selectionOnPress[0];
            if (sel == null) {
                return;
            }
            String text = codeArea.getText();
            int start = Math.min(sel.getStart(), text.length());
            int end = Math.min(sel.getEnd(), text.length());
            String selected = text.substring(start, end);
            if (selected.isBlank()) {
                return;
            }
            int[] range = lineRangeFor(text, start, end);
            Dragboard db = codeArea.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            if (filePath != null) {
                content.put(InputTag.FILE_REF_FORMAT,
                        InputTag.encodeFileRef(filePath, range[0], range[1]));
                content.putString(selected);
            } else {
                content.putString(selected);
            }
            db.setContent(content);
            event.consume();
        });
    }

    /**
     * 计算 CodeArea 当前选区对应的起始/结束行号（1-based）。
     */
    private int[] selectedLineRange(CodeArea codeArea) {
        IndexRange selection = codeArea.getSelection();
        return lineRangeFor(codeArea.getText(), selection.getStart(), selection.getEnd());
    }

    /**
     * 根据文本和起止字符偏移计算行号（1-based）。
     * 选区末尾若落在换行符上，忽略该换行符，避免结束行号多报 1。
     */
    private int[] lineRangeFor(String text, int rawStart, int rawEnd) {
        int start = Math.min(rawStart, text.length());
        int end = Math.min(rawEnd, text.length());
        if (end > 0 && end <= text.length() && text.charAt(end - 1) == '\n') {
            end--;
        }
        int startLine = charIndexToLine(text, start);
        int endLine = charIndexToLine(text, end);
        return new int[]{startLine, endLine};
    }

    private int charIndexToLine(String text, int pos) {
        int line = 1;
        for (int i = 0; i < pos; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
