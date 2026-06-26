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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualizedScrollPane;
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
import java.util.List;
import java.util.ResourceBundle;

/**
 * 编辑器面板控制器
 * 通过 StackPane 管理三个视图：终端、项目（文件树+文件内容）、变更（diff列表+diff视图）。
 */
@Slf4j
@Component
public class EditorPanelController implements Initializable {

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
                .subscribe(event -> {
                    List<FileDiff> pendingDiffs = diffService.getPendingDiffs();
                    Platform.runLater(() -> updateDiffList(pendingDiffs));
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
     * 显示文件内容（注入到项目视图右侧）
     */
    public void showFileContent(Path filePath) {
        show();
        showProjectView();

        try {
            String content = Files.readString(filePath);

            CodeArea codeArea = new CodeArea();
            codeArea.setEditable(false);
            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            codeArea.replaceText(content);
            codeArea.getStyleClass().add("editor-panel__code-area");
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(filePath);
            highlighter.apply(codeArea, content);
            codeArea.moveTo(0);

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");

            fileContentPanel.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
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
     */
    public void showDiffView(FileDiff diff) {
        show();
        showChangesView();

        StyleClassedTextArea diffArea = new StyleClassedTextArea();
        diffArea.setEditable(false);
        diffArea.setParagraphGraphicFactory(LineNumberFactory.get(diffArea));
        diffArea.getStyleClass().add("editor-panel__diff-area");

        int paragraph = 0;
        for (FileDiff.Hunk hunk : diff.hunks()) {
            String hunkHeader = String.format("@@ -%d,%d +%d,%d @@",
                    hunk.oldStart(), hunk.oldCount(), hunk.newStart(), hunk.newCount());
            diffArea.appendText(hunkHeader + "\n");
            diffArea.setParagraphStyle(paragraph, List.of("diff-hunk-header"));
            paragraph++;
            for (FileDiff.DiffLine line : hunk.lines()) {
                String prefix = switch (line.type()) {
                    case ADD -> "+";
                    case REMOVE -> "-";
                    case CONTEXT -> " ";
                };
                diffArea.appendText(prefix + line.content() + "\n");
                String styleCls = switch (line.type()) {
                    case ADD -> "diff-line-add";
                    case REMOVE -> "diff-line-remove";
                    case CONTEXT -> "diff-line-context";
                };
                diffArea.setParagraphStyle(paragraph, List.of(styleCls));
                paragraph++;
            }
        }
        diffArea.moveTo(0);

        VirtualizedScrollPane<StyleClassedTextArea> scrollPane = new VirtualizedScrollPane<>(diffArea);
        scrollPane.getStyleClass().add("editor-panel__code-scroll");

        VBox container = new VBox(createFileMetaBar(diff), scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        diffViewPanel.getChildren().setAll(container);
        VBox.setVgrow(container, Priority.ALWAYS);
    }

    /**
     * 创建 diff 文件元信息条（顶部）
     * 左侧显示文件路径，右侧放审核按钮（撤销/确定）
     */
    private HBox createFileMetaBar(FileDiff diff) {
        String filePath = diff.filePath();

        Label pathLabel = new Label(filePath);
        pathLabel.getStyleClass().add("editor-panel__meta-path");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().add("editor-panel__diff-btn--reject");
        rejectBtn.setOnAction(e -> {
            diffService.rejectDiff(diff.id());
            resetDiffViewPlaceholder();
            updateDiffList(diffService.getPendingDiffs());
        });

        Button approveBtn = new Button("确定");
        approveBtn.getStyleClass().add("editor-panel__diff-btn--approve");
        approveBtn.setOnAction(e -> {
            diffService.approveDiff(diff.id());
            resetDiffViewPlaceholder();
            updateDiffList(diffService.getPendingDiffs());
        });

        HBox metaBar = new HBox(pathLabel, spacer, rejectBtn, approveBtn);
        metaBar.getStyleClass().add("editor-panel__meta-bar");
        return metaBar;
    }

    /**
     * 重置 diff 视图为占位符
     */
    private void resetDiffViewPlaceholder() {
        diffViewPanel.getChildren().setAll(diffPlaceholder);
        VBox.setVgrow(diffPlaceholder, Priority.ALWAYS);
    }

    /**
     * 刷新变更列表
     */
    private void refreshDiffList() {
        List<FileDiff> pendingDiffs = diffService.getPendingDiffs();
        diffList.getItems().clear();
        diffList.getItems().addAll(pendingDiffs);
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
}
