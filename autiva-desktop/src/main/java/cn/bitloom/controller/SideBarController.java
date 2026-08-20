package cn.bitloom.controller;

import cn.bitloom.agentic.event.AbstractEvent;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.project.FileTreeCell;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.project.FileTreeService;
import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.ProjectRegistry;
import cn.bitloom.project.git.GitStatusService;
import cn.bitloom.project.git.ProjectFileWatcherService;
import cn.bitloom.project.git.ProjectStatusStore;
import cn.bitloom.router.RouteConfig;
import cn.bitloom.store.Store;
import cn.bitloom.vm.AbstractHomePageViewModel;
import cn.bitloom.vm.CodeHomePageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SideBarController implements Initializable, PageHolder {

    private static final String ACTIVE_CSS_CLASS = "sidebar__option--active";
    private static final String HISTORY_ACTIVE_CSS_CLASS = "sidebar__history-item--active";

    /** 运行中圆点的闪烁动画（全量重建列表前停止，防止节点移除后动画泄漏） */
    private final List<Animation> statusAnimations = new ArrayList<>();

    private final FileSystemSessionManager fileSystemSessionManager;
    private final ProjectRegistry projectRegistry;
    private final FileTreeService fileTreeService;
    private final ProjectStatusStore projectStatusStore;
    private final GitStatusService gitStatusService;
    private final ProjectFileWatcherService projectFileWatcherService;
    private final WindowManager windowManager;
    private Path watchedProjectPath = null;
    private TreeView<Path> currentTreeView = null;
    /** 已构建的目录树缓存（切回会话列表后再次进入时复用，保留展开/选中状态） */
    private TreeView<Path> cachedTreeView = null;
    private String cachedTreeProjectId = null;

    @FXML
    @Getter
    private VBox sideBar;
    @FXML
    private ToggleButton workModeBtn;
    @FXML
    private ToggleButton coderModeBtn;
    @FXML
    private HBox homeOption;
    @FXML
    private HBox agentOption;
    @FXML
    private HBox settingsOption;
    @FXML
    private HBox skillOption;
    @FXML
    private HBox taskOption;
    @FXML
    private VBox historyList;
    @FXML
    private ScrollPane historyScroll;

    @Getter
    @Setter
    private IndexController indexController;
    private Map<String, HBox> routeOptionMap;
    private HBox activeHistoryItem = null;
    private final Map<String, HBox> historyItemMap = new LinkedHashMap<>();

    // 当前展开的目录树所属项目（用于二次点击目录树按钮切换回会话列表）
    private ProjectInfo activeTreeProject = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.hide();

        if (AgentMode.CODE.matches(Store.currentAgent.get())) {
            this.coderModeBtn.setSelected(true);
        } else {
            this.workModeBtn.setSelected(true);
        }

        this.workModeBtn.setOnAction(_ -> switchAgentMode(AgentMode.WORK));
        this.coderModeBtn.setOnAction(_ -> switchAgentMode(AgentMode.CODE));

        // 监听智能体切换：同步模式按钮选中状态并刷新历史列表（按 agentId 过滤）
        Store.currentAgent.addListener((_, _, newVal) -> Platform.runLater(() -> {
            if (AgentMode.CODE.matches(newVal)) {
                if (!coderModeBtn.isSelected()) {
                    coderModeBtn.setSelected(true);
                }
            } else {
                if (!workModeBtn.isSelected()) {
                    workModeBtn.setSelected(true);
                }
            }
            refreshHistoryList();
        }));

        this.routeOptionMap = new LinkedHashMap<>();
        this.routeOptionMap.put(RouteConfig.Path.HOME, this.homeOption);
        this.routeOptionMap.put(RouteConfig.Path.AGENT, this.agentOption);
        this.routeOptionMap.put(RouteConfig.Path.SKILLS, this.skillOption);
        this.routeOptionMap.put(RouteConfig.Path.TASK, this.taskOption);
        this.routeOptionMap.put(RouteConfig.Path.SETTINGS, this.settingsOption);

        this.routeOptionMap.forEach((path, option) -> {
            if (option == this.homeOption) {
                return;
            }
            option.setOnMouseClicked(_ -> {
                switch (path) {
                    case RouteConfig.Path.AGENT -> openAgentDialog();
                    case RouteConfig.Path.SKILLS -> openSkillDialog();
                    case RouteConfig.Path.TASK -> openTaskDialog();
                    case RouteConfig.Path.SETTINGS -> openSettingsDialog();
                    default -> {
                        if (this.indexController != null) {
                            this.indexController.navigate(path);
                        }
                    }
                }
            });
        });

        this.homeOption.setOnMouseClicked(_ -> {
            AbstractHomePageViewModel vm = this.currentViewModel();
            if (vm != null) {
                vm.createNewSession();
            }
            this.resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });

        // 监听 session 切换，刷新历史列表
        Store.currentSessionId.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        // 监听刷新信号（聊天过程中触发标题更新）
        Store.refreshHistory.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(this::refreshHistoryList);
        });

        // 监听文件变化刷新信号：仅注册一次，文件变化时重建目录树（仅在树可见时生效）
        projectStatusStore.refreshSignal.addListener((obs, oldVal, newVal) -> {
            if (watchedProjectPath == null || currentTreeView == null) {
                return;
            }
            Platform.runLater(this::refreshProjectTree);
        });

        refreshHistoryList();
    }

    private void resetChatUI() {
        AbstractHomePageController homeController = this.indexController != null
                ? this.indexController.getHomePageController() : null;
        if (homeController != null) {
            homeController.resetForNewSession();
        }
    }

    private void openAgentDialog() {
        showViewDialog("cn/bitloom/view/AgentPage.fxml",
                c -> { if (c instanceof AgentPageController ctr) ctr.refresh(); });
    }

    private void openSkillDialog() {
        showViewDialog("cn/bitloom/view/SkillPage.fxml",
                c -> { if (c instanceof SkillPageController ctr) ctr.refresh(); });
    }

    private void openTaskDialog() {
        showViewDialog("cn/bitloom/view/TaskPage.fxml",
                c -> { if (c instanceof TaskPageController ctr) ctr.refresh(); });
    }

    private void openSettingsDialog() {
        showViewDialog("cn/bitloom/view/SettingsPage.fxml",
                c -> { if (c instanceof SettingsPageController ctr) ctr.reload(); });
    }

    private void showViewDialog(String fxml, java.util.function.Consumer<Object> initializer) {
        Window owner = this.sideBar != null && this.sideBar.getScene() != null
                ? this.sideBar.getScene().getWindow() : null;
        windowManager.showDialog(fxml, owner, initializer);
    }

    /**
     * 获取当前活跃的首页 viewModel（coder 或 work）
     */
    private AbstractHomePageViewModel currentViewModel() {
        if (this.indexController != null && this.indexController.getHomePageController() != null) {
            return this.indexController.getHomePageController().getViewModel();
        }
        return null;
    }

    /**
     * 切换智能体模式：切换 agent、重置聊天 UI 并导航回首页。
     */
    private void switchAgentMode(AgentMode mode) {
        showHistoryList();   // 切换模式时恢复会话列表视图
        AbstractHomePageViewModel vm = this.currentViewModel();
        if (vm != null) {
            vm.switchAgent(mode.agentId());
        }
        this.resetChatUI();
        if (this.indexController != null) {
            this.indexController.navigate(RouteConfig.Path.HOME);
        }
    }

    public void refreshHistoryList() {
        // 卡片重建会生成新的 header/treeBtn 实例，重置目录树切换状态
        this.activeTreeProject = null;
        // 停止旧节点的闪烁动画（节点即将被移除，无限循环动画需显式停止）
        this.statusAnimations.forEach(Animation::stop);
        this.statusAnimations.clear();

        String currentAgent = Store.currentAgent.get();
        boolean isCoder = AgentMode.CODE.matches(currentAgent);
        String prefix = isCoder ? "code-" : "work-";
        List<Session> sessions = fileSystemSessionManager.findByUserId(Store.userId.get()).stream()
                .filter(s -> s.id().startsWith(prefix))
                .sorted((a, b) -> {
                    Object aUpd = a.metadata().get("updateAt");
                    Object bUpd = b.metadata().get("updateAt");
                    long aTime = aUpd instanceof Number n ? n.longValue() : (a.createdAt() != null ? a.createdAt().toEpochMilli() : 0L);
                    long bTime = bUpd instanceof Number m ? m.longValue() : (b.createdAt() != null ? b.createdAt().toEpochMilli() : 0L);
                    return Long.compare(bTime, aTime);
                })
                .toList();
        String currentSessionId = Store.currentSessionId.get();

        // 全量重建
        historyList.getChildren().clear();
        historyItemMap.clear();
        activeHistoryItem = null;

        if (isCoder) {
            // code 模式：按项目分组
            renderProjectGroupedHistory(sessions, currentSessionId);
        } else {
            // work 模式：平铺
            for (Session session : sessions) {
                HBox item = createHistoryItem(session);
                historyItemMap.put(session.id(), item);
                historyList.getChildren().add(item);
            }
        }

        // 更新高亮
        for (Map.Entry<String, HBox> entry : historyItemMap.entrySet()) {
            HBox item = entry.getValue();
            if (entry.getKey().equals(currentSessionId)) {
                if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                    item.getStyleClass().add(HISTORY_ACTIVE_CSS_CLASS);
                }
                item.setStyle(null);
                activeHistoryItem = item;
            } else {
                item.getStyleClass().remove(HISTORY_ACTIVE_CSS_CLASS);
                item.setStyle("-fx-background-color: transparent;");
            }
        }
    }

    /**
     * code 模式：按项目分组渲染历史对话。
     * 每个项目是一个折叠卡片，展开后显示该项目下的 session 列表。
     *
     * @param currentSessionId 当前活跃会话 id，含该会话的项目默认展开，其余项目默认折叠
     */
    private void renderProjectGroupedHistory(List<Session> sessions, String currentSessionId) {
        List<ProjectInfo> projects = projectRegistry.listProjects();
        if (projects.isEmpty()) {
            return;
        }

        for (ProjectInfo project : projects) {
            List<Session> projectSessions = sessions.stream()
                    .filter(s -> project.id().equals(s.metadata().get("projectId")))
                    .toList();

            VBox projectCard = createProjectCard(project, projectSessions, currentSessionId);
            historyList.getChildren().add(projectCard);
        }
    }

    /**
     * 创建项目折叠卡片。
     */
    private VBox createProjectCard(ProjectInfo project, List<Session> projectSessions, String currentSessionId) {
        VBox card = new VBox();
        card.getStyleClass().add("sidebar__project-card");

        // 项目标题行
        HBox header = new HBox();
        header.getStyleClass().add("sidebar__project-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        // 文件夹图标
        SvgImageView folderIcon = new SvgImageView();
        folderIcon.setFitWidth(16);
        folderIcon.setFitHeight(16);
        folderIcon.setSvgPath("/cn/bitloom/images/folder.svg");
        folderIcon.getStyleClass().add("sidebar__project-icon");

        Label nameLabel = new Label(project.name());
        nameLabel.getStyleClass().add("sidebar__project-name");

        // 弹性占位，把按钮推到最右
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 新建对话按钮（与上方新对话图标一致，默认隐藏，悬浮显示）
        Button newChatBtn = new Button();
        newChatBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView newChatIcon = new SvgImageView();
        newChatIcon.setFitWidth(14);
        newChatIcon.setFitHeight(14);
        newChatIcon.setSvgPath("/cn/bitloom/images/chat-new.svg");
        newChatBtn.setGraphic(newChatIcon);
        newChatBtn.setVisible(false);
        newChatBtn.setOnAction(e -> {
            e.consume();
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm instanceof CodeHomePageViewModel coderVm) {
                coderVm.setCurrentProject(project);
            }
            if (vm != null) vm.createNewSession();
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });

        // 目录按钮：点击后会话区切换为该项目的目录树（默认隐藏，悬浮显示）
        Button treeBtn = new Button();
        treeBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView treeIcon = new SvgImageView();
        treeIcon.setFitWidth(14);
        treeIcon.setFitHeight(14);
        treeIcon.setSvgPath("/cn/bitloom/images/file-tree.svg");
        treeBtn.setGraphic(treeIcon);
        treeBtn.setVisible(false);
        treeBtn.setOnAction(e -> {
            e.consume();
            // 二次点击同一项目：退出目录树，返回会话列表
            if (activeTreeProject != null && activeTreeProject.id().equals(project.id())) {
                showHistoryList();
            } else {
                activeTreeProject = project;
                showProjectTree(project);
            }
        });

        header.getChildren().addAll(folderIcon, nameLabel, spacer, treeBtn, newChatBtn);

        header.setOnMouseEntered(e -> { newChatBtn.setVisible(true); treeBtn.setVisible(true); });
        header.setOnMouseExited(e -> { newChatBtn.setVisible(false); treeBtn.setVisible(false); });

        // session 列表容器（默认折叠；仅当前活跃会话所属项目默认为展开）
        VBox sessionList = new VBox();
        sessionList.getStyleClass().add("sidebar__project-sessions");
        boolean isActiveProject = currentSessionId != null
                && projectSessions.stream().anyMatch(s -> s.id().equals(currentSessionId));
        sessionList.setVisible(isActiveProject);
        sessionList.setManaged(isActiveProject);

        for (Session session : projectSessions) {
            HBox item = createHistoryItem(session);
            historyItemMap.put(session.id(), item);
            sessionList.getChildren().add(item);
        }

        // 点击项目名展开/折叠（按钮点击不触发）
        header.setOnMouseClicked(e -> {
            if (e.getTarget() == newChatBtn || e.getTarget() == newChatIcon
                    || e.getTarget() == treeBtn || e.getTarget() == treeIcon) {
                return;
            }
            boolean expanded = sessionList.isVisible();
            sessionList.setVisible(!expanded);
            sessionList.setManaged(!expanded);
        });

        card.getChildren().addAll(header, sessionList);
        return card;
    }

    /**
     * 将会话记录区域切换为项目目录树。
     * 顶部复用项目卡片头部同款样式与按钮（文件夹图标 + 项目名 + 目录树/新建按钮），
     * 复用 FileTreeService 构建懒加载目录树。
     * 无单独返回按钮：再次点击头部目录树按钮（激活态）即可切回会话列表。
     */
    private void showProjectTree(ProjectInfo project) {
        Path projectPath = Paths.get(project.path());
        this.watchedProjectPath = projectPath.toAbsolutePath().normalize();

        // 先更新 Git 状态：此时 currentTreeView 尚为 null，refreshSignal 不会触发整树重建，
        // 避免打开目录树后立刻 setRoot 重建导致的闪烁；树渲染时单元格直接读取新状态着色。
        projectStatusStore.update(projectPath, gitStatusService.queryStatusMap(projectPath));

        TreeView<Path> treeView;
        if (cachedTreeView != null && cachedTreeProjectId != null && cachedTreeProjectId.equals(project.id())) {
            // 复用已构建的目录树，保留之前展开/选中的节点状态
            treeView = cachedTreeView;
            // 单元格仍是旧状态着色：refresh() 仅强制单元格重绘（updateItem 重跑），
            // 不重建树结构，展开/选中/滚动状态全部保留
            treeView.refresh();
        } else {
            treeView = buildNewTreeView();
            cachedTreeView = treeView;
            cachedTreeProjectId = project.id();
        }
        this.currentTreeView = treeView;

        // 启动文件监听，文件变化时自动刷新 Git 状态与目录树/文件视图
        projectFileWatcherService.watch(projectPath);

        // 顶部头部：与项目卡片 header 样式完全一致（文件夹图标 + 项目名 + 悬浮按钮）
        HBox treeHeader = createProjectHeader(project, true);

        // 目录树容器：顶部头部 + 下方目录树
        VBox treeContainer = new VBox(treeHeader, treeView);
        treeContainer.getStyleClass().add("sidebar__tree-container");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // 目录树视图需要撑满 ScrollPane 高度
        historyScroll.setFitToHeight(true);
        historyScroll.setContent(treeContainer);
    }

    /** 新建一棵懒加载目录树并挂接 Git 刷新信号监听。 */
    private TreeView<Path> buildNewTreeView() {
        TreeView<Path> treeView = new TreeView<>();
        FileTreeCell.setStatusStore(projectStatusStore);
        treeView.setCellFactory(t -> new FileTreeCell());
        treeView.setShowRoot(false);
        // 双击文件在右侧编辑器面板展示内容
        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && Files.isRegularFile(selected.getValue())
                        && indexController != null) {
                    indexController.showFileInPanel(selected.getValue());
                }
            }
        });
        try {
            TreeItem<Path> root = fileTreeService.buildFileTree(watchedProjectPath);
            treeView.setRoot(root);
        } catch (Exception e) {
            log.error("构建侧边栏目录树失败: {}", watchedProjectPath, e);
        }
        return treeView;
    }

    /**
     * 依据当前 Git 状态重建目录树，使新增/删除/修改的文件立即反映并正确着色。
     */
    private void refreshProjectTree() {
        if (watchedProjectPath == null || currentTreeView == null) {
            return;
        }
        try {
            // 记录刷新前已展开节点的绝对路径，重建后恢复展开状态
            Set<Path> expandedPaths = new HashSet<>();
            collectExpandedPaths(currentTreeView.getRoot(), expandedPaths);

            Path selectedValue = null;
            TreeItem<Path> selected = currentTreeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedValue = selected.getValue();
            }
            TreeItem<Path> newRoot = fileTreeService.buildFileTree(watchedProjectPath);
            currentTreeView.setRoot(newRoot);
            // 恢复展开状态（懒加载节点 setExpanded(true) 会触发逐层加载）
            expandPaths(newRoot, expandedPaths);
            // 恢复选中：在已加载的树节点中查找对应路径
            if (selectedValue != null) {
                TreeItem<Path> target = findTreeItem(newRoot, selectedValue.toAbsolutePath().normalize());
                if (target != null) {
                    currentTreeView.getSelectionModel().select(target);
                }
            }
        } catch (Exception e) {
            log.warn("刷新目录树失败: {}", watchedProjectPath, e);
        }
    }

    /** 递归收集当前树中所有已展开节点的绝对路径。 */
    private void collectExpandedPaths(TreeItem<Path> node, Set<Path> acc) {
        if (node == null || node.getValue() == null) {
            return;
        }
        if (node.isExpanded()) {
            acc.add(node.getValue().toAbsolutePath().normalize());
        }
        for (TreeItem<Path> child : node.getChildren()) {
            collectExpandedPaths(child, acc);
        }
    }

    /**
     * 在重建后的树中逐层恢复展开状态。
     * 惰性加载节点须沿路径逐级定位并 setExpanded(true)（触发子层加载），
     * 以保证深层节点也能被重新展开。
     */
    private void expandPaths(TreeItem<Path> node, Set<Path> allExpanded) {
        if (node == null || node.getValue() == null) {
            return;
        }
        Path abs = node.getValue().toAbsolutePath().normalize();
        if (allExpanded.contains(abs)) {
            node.setExpanded(true);
        }
        for (TreeItem<Path> child : node.getChildren()) {
            expandPaths(child, allExpanded);
        }
    }

    /** 在树节点中递归查找指定绝对路径的 TreeItem（仅覆盖已加载节点）。 */
    private TreeItem<Path> findTreeItem(TreeItem<Path> node, Path absTarget) {
        if (node == null || node.getValue() == null) {
            return null;
        }
        if (node.getValue().toAbsolutePath().normalize().equals(absTarget)) {
            return node;
        }
        for (TreeItem<Path> child : node.getChildren()) {
            TreeItem<Path> hit = findTreeItem(child, absTarget);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 取消当前 Git 状态监听与已展开目录树（切回会话列表时调用） */
    private void detachProjectTree() {
        projectFileWatcherService.stop();
        currentTreeView = null;
        watchedProjectPath = null;
    }

    /**
     * 构建与项目卡片 header 完全一致样式的头部。
     *
     * @param treeActive 当前是否处于目录树视图；true 时目录树按钮呈激活态（点击返回会话列表），
     *                   否则与卡片一致（点击进入目录树）
     */
    private HBox createProjectHeader(ProjectInfo project, boolean treeActive) {
        HBox header = new HBox();
        header.getStyleClass().add("sidebar__project-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        // 文件夹图标（与卡片一致）
        SvgImageView folderIcon = new SvgImageView();
        folderIcon.setFitWidth(16);
        folderIcon.setFitHeight(16);
        folderIcon.setSvgPath("/cn/bitloom/images/folder.svg");
        folderIcon.getStyleClass().add("sidebar__project-icon");

        Label nameLabel = new Label(project.name());
        nameLabel.getStyleClass().add("sidebar__project-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 目录树按钮（激活态时点击返回会话列表；否则点击进入目录树）
        Button treeBtn = new Button();
        treeBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView treeIcon = new SvgImageView();
        treeIcon.setFitWidth(14);
        treeIcon.setFitHeight(14);
        treeIcon.setSvgPath("/cn/bitloom/images/file-tree.svg");
        treeBtn.setGraphic(treeIcon);
        treeBtn.setVisible(false);
        if (treeActive) {
            // 仅切换行为为返回会话列表，不保持选中高亮态
            treeBtn.setOnAction(e -> showHistoryList());
        } else {
            treeBtn.setOnAction(e -> {
                e.consume();
                activeTreeProject = project;
                showProjectTree(project);
            });
        }

        // 新建对话按钮（悬浮显示，行为与卡片一致）
        Button newChatBtn = new Button();
        newChatBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView newChatIcon = new SvgImageView();
        newChatIcon.setFitWidth(14);
        newChatIcon.setFitHeight(14);
        newChatIcon.setSvgPath("/cn/bitloom/images/chat-new.svg");
        newChatBtn.setGraphic(newChatIcon);
        newChatBtn.setVisible(false);
        newChatBtn.setOnAction(e -> {
            e.consume();
            AbstractHomePageViewModel vm = currentViewModel();
            if (vm instanceof CodeHomePageViewModel coderVm) {
                coderVm.setCurrentProject(project);
            }
            if (vm != null) vm.createNewSession();
            resetChatUI();
            if (this.indexController != null) {
                this.indexController.navigate(RouteConfig.Path.HOME);
            }
        });

        header.getChildren().addAll(folderIcon, nameLabel, spacer, treeBtn, newChatBtn);

        header.setOnMouseEntered(e -> { newChatBtn.setVisible(true); treeBtn.setVisible(true); });
        header.setOnMouseExited(e -> {
            newChatBtn.setVisible(false);
            treeBtn.setVisible(false);
        });

        return header;
    }

    /**
     * 恢复会话记录区域为会话列表。
     */
    private void showHistoryList() {
        activeTreeProject = null;
        detachProjectTree();
        historyScroll.setFitToHeight(false);
        historyScroll.setContent(historyList);
    }

    /**
     * 获取 session 标题：优先从 metadata 取，没有则从 events.jsonl 提取第一条 USER 消息并持久化。
     */
    private String resolveSessionTitle(Session session) {
        Object titleObj = session.metadata().get("title");
        String title = titleObj != null ? titleObj.toString() : "新对话";
        if (!"新对话".equals(title)) {
            return title;
        }
        List<AbstractEvent> events = fileSystemSessionManager.getEvents(session.id(),
                EventFilter.builder().page(0).pageSize(10).build());
        for (AbstractEvent event : events) {
            if (event instanceof MessageEvent me && me.isUserMessage()
                    && me.getText() != null && !me.getText().isBlank()) {
                String text = me.getText().replace("\n", " ").trim();
                String newTitle = text.length() > 20 ? text.substring(0, 20) + "..." : text;
                Map<String, Object> md = new HashMap<>(session.metadata());
                md.put("title", newTitle);
                Session updated = Session.builder()
                        .id(session.id())
                        .userId(session.userId())
                        .createdAt(session.createdAt())
                        .metadata(md)
                        .build();
                fileSystemSessionManager.persistSession(updated);
                return newTitle;
            }
        }
        return title;
    }

    private HBox createHistoryItem(Session session) {
        HBox item = new HBox();
        item.getStyleClass().add("sidebar__history-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setSpacing(4);
        // 子节点各自使用自身高度，按 item 的 center-left 对齐，
        // 避免 fillHeight 拉伸导致状态点/文字/按钮中心线不齐
        item.setFillHeight(false);

        VBox textContainer = new VBox();
        textContainer.getStyleClass().add("sidebar__history-text");
        textContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        Label titleLabel = new Label(resolveSessionTitle(session));
        titleLabel.getStyleClass().add("sidebar__history-item-title");

        textContainer.getChildren().add(titleLabel);

        // 删除按钮
        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().add("sidebar__history-delete-btn");
        SvgImageView deleteIcon = new SvgImageView();
        deleteIcon.setFitWidth(14);
        deleteIcon.setFitHeight(14);
        deleteIcon.setSvgPath("/cn/bitloom/images/trash.svg");
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.setVisible(false);

        deleteBtn.setOnAction(e -> {
            e.consume();
            String sessionId = session.id();
            fileSystemSessionManager.remove(sessionId);

            String currentId = Store.currentSessionId.get();
            if (sessionId.equals(currentId)) {
                // 删除的是当前会话，切换到初始态
                AbstractHomePageViewModel vm = currentViewModel();
                if (vm != null) vm.createNewSession();
                resetChatUI();
            }
            refreshHistoryList();
        });

        item.setOnMouseEntered(event -> {
            if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                item.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05);");
            }
            deleteBtn.setVisible(true);
        });
        item.setOnMouseExited(event -> {
            if (!item.getStyleClass().contains(HISTORY_ACTIVE_CSS_CLASS)) {
                item.setStyle("-fx-background-color: transparent;");
            }
            deleteBtn.setVisible(false);
        });

        item.getChildren().addAll(createStatusIndicator(session), textContainer, deleteBtn);

        item.setOnMouseClicked(event -> {
            if (event.getTarget() != deleteBtn && event.getTarget() != deleteIcon) {
                AbstractHomePageViewModel vm = currentViewModel();
                if (vm != null) vm.switchToSession(session.id());
                resetChatUI();
                if (this.indexController != null) {
                    this.indexController.navigate(RouteConfig.Path.HOME);
                }
                updateHistoryActiveState(item);
            }
        });

        return item;
    }

    /**
     * session 运行状态圆点（标题前方，固定尺寸保证标题对齐）：
     * 空闲/已停止 = 灰边空心圆；执行中 = 灰色实心圆闪烁（FadeTransition 驱动）。
     */
    private Region createStatusIndicator(Session session) {
        Region dot = new Region();
        dot.getStyleClass().add("sidebar__status-dot");
        // margin 隔距（padding 会扩大实心背景绘制区域导致胶囊形）
        HBox.setMargin(dot, new Insets(0, 6, 0, 2));

        AbstractHomePageViewModel vm = currentViewModel();
        if (vm != null
                && vm.getSessionStatus(session.id()) == AbstractHomePageViewModel.SessionStatus.RUNNING) {
            dot.getStyleClass().add("sidebar__status-dot--running");
            FadeTransition blink = new FadeTransition(Duration.millis(900), dot);
            blink.setFromValue(1.0);
            blink.setToValue(0.25);
            blink.setAutoReverse(true);
            blink.setCycleCount(FadeTransition.INDEFINITE);
            blink.play();
            statusAnimations.add(blink);
        }
        return dot;
    }

    private void updateHistoryActiveState(HBox newActive) {
        if (activeHistoryItem != null) {
            activeHistoryItem.getStyleClass().remove(HISTORY_ACTIVE_CSS_CLASS);
            activeHistoryItem.setStyle("-fx-background-color: transparent;");
        }
        newActive.getStyleClass().add(HISTORY_ACTIVE_CSS_CLASS);
        newActive.setStyle(null);
        activeHistoryItem = newActive;
    }

    public void updateActiveState(String path) {
        this.routeOptionMap.values().forEach(option ->
                option.getStyleClass().remove(ACTIVE_CSS_CLASS));

        // homeOption 是"新建对话"动作按钮，不参与页面选中高亮
        if (RouteConfig.Path.HOME.equals(path)) {
            return;
        }

        HBox activeOption = this.routeOptionMap.get(path);
        if (activeOption != null) {
            activeOption.getStyleClass().add(ACTIVE_CSS_CLASS);
        }
    }

    @Override
    public void show() {
        this.sideBar.setVisible(true);
        this.sideBar.setManaged(true);
    }

    @Override
    public void hide() {
        this.sideBar.setVisible(false);
        this.sideBar.setManaged(false);
    }

    public boolean isSidebarVisible() {
        return this.sideBar != null && this.sideBar.isVisible();
    }

}
