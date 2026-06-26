package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.project.FileTreeService;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.node.project.FileTreeCell;
import cn.bitloom.node.terminal.JediTerminalView;
import cn.bitloom.node.terminal.PtySession;
import cn.bitloom.node.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
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
 * 统一管理文件树、终端、变更列表、文件内容、diff 视图，通过 TabPane 组织
 */
@Slf4j
@Component
public class EditorPanelController implements Initializable {

    @FXML
    @Getter
    private VBox editorPanel;
    @FXML
    private Button toggleFileTreeButton;
    @FXML
    private Button closeButton;
    @FXML
    private VBox fileTreePanel;
    @FXML
    private TreeView<Path> fileTree;
    @FXML
    private TabPane tabPane;

    private final FileTreeService fileTreeService;
    private final PtyTerminalService ptyTerminalService;
    private final DiffService diffService;

    private JediTerminalView terminalView;
    private PtySession terminalSession;
    private Path lastTerminalWorkingDir;
    private ProjectInfo currentProject;
    private Disposable diffEventSubscription;
    private ListView<FileDiff> diffListView;

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
        setupChangesTab();
        subscribeDiffEvents();
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
     * 设置变更标签页（持久标签，显示 pending diff 列表）
     */
    private void setupChangesTab() {
        Tab changesTab = new Tab("变更");
        changesTab.setId("changes");
        changesTab.setClosable(false);

        diffListView = new ListView<>();
        diffListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileDiff item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.filePath());
                }
            }
        });
        diffListView.setOnMouseClicked(event -> {
            FileDiff selected = diffListView.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 1) {
                showDiffView(selected);
            }
        });

        VBox wrapper = new VBox(diffListView);
        VBox.setVgrow(diffListView, Priority.ALWAYS);
        changesTab.setContent(wrapper);
        tabPane.getTabs().add(changesTab);
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
     * 打开终端标签页（若已存在则聚焦）
     */
    public void openTerminal(Path workingDir) {
        this.lastTerminalWorkingDir = workingDir;
        show();

        Tab terminalTab = findTabById("terminal");
        if (terminalTab != null) {
            tabPane.getSelectionModel().select(terminalTab);
            if (terminalView != null) {
                Platform.runLater(() -> terminalView.requestFocus());
            }
            return;
        }

        terminalTab = new Tab("终端");
        terminalTab.setId("terminal");
        terminalTab.setClosable(false);
        terminalTab.setContent(createLoadingContent("正在启动终端..."));
        tabPane.getTabs().add(0, terminalTab);
        tabPane.getSelectionModel().select(terminalTab);

        startTerminalAsync(workingDir, terminalTab);
    }

    /**
     * 异步启动终端会话
     */
    private void startTerminalAsync(Path workingDir, Tab terminalTab) {
        new Thread(() -> {
            try {
                closeTerminalInternal();

                terminalSession = ptyTerminalService.createSession(workingDir);
                terminalView = new JediTerminalView();
                terminalView.startSession(terminalSession);

                Platform.runLater(() -> {
                    terminalTab.setContent(terminalView);
                    Platform.runLater(() -> terminalView.requestFocus());
                });
            } catch (IOException e) {
                log.error("创建终端会话失败", e);
                Platform.runLater(() -> terminalTab.setContent(
                        createErrorContent("终端启动失败: " + e.getMessage(),
                                () -> openTerminal(lastTerminalWorkingDir))));
            } catch (Exception e) {
                log.error("终端初始化异常", e);
                Platform.runLater(() -> terminalTab.setContent(
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
        Tab terminalTab = findTabById("terminal");
        if (terminalTab != null) {
            terminalTab.setContent(createLoadingContent("终端已关闭"));
        }
    }

    /**
     * 内部关闭终端方法
     */
    private void closeTerminalInternal() {
        if (terminalView != null) {
            terminalView.closeSession();
            terminalView = null;
        }
        if (terminalSession != null) {
            ptyTerminalService.closeSession(terminalSession.getSessionId());
            terminalSession = null;
        }
    }

    /**
     * 显示文件内容（打开文件标签页，若已存在则聚焦）
     */
    public void showFileContent(Path filePath) {
        show();
        String tabId = filePath.toAbsolutePath().toString();
        Tab existing = findTabById(tabId);
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return;
        }

        try {
            String content = Files.readString(filePath);
            Tab fileTab = new Tab(filePath.getFileName().toString());
            fileTab.setId(tabId);
            fileTab.setClosable(true);

            CodeArea codeArea = new CodeArea();
            codeArea.setEditable(false);
            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            codeArea.replaceText(content);
            codeArea.getStyleClass().add("editor-panel__code-area");
            codeArea.moveTo(0);

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");
            fileTab.setContent(scrollPane);

            tabPane.getTabs().add(fileTab);
            tabPane.getSelectionModel().select(fileTab);
        } catch (IOException e) {
            log.warn("读取文件失败: {}", filePath, e);
        } catch (Exception e) {
            log.error("显示文件内容失败: {}", filePath, e);
        }
    }

    /**
     * 显示 diff 视图（打开 diff 标签页，若已存在则聚焦）
     */
    public void showDiffView(FileDiff diff) {
        show();
        String tabId = "diff:" + diff.id();
        Tab existing = findTabById(tabId);
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return;
        }

        Tab diffTab = new Tab("Diff: " + diff.filePath());
        diffTab.setId(tabId);
        diffTab.setClosable(true);

        StyleClassedTextArea diffArea = new StyleClassedTextArea();
        diffArea.setEditable(false);
        diffArea.setParagraphGraphicFactory(LineNumberFactory.get(diffArea));
        diffArea.getStyleClass().add("editor-panel__diff-area");

        int paragraph = 0;
        diffArea.appendText("文件: " + diff.filePath() + "\n");
        diffArea.setParagraphStyle(paragraph, List.of("diff-meta"));
        paragraph++;
        if (diff.isCreate()) {
            diffArea.appendText("(新建文件)\n");
            diffArea.setParagraphStyle(paragraph, List.of("diff-meta"));
            paragraph++;
        }
        if (diff.isDelete()) {
            diffArea.appendText("(删除文件)\n");
            diffArea.setParagraphStyle(paragraph, List.of("diff-meta"));
            paragraph++;
        }
        diffArea.appendText("\n");
        diffArea.setParagraphStyle(paragraph, List.of("diff-meta"));
        paragraph++;

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

        VBox container = new VBox(scrollPane, createDiffActionBar(diff));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        diffTab.setContent(container);

        tabPane.getTabs().add(diffTab);
        tabPane.getSelectionModel().select(diffTab);
    }

    /**
     * 创建 diff 审核按钮栏
     */
    private HBox createDiffActionBar(FileDiff diff) {
        HBox actionBar = new HBox();
        actionBar.getStyleClass().add("editor-panel__diff-actions");
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.setSpacing(8);
        actionBar.setPadding(new Insets(8, 0, 0, 0));

        Button approveBtn = new Button("确定");
        approveBtn.getStyleClass().add("editor-panel__diff-btn--approve");
        approveBtn.setOnAction(e -> {
            diffService.approveDiff(diff.id());
            Tab tab = findTabById("diff:" + diff.id());
            if (tab != null) {
                tabPane.getTabs().remove(tab);
            }
            updateDiffList(diffService.getPendingDiffs());
        });

        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().add("editor-panel__diff-btn--reject");
        rejectBtn.setOnAction(e -> {
            diffService.rejectDiff(diff.id());
            Tab tab = findTabById("diff:" + diff.id());
            if (tab != null) {
                tabPane.getTabs().remove(tab);
            }
            updateDiffList(diffService.getPendingDiffs());
        });

        actionBar.getChildren().addAll(approveBtn, rejectBtn);
        return actionBar;
    }

    /**
     * 更新变更列表，刷新标签标题
     */
    public void updateDiffList(List<FileDiff> diffs) {
        Platform.runLater(() -> {
            diffListView.getItems().clear();
            diffListView.getItems().addAll(diffs);
            Tab changesTab = findTabById("changes");
            if (changesTab != null) {
                changesTab.setText(diffs.isEmpty() ? "变更" : "变更 (" + diffs.size() + ")");
            }
        });
    }

    /**
     * 根据 id 查找已存在的标签页
     */
    private Tab findTabById(String id) {
        for (Tab tab : tabPane.getTabs()) {
            if (id.equals(tab.getId())) {
                return tab;
            }
        }
        return null;
    }

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

    @FXML
    private void handleToggleFileTree() {
        boolean visible = !fileTreePanel.isVisible();
        fileTreePanel.setVisible(visible);
        fileTreePanel.setManaged(visible);
    }

    @FXML
    private void handleClose() {
        hide();
    }
}
