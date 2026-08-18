package cn.bitloom.controller;

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
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.CodeArea;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

/**
 * Coder 模式编辑器面板控制器。
 * <p>
 * 继承通用 {@link EditorPanelController}，扩展 coder 专有的终端/文件视图。
 * 所有视图以 Tab 形式管理，终端/文件支持多开。
 */
@Slf4j
@Component
public class CoderEditorPanelController extends EditorPanelController implements Initializable {

    private final PtyTerminalService ptyTerminalService;
    private final ProjectStatusStore projectStatusStore;
    private final GitStatusService gitStatusService;
    private boolean refreshSubscribed = false;

    /** 编辑器实时 git 标注防抖调度器：停止输入后延迟重算行级改动并重绘 */
    private static final long GIT_REFRESH_DEBOUNCE_MS = 300;
    private final ScheduledExecutorService gitRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "autiva-editor-git-refresh");
        t.setDaemon(true);
        return t;
    });

    public CoderEditorPanelController(PtyTerminalService ptyTerminalService,
                                      ProjectStatusStore projectStatusStore,
                                      GitStatusService gitStatusService) {
        this.ptyTerminalService = ptyTerminalService;
        this.projectStatusStore = projectStatusStore;
        this.gitStatusService = gitStatusService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
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

    // ===== 终端（单顶部按钮 + 子 tab 多开） =====

    @Override
    public void openTerminal(Path workingDir) {
        show();
        EditorTab terminalTab = findTabByType(ViewType.TERMINAL);
        SubTabContainer container;
        if (terminalTab != null) {
            container = (SubTabContainer) terminalTab.userData.get("container");
            selectTab(terminalTab);
        } else {
            container = new SubTabContainer(
                    _ -> openTerminal(resolveWorkingDir()),
                    this::cleanupTerminalSubTab);
            final EditorTab newTab = createTab(ViewType.TERMINAL, container.getView());
            newTab.userData.put("container", container);
            newTab.userData.put("workingDir", workingDir);
            container.setOnEmpty(() -> closeTab(newTab));
            container.setOnCloseView(() -> closeTab(newTab));
            addTab(newTab);
            selectTab(newTab);
            terminalTab = newTab;
        }

        // 创建新的终端子 tab
        VBox terminalContent = new VBox();
        terminalContent.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(terminalContent, Priority.ALWAYS);

        int index = container.getSubTabs().size() + 1;
        SubTabContainer.SubTab subTab = container.addSubTab("终端 " + index, terminalContent);

        terminalContent.getChildren().setAll(createLoadingContent("正在启动终端..."));

        final Path wd = workingDir != null ? workingDir : resolveWorkingDir();
        new Thread(() -> {
            try {
                PtySession session = ptyTerminalService.createSession(wd);
                JediTerminalView view = new JediTerminalView();
                view.startSession(session);

                Platform.runLater(() -> {
                    subTab.userData.put("session", session);
                    subTab.userData.put("view", view);
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
                                    container.closeSubTab(subTab);
                                    openTerminal(wd);
                                })));
            } catch (Exception e) {
                log.error("终端初始化异常", e);
                Platform.runLater(() -> terminalContent.getChildren().setAll(
                        createErrorContent("终端初始化异常: " + e.getMessage(),
                                () -> {
                                    container.closeSubTab(subTab);
                                    openTerminal(wd);
                                })));
            }
        }).start();
    }

    private void cleanupTerminalSubTab(SubTabContainer.SubTab subTab) {
        JediTerminalView view = (JediTerminalView) subTab.userData.get("view");
        PtySession session = (PtySession) subTab.userData.get("session");
        if (view != null) {
            view.closeSession();
        }
        if (session != null) {
            ptyTerminalService.closeSession(session.getSessionId());
        }
    }

    @Override
    public void closeTerminal() {
        EditorTab terminalTab = findTabByType(ViewType.TERMINAL);
        if (terminalTab != null) {
            closeTab(terminalTab);
        }
    }

    @Override
    protected void onTabClosed(EditorTab tab) {
        if (tab.viewType == ViewType.TERMINAL) {
            SubTabContainer container = (SubTabContainer) tab.userData.get("container");
            if (container != null) {
                for (SubTabContainer.SubTab subTab : container.getSubTabs()) {
                    cleanupTerminalSubTab(subTab);
                }
            }
        }
    }

    // ===== 文件内容（单顶部按钮 + 子 tab 多开） =====

    @Override
    public void showFileContent(Path filePath) {
        show();
        String pathKey = filePath.toString();

        EditorTab fileTab = findTabByType(ViewType.FILE);
        SubTabContainer container;
        if (fileTab != null) {
            container = (SubTabContainer) fileTab.userData.get("container");
            selectTab(fileTab);
            // 检查文件是否已打开
            SubTabContainer.SubTab existing = container.findSubTabByUserData("path", pathKey);
            if (existing != null) {
                container.selectSubTab(existing);
                return;
            }
        } else {
            container = new SubTabContainer(
                    _ -> openFileViaDialog(),
                    this::cancelGitRefreshJob);
            final EditorTab newTab = createTab(ViewType.FILE, container.getView());
            newTab.userData.put("container", container);
            container.setOnEmpty(() -> closeTab(newTab));
            container.setOnCloseView(() -> closeTab(newTab));
            addTab(newTab);
            selectTab(newTab);
            fileTab = newTab;
        }

        String fileName = filePath.getFileName().toString();
        VBox fileContent = new VBox();
        fileContent.getStyleClass().add("editor-panel__view");
        VBox.setVgrow(fileContent, Priority.ALWAYS);

        SubTabContainer.SubTab subTab = container.addSubTab(fileName, fileContent);
        subTab.userData.put("path", pathKey);

        try {
            String content = Files.readString(filePath);

            CodeArea codeArea = new CodeArea();
            codeArea.setEditable(true);
            codeArea.setShowCaret(Caret.CaretVisibility.ON);
            // 行号处按 Git 改动着色：存入可变行状态引用，外部刷新时仅换引用并重绘
            AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef =
                    new AtomicReference<>(computeLineStatus(filePath));
            subTab.userData.put("lineStatus", lineStatusRef);
            // 支持 Ctrl + 滚轮缩放代码（正文 + 行号联动），默认 12px
            SimpleDoubleProperty codeFontSize = new SimpleDoubleProperty(12.0);
            applyGitGutter(codeArea, lineStatusRef, codeFontSize.get());
            setupCodeAreaZoom(codeArea, codeFontSize, lineStatusRef);
            codeArea.replaceText(content);
            // 记录是否有未保存改动，外部变化时避免覆盖用户编辑
            subTab.userData.put("dirty", false);
            codeArea.textProperty().addListener((obs, oldText, newText) -> {
                subTab.userData.put("dirty", !content.equals(newText));
                // 编辑后防抖重算行级 git 标注并重绘，实现「直接编辑实时显示颜色变化」
                scheduleGitColorRefresh(subTab, codeArea, lineStatusRef, filePath);
            });
            codeArea.getStyleClass().add("editor-panel__code-area");
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.forPath(filePath);
            highlighter.apply(codeArea, content);
            // 语法高亮完成后为改动行设置段落背景与左侧竖线（gutter 标注）
            applyGitParaStyles(codeArea, lineStatusRef);
            codeArea.moveTo(0);

            codeArea.setOnKeyPressed(e -> {
                if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.S) {
                    e.consume();
                    cancelGitRefreshJob(subTab);
                    saveFileContent(filePath, codeArea.getText());
                    subTab.userData.put("dirty", false);
                }
            });

            VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
            scrollPane.getStyleClass().add("editor-panel__code-scroll");

            fileContent.getChildren().setAll(scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            // 依据项目 Git 状态着色（子 tab 标题 + 代码区状态色）
            applyGitStyleToSubTab(subTab, filePath);

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
     * "+" 按钮回调：弹出文件选择器打开文件
     */
    private void openFileViaDialog() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("打开文件");
        Path wd = resolveWorkingDir();
        if (wd != null) {
            chooser.setInitialDirectory(wd.toFile());
        }
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) {
            showFileContent(selected.toPath());
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
     * 让代码区支持 Ctrl + 滚轮缩放：正文字号与行号联动放大/缩小。
     * 通过内联样式覆盖默认字号（内联优先于样式类），滚轮时重建行号工厂使其同步缩放。
     */
    private void setupCodeAreaZoom(CodeArea codeArea, SimpleDoubleProperty fontSize, AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef) {
        // 用 addEventFilter（capturing 阶段）而非 setOnScroll（bubble 阶段）：
        // 外层 VirtualizedScrollPane 会在 bubble 阶段先 consume 滚轮事件用于滚动，
        // 导致 Ctrl+滚轮无法在 codeArea 上收到。capturing 阶段提前拦截并在 Ctrl 下 consume，
        // 既保证缩放生效，又阻止普通滚动被触发。
        codeArea.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown()) {
                return;
            }
            // 高精度轨道滚轮 deltaY 可能为 0，改用垂直像素总量判断方向
            double deltaY = e.getDeltaY() != 0 ? e.getDeltaY() : e.getTotalDeltaY();
            if (deltaY == 0) {
                return;
            }
            // 向上滚放大，向下滚缩小；步进约 15%
            double factor = deltaY > 0 ? 1.15 : 1 / 1.15;
            double next = Math.round(Math.clamp(fontSize.get() * factor, 8.0, 48.0) * 100.0) / 100.0;
            if (next == fontSize.get()) {
                return;
            }
            fontSize.set(next);
            codeArea.setUserData(next);
            codeArea.setStyle("-fx-font-size: " + next + "px;");
            applyGitGutter(codeArea, lineStatusRef, next);
            // 缩放已完成，阻止事件继续被滚动容器消费
            e.consume();
        });
    }

    /**
     * 设置代码区行号工厂：在标准行号基础上，为 Git 改动行追加状态修饰类。
     * 行状态引用可被外部替换后通过重设工厂刷新（RichTextFX 重设工厂会重建可见行图形）。
     */
    private void applyGitGutter(CodeArea codeArea, AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef, double fontSize) {
        IntFunction<Node> factory = idx -> {
            Label label = new Label(String.valueOf(idx + 1));
            // 行号字号随正文缩放联动（保持比正文小 1px），内联样式覆盖 .lineno 的默认值
            label.setStyle("-fx-font-size: " + (fontSize - 1) + "px;");
            label.getStyleClass().addAll("lineno", "git-lineno");
            GitFileStatus st = lineStatusRef.get().get(idx);
            // 新增/未跟踪行 → 绿色；修改行 → 蓝色；删除做锚定的行 → 蓝色
            if (st == GitFileStatus.ADDED) {
                label.getStyleClass().add("git-lineno--added");
            } else if (st == GitFileStatus.MODIFIED) {
                label.getStyleClass().add("git-lineno--modified");
            }
            label.setAlignment(Pos.CENTER_RIGHT);
            // 固定行号单元格宽度，避免行号随位数"一位窄、多位宽"跳动。
            // 宽度 = 等宽字符宽(约 0.62×字号) × 最大位数 + 左右留白，随缩放联动。
            int paraCount = codeArea.getParagraphs().size();
            int maxDigits = Math.max(1, String.valueOf(Math.max(1, paraCount)).length());
            double cellWidth = maxDigits * (fontSize - 1) * 0.62 + 24;
            label.setPrefWidth(cellWidth);
            label.setMinWidth(cellWidth);
            label.setMaxWidth(cellWidth);
            return label;
        };
        codeArea.setParagraphGraphicFactory(factory);
    }

    /**
     * 为 Git 改动行设置段落样式：整行浅色背景 + 左侧彩色竖线（gutter 标注）。
     * 与 {@link #applyGitGutter} 的行号着色配合，使变化行在代码区直观可见。
     */
    private void applyGitParaStyles(CodeArea codeArea, AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef) {
        int paraCount = codeArea.getParagraphs().size();
        Map<Integer, GitFileStatus> map = lineStatusRef.get();
        for (int i = 0; i < paraCount; i++) {
            GitFileStatus st = map.get(i);
            String style = null;
            if (st == GitFileStatus.ADDED) {
                style = "git-para--added";
            } else if (st == GitFileStatus.MODIFIED) {
                style = "git-para--modified";
            }
            codeArea.setParagraphStyle(i, style == null ? List.of() : List.of(style));
        }
    }

    /**
     * 编辑器内容变化后防抖调度「实时 git 标注刷新」：
     * <p>停止输入 {@link #GIT_REFRESH_DEBOUNCE_MS} 后，用内存编辑文本相对 HEAD 重新计算行级改动
     * （后台线程），随后在 FX 线程更新 {@code lineStatusRef} 并重绘行号、段落背景与 tab 标题色。
     * <p>同一 sub tab 的待执行刷新任务可被后续编辑取消（防抖合并），避免高频输入触发频繁 diff。
     */
    private void scheduleGitColorRefresh(SubTabContainer.SubTab subTab, CodeArea codeArea,
                                         AtomicReference<Map<Integer, GitFileStatus>> lineStatusRef, Path filePath) {
        Object prior = subTab.userData.get("gitRefreshJob");
        if (prior instanceof ScheduledFuture<?> pf) {
            pf.cancel(false);
        }
        ScheduledFuture<?> job = gitRefreshExecutor.schedule(() -> {
            if (subTab.userData.get("gitRefreshJob") == null) {
                return;
            }
            Map<Integer, GitFileStatus> statusMap = gitStatusService.diffLineStatus(
                    projectStatusStore.getProjectRoot(), filePath, codeArea.getText());
            Map<Integer, GitFileStatus> fresh = statusMap.isEmpty() ? Map.of() : statusMap;
            Platform.runLater(() -> {
                if (subTab.userData.get("gitRefreshJob") == null) {
                    return;
                }
                lineStatusRef.set(fresh);
                double size = codeArea.getUserData() instanceof Number num ? num.doubleValue() : 12.0;
                applyGitGutter(codeArea, lineStatusRef, size);
                applyGitParaStyles(codeArea, lineStatusRef);
                applyGitStyleToSubTab(subTab, filePath);
                subTab.userData.remove("gitRefreshJob");
            });
        }, GIT_REFRESH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        subTab.userData.put("gitRefreshJob", job);
    }

    /**
     * 取消指定文件子 tab 尚未执行的实时 git 标注刷新任务（关闭 tab / 保存前调用）。
     */
    private void cancelGitRefreshJob(SubTabContainer.SubTab subTab) {
        Object pending = subTab.userData.get("gitRefreshJob");
        if (pending instanceof ScheduledFuture<?> pf) {
            pf.cancel(false);
        }
        subTab.userData.remove("gitRefreshJob");
    }



    /**
     * 根据项目 Git 状态为已打开的文件子 tab 标题着色，并在状态变化时同步刷新。
     * 代码区改为按行（行号处）着色，由 {@link #applyGitGutter} 处理。
     */
    private void applyGitStyleToSubTab(SubTabContainer.SubTab subTab, Path filePath) {
        if (subTab == null) {
            return;
        }
        GitFileStatus st = projectStatusStore.statusOf(filePath);
        // 状态样式类名与变色（仅子 tab 标题）
        String gitClass = null;
        if (st != null) {
            gitClass = switch (st) {
                case ADDED -> "editor-panel__sub-tab--git-added";
                case MODIFIED -> "editor-panel__sub-tab--git-modified";
                case UNTRACKED -> "editor-panel__sub-tab--git-untracked";
            };
        }
        subTab.header.getStyleClass().removeAll(
                "editor-panel__sub-tab--git-added", "editor-panel__sub-tab--git-modified", "editor-panel__sub-tab--git-untracked");
        if (gitClass != null) {
            subTab.header.getStyleClass().add(gitClass);
        }
    }

    /**
     * 订阅 Git 状态刷新信号：对已打开文件子 tab 重新着色（并重读无未保存改动的文件内容）。
     */
    private void subscribeStatusRefresh() {
        if (refreshSubscribed) {
            return;
        }
        refreshSubscribed = true;
        projectStatusStore.refreshSignal.addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> {
                    EditorTab fileTab = findTabByType(ViewType.FILE);
                    if (fileTab == null) return;
                    SubTabContainer container = (SubTabContainer) fileTab.userData.get("container");
                    if (container == null) return;

                    for (SubTabContainer.SubTab subTab : container.getSubTabs()) {
                        String pathStr = (String) subTab.userData.get("path");
                        if (pathStr == null) continue;
                        Path p = Path.of(pathStr);
                        // 无未保存改动时重读文件内容，确保随外部变化更新
                        if (Boolean.FALSE.equals(subTab.userData.get("dirty")) && Files.isRegularFile(p)) {
                            // 重新计算行级改动并更新行号着色引用
                            if (subTab.userData.get("lineStatus") instanceof AtomicReference<?> ref) {
                                @SuppressWarnings("unchecked")
                                AtomicReference<Map<Integer, GitFileStatus>> lineRef =
                                        (AtomicReference<Map<Integer, GitFileStatus>>) ref;
                                lineRef.set(computeLineStatus(p));
                            }
                            try {
                                String fresh = Files.readString(p);
                                if (subTab.content instanceof VBox vbox) {
                                    vbox.lookupAll(".editor-panel__code-area").forEach(n -> {
                                        if (n instanceof CodeArea ca) {
                                            if (subTab.userData.get("lineStatus") instanceof AtomicReference<?> lineRef) {
                                                @SuppressWarnings("unchecked")
                                                AtomicReference<Map<Integer, GitFileStatus>> lr =
                                                        (AtomicReference<Map<Integer, GitFileStatus>>) lineRef;
                                                // 内容变化时先同步文本，再统一刷新行号着色与段落 gutter 标注（保持用户缩放字号）
                                                if (!ca.getText().equals(fresh)) {
                                                    ca.replaceText(fresh);
                                                }
                                                double size = ca.getUserData() instanceof Number num ? num.doubleValue() : 12.0;
                                                applyGitGutter(ca, lr, size);
                                                applyGitParaStyles(ca, lr);
                                            }
                                        }
                                    });
                                }
                            } catch (IOException e) {
                                log.warn("重新读取文件失败: {}", p, e);
                            }
                        }
                        applyGitStyleToSubTab(subTab, p);
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

    /** 应用关闭时释放编辑器实时 git 标注调度线程 */
    @PreDestroy
    public void destroy() {
        gitRefreshExecutor.shutdownNow();
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
        if (end > 0 && text.charAt(end - 1) == '\n') {
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
