package cn.bitloom.controller;

import cn.bitloom.agentic.diff.DiffService;
import cn.bitloom.agentic.diff.FileDiff;
import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.project.ProjectInfo;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.message.AssistantMessageCard;
import cn.bitloom.node.message.MessageCard;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.node.AutoResizeTextArea;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.node.tool.TaskCard;
import cn.bitloom.node.tool.TodoCard;
import cn.bitloom.store.Store;
import cn.bitloom.vm.HomePageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    private VBox homePage;
    @FXML
    private VBox sendBox;
    @FXML
    private AutoResizeTextArea sendField;
    @FXML
    private Button sendButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button addFileButton;
    @FXML
    private Button canvasButton;
    @FXML
    private FlowPane fileTagsPane;
    @FXML
    private ScrollPane fileTagsScroll;
    @FXML
    private VBox icon;
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatContainer;
    @FXML
    private VBox diffReviewBar;
    @FXML
    private HBox diffReviewHeader;
    @FXML
    private Label diffReviewCount;
    @FXML
    private Button diffReviewRejectAllBtn;
    @FXML
    private Button diffReviewApproveAllBtn;
    @FXML
    private ScrollPane diffReviewListScroll;
    @FXML
    private VBox diffReviewList;
    @FXML
    private MenuButton projectSelectButton;
    @FXML
    private Button branchDisplayButton;

    private final List<File> attachedFiles = new ArrayList<>();
    private final BooleanProperty shouldScrollToBottom = new SimpleBooleanProperty(false);
    private Disposable diffEventSubscription;
    private boolean diffReviewExpanded = false;
    private final DiffService diffService;

    @Getter
    private final HomePageViewModel viewModel;
    @Getter
    private final ToolUIBridge toolUIBridge;
    @Getter
    private final WindowManager windowManager;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 默认使用 DeepSeek 模型
        Store.selectedModel.set(ModelTypeEnum.DEEPSEEK);

        // 加载 A2UI 样式
        try {
            String a2uiCss = getClass().getResource("/cn/bitloom/style/a2ui.css").toExternalForm();
            homePage.getStylesheets().add(a2uiCss);
        } catch (Exception e) {
            log.warn("Failed to load a2ui.css", e);
        }

        this.sendButton.setOnAction(event -> this.handleSendMessage());
        this.stopButton.setOnAction(event -> this.viewModel.pauseGeneration());

        // Ctrl+Enter 发送消息，Enter 换行
        this.sendField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown() && !event.isControlDown()) {
                event.consume();
                this.handleSendMessage();
            }
        });

        // sendField 使用 AutoResizeTextArea，高度由 computePrefHeight 自动计算，无需手动调整

        // 添加文件按钮
        this.addFileButton.setOnAction(event -> this.handleAddFile());

        // 画布按钮
        this.canvasButton.setOnAction(event -> this.handleOpenCanvas());

        Store.isStreaming.addListener((obs, oldVal, newVal) -> {
            boolean streaming = newVal != null && newVal;
            boolean paused = Store.isPaused.get();
            boolean showSend = !streaming || paused;
            this.sendButton.setVisible(showSend);
            this.sendButton.setManaged(showSend);
            this.stopButton.setVisible(streaming && !paused);
            this.stopButton.setManaged(streaming && !paused);
        });

        Store.isPaused.addListener((obs, oldVal, newVal) -> {
            boolean streaming = Store.isStreaming.get();
            boolean paused = newVal != null && newVal;
            boolean showSend = !streaming || paused;
            this.sendButton.setVisible(showSend);
            this.sendButton.setManaged(showSend);
            this.stopButton.setVisible(streaming && !paused);
            this.stopButton.setManaged(streaming && !paused);
        });

        homePage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addPostLayoutPulseListener(() -> {
                    if (shouldScrollToBottom.get()) {
                        shouldScrollToBottom.set(false);
                        chatScrollPane.setVvalue(1.0);
                    }
                });
            }
        });

        // 智能体切换联动：控制 HomePage sendBox 的项目/分支按钮可见性
        // 智能体切换入口在侧边栏分段按钮（SideBarController），这里仅监听变化并响应
        updateProjectButtonBarVisibility(Store.currentAgent.get());
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> updateProjectButtonBarVisibility(newVal));
        });
        this.setupProjectMenu();
        this.branchDisplayButton.setText("");
        this.viewModel.currentProjectProperty()
                .addListener((obs, oldVal, newVal) -> {
                    refreshBranchDisplay(newVal);
                    refreshProjectMenuText(newVal);
                    if (indexController != null) {
                        indexController.updateCurrentProject(newVal);
                    }
                });

        this.toolUIBridge.setOnNodeAdded(this::addChatNode);

        this.viewModel.getMessages().addListener((ListChangeListener<MessageCard>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (MessageCard card : change.getAddedSubList()) {
                        addChatCard(card);
                    }
                }
            }
            // 有对话消息后锁定智能体和项目选择器（一个 session 只能绑定一个智能体和一个项目）
            updateSelectorLockState(!this.viewModel.getMessages().isEmpty());
        });

        if (this.viewModel.hasHistoricalMessages()) {
            this.animateToChatState();
            this.viewModel.prepareHistoricalMessages();
        }

        // 注册对话框为拖拽目标（接收来自文件树/Diff 列表的文件拖拽）
        this.setupDragDrop();

        // 订阅 DiffEvent：每次发生 diff 时刷新对话框上方的文件卡片条
        this.subscribeDiffEvents();

        // 绑定 diff 审查卡片条按钮事件
        this.diffReviewHeader.setOnMouseClicked(e -> {
            // 点击 header 切换展开/折叠，但排除按钮区域的点击
            if (e.getTarget() instanceof Button || e.getTarget() instanceof Label parentLabel
                    && parentLabel.getParent() instanceof Button) return;
            toggleDiffReviewExpand();
        });
        this.diffReviewHeader.setStyle("-fx-cursor: hand;");
        this.diffReviewRejectAllBtn.setOnAction(e -> handleRejectAllDiff());
        this.diffReviewApproveAllBtn.setOnAction(e -> handleApproveAllDiff());

        // 监听会话切换：切换会话/新建会话时清空 diff 文件卡片条（仅当前会话的 diff 显示）
        Store.currentSessionId.addListener((obs, oldVal, newVal) -> {
            if (oldVal != null && !oldVal.equals(newVal)) {
                diffReviewList.getChildren().clear();
                diffReviewExpanded = false;
                diffReviewListScroll.setVisible(false);
                diffReviewListScroll.setManaged(false);
                diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
                updateDiffReviewBar();
            }
        });
    }



    /**
     * 订阅 DiffEvent，收到事件后直接使用 DiffEvent 中的 FileDiff 追加卡片。
     * diff 数据源为工具（WriteTool/EditTool）调用 generateDiff 发布的事件，
     * 不再扫描 git 工作区（scanWorkingTreeDiffs 仅用于进化功能）。
     */
    private void subscribeDiffEvents() {
        this.diffEventSubscription = EventBus.outBoxFlux()
                .filter(event -> event instanceof DiffEvent)
                .subscribe(event -> Platform.runLater(() -> {
                    DiffEvent diffEvent = (DiffEvent) event;
                    log.info("[DiffUI] 收到 DiffEvent: filePath={}, hunks={}",
                            diffEvent.getDiff().filePath(),
                            diffEvent.getDiff().hunks() != null ? diffEvent.getDiff().hunks().size() : 0);
                    addDiffFileCard(diffEvent.getDiff());
                }));
    }

    /**
     * 追加单个 diff 文件卡片到展开列表，并更新计数。
     * 同一文件被多次修改时，每次 DiffEvent 都生成独立卡片，保留完整修改历史。
     */
    private void addDiffFileCard(FileDiff diff) {
        log.info("[DiffUI] addDiffFileCard: filePath={}, diffReviewList当前子节点数={}",
                diff.filePath(), diffReviewList.getChildren().size());
        diffReviewList.getChildren().add(createDiffFileCard(diff));
        updateDiffReviewBar();
    }

    /**
     * 更新 diff 审查卡片条的计数和可见性。
     * 折叠状态下高度仅显示 header（约36px），不占用聊天区域。
     */
    private void updateDiffReviewBar() {
        int count = diffReviewList.getChildren().size();
        diffReviewCount.setText(count + " 个文件待审查");
        boolean hasDiffs = count > 0;
        diffReviewBar.setVisible(hasDiffs);
        diffReviewBar.setManaged(hasDiffs);
        if (hasDiffs && !diffReviewExpanded) {
            // 折叠状态：仅 header 高度
            diffReviewBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
            diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
        }
        log.info("[DiffUI] updateDiffReviewBar: count={}, diffReviewBar.visible={}",
                count, diffReviewBar.isVisible());
    }

    /**
     * 切换 diff 审查列表的展开/折叠状态。
     * 展开时最大高度限制为280px（与sendBox一致），折叠时仅显示header。
     */
    @FXML
    private void toggleDiffReviewExpand() {
        diffReviewExpanded = !diffReviewExpanded;
        diffReviewListScroll.setVisible(diffReviewExpanded);
        diffReviewListScroll.setManaged(diffReviewExpanded);
        if (diffReviewExpanded) {
            // 展开状态：限制最大高度为280（与sendBox一致）
            diffReviewBar.setMaxHeight(280);
            diffReviewBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
        } else {
            // 折叠状态：仅 header 高度
            diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
            diffReviewBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
        }
    }

    /**
     * 全部撤销：遍历 diffReviewList 中所有 FileDiff，调用 diffService.rejectFileDiff 回滚文件。
     */
    @FXML
    private void handleRejectAllDiff() {
        for (var node : diffReviewList.getChildren()) {
            if (node.getUserData() instanceof FileDiff diff) {
                diffService.rejectFileDiff(diff);
            }
        }
        diffReviewList.getChildren().clear();
        diffReviewExpanded = false;
        diffReviewListScroll.setVisible(false);
        diffReviewListScroll.setManaged(false);
        diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
        updateDiffReviewBar();
    }

    /**
     * 全部保留：遍历 diffReviewList 中所有 FileDiff，调用 diffService.approveFileDiff 保留文件。
     */
    @FXML
    private void handleApproveAllDiff() {
        for (var node : diffReviewList.getChildren()) {
            if (node.getUserData() instanceof FileDiff diff) {
                diffService.approveFileDiff(diff);
            }
        }
        diffReviewList.getChildren().clear();
        diffReviewExpanded = false;
        diffReviewListScroll.setVisible(false);
        diffReviewListScroll.setManaged(false);
        diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
        updateDiffReviewBar();
    }

    /**
     * 创建单个 diff 文件卡片：文件名 + "+N -M" 变更统计 + 单个文件撤销/保留按钮。
     * 点击卡片行（非按钮区域）在项目视图中打开 diff。
     * UserData 存储 FileDiff 以便全部撤销/全部保留时遍历获取。
     */
    private Node createDiffFileCard(FileDiff diff) {
        HBox card = new HBox(6);
        card.getStyleClass().add("home-page__diff-review-file-row");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setUserData(diff);

        // 文件名（仅显示文件名，不带完整路径）
        String filePath = diff.filePath();
        String fileName = filePath;
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSep >= 0 && lastSep < filePath.length() - 1) {
            fileName = filePath.substring(lastSep + 1);
        }
        Label nameLabel = new Label(fileName);
        nameLabel.getStyleClass().add("home-page__diff-review-file-name");
        nameLabel.setTooltip(new Tooltip(filePath));
        card.getChildren().add(nameLabel);

        // 变更统计 "+N -M"
        int[] stats = computeDiffStats(diff);
        Label statsLabel = new Label("+" + stats[0] + " -" + stats[1]);
        statsLabel.getStyleClass().add("home-page__diff-review-file-stats");
        if (stats[0] > 0) {
            statsLabel.getStyleClass().add("home-page__diff-review-file-stats--add");
        }
        if (stats[1] > 0) {
            statsLabel.getStyleClass().add("home-page__diff-review-file-stats--remove");
        }
        card.getChildren().add(statsLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        card.getChildren().add(spacer);

        // 单个文件撤销按钮
        Button rejectBtn = new Button("撤销");
        rejectBtn.getStyleClass().addAll("home-page__diff-review-file-btn", "home-page__diff-review-file-btn--reject");
        rejectBtn.setOnAction(e -> {
            diffService.rejectFileDiff(diff);
            diffReviewList.getChildren().remove(card);
            updateDiffReviewBar();
        });
        card.getChildren().add(rejectBtn);

        // 单个文件保留按钮
        Button approveBtn = new Button("保留");
        approveBtn.getStyleClass().addAll("home-page__diff-review-file-btn", "home-page__diff-review-file-btn--approve");
        approveBtn.setOnAction(e -> {
            diffService.approveFileDiff(diff);
            diffReviewList.getChildren().remove(card);
            updateDiffReviewBar();
        });
        card.getChildren().add(approveBtn);

        // 点击卡片行（非按钮区域）在项目视图中打开 diff
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button) return;
            if (indexController != null) {
                indexController.showDiffInProjectView(diff);
            }
        });

        return card;
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
     * 将 sendBox 注册为拖拽目标，接收来自文件树/Diff 列表的文件拖拽。
     * 拖拽文件释放后作为附件添加（复用 attachedFiles 机制）。
     */
    private void setupDragDrop() {
        sendBox.setOnDragOver(this::handleDragOver);
        sendBox.setOnDragDropped(this::handleDragDropped);
    }

    private void handleDragOver(DragEvent event) {
        if (event.getGestureSource() != sendBox && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        boolean success = false;
        if (event.getDragboard().hasFiles()) {
            for (File file : event.getDragboard().getFiles()) {
                addAttachedFile(file);
            }
            success = true;
            // 拖拽文件仅添加附件，不触发界面状态切换动画（避免 logo 消失）
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private void addChatCard(MessageCard card) {
        if (card.getMessageType() == MessageEvent.Type.TOOL && card instanceof ToolMessageCard toolCard) {
            // 工具调用卡片重定向到 EditorPanel 的工具调用视图（复用 ToolGroupCard 分组逻辑）
            addToolToEditorPanel(toolCard, toolCard.getToolName());
            return;
        }

        // MessageCard 继承 VBox，可以直接绑定宽度
        card.maxWidthProperty().bind(
                Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.75))
        );

        // 创建消息包装容器：卡片 + 操作按钮
        VBox messageWrapper = new VBox();
        messageWrapper.getStyleClass().add("chat-message-wrapper");
        messageWrapper.getChildren().add(card);

        // 创建操作按钮栏
        HBox actionBar = createActionBar(card);
        messageWrapper.getChildren().add(actionBar);

        // 对于助手消息，将 actionBar 传递给卡片内部管理
        if (card instanceof AssistantMessageCard assistantCard) {
            assistantCard.setActionBar(actionBar);
            assistantCard.setOnContentChanged(c -> scrollToBottom());
        }

        HBox row = createMessageRow(messageWrapper, card.getMessageType());
        chatContainer.getChildren().add(row);
        scrollToBottom();
    }

    /**
     * 添加工具卡片到 EditorPanel 的工具调用视图（直接添加，不再分组包裹）
     */
    private void addToolToEditorPanel(Node toolCard, String toolName) {
        if (indexController == null || indexController.getEditorPanelController() == null) return;
        indexController.getEditorPanelController().addToolCallCard(toolCard);
    }

    private void addChatNode(Node node) {
        // TodoCard 重定向到 EditorPanel 的待办视图
        if (node instanceof TodoCard todoCard) {
            if (indexController != null && indexController.getEditorPanelController() != null) {
                indexController.getEditorPanelController().addTodoCard(todoCard);
            }
            return;
        }
        if (node instanceof Region region) {
            region.maxWidthProperty().bind(
                    Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.85))
            );
        }
        if (node instanceof TaskCard taskCard) {
            taskCard.setOnContentChanged(c -> scrollToBottom());
        }
        HBox row = new HBox();
        row.getStyleClass().add("chat-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(node);
        chatContainer.getChildren().add(row);
        scrollToBottom();
    }

    private HBox createMessageRow(Node card, MessageEvent.Type type) {
        HBox row = new HBox();
        row.getStyleClass().add("chat-row");
        row.setMaxWidth(Double.MAX_VALUE);

        if (type == MessageEvent.Type.USER) {
            row.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, card);
        } else {
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(card);
        }

        return row;
    }

    private HBox createActionBar(MessageCard card) {
        HBox actionBar = new HBox();
        actionBar.getStyleClass().add("chat-message__actions");
        if (card.getMessageType() == MessageEvent.Type.USER) {
            actionBar.getStyleClass().add("chat-message__actions--user");
        }

        Button copyBtn = new Button();
        copyBtn.getStyleClass().add("chat-message__action-btn");
        SvgImageView copyIcon = new SvgImageView();
        copyIcon.setFitWidth(14);
        copyIcon.setFitHeight(14);
        copyIcon.setSvgPath("/cn/bitloom/images/copy.svg");
        copyBtn.setGraphic(copyIcon);
        copyBtn.setOnAction(e -> {
            String content = card.getContent();
            if (content != null && !content.isBlank()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(content);
                clipboard.setContent(clipboardContent);
                copyBtn.getStyleClass().add("chat-message__action-btn--copied");
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
                pause.setOnFinished(ev -> copyBtn.getStyleClass().remove("chat-message__action-btn--copied"));
                pause.play();
            }
        });

        Button likeBtn = new Button();
        likeBtn.getStyleClass().add("chat-message__action-btn");
        SvgImageView likeIcon = new SvgImageView();
        likeIcon.setFitWidth(14);
        likeIcon.setFitHeight(14);
        likeIcon.setSvgPath("/cn/bitloom/images/like.svg");
        likeBtn.setGraphic(likeIcon);

        Button dislikeBtn = new Button();
        dislikeBtn.getStyleClass().add("chat-message__action-btn");
        SvgImageView dislikeIcon = new SvgImageView();
        dislikeIcon.setFitWidth(14);
        dislikeIcon.setFitHeight(14);
        dislikeIcon.setSvgPath("/cn/bitloom/images/dislike.svg");
        dislikeBtn.setGraphic(dislikeIcon);

        likeBtn.setOnAction(e -> {
            if (likeBtn.getStyleClass().contains("chat-message__action-btn--liked")) {
                likeBtn.getStyleClass().remove("chat-message__action-btn--liked");
            } else {
                likeBtn.getStyleClass().add("chat-message__action-btn--liked");
                dislikeBtn.getStyleClass().remove("chat-message__action-btn--disliked");
            }
        });

        dislikeBtn.setOnAction(e -> {
            if (dislikeBtn.getStyleClass().contains("chat-message__action-btn--disliked")) {
                dislikeBtn.getStyleClass().remove("chat-message__action-btn--disliked");
            } else {
                dislikeBtn.getStyleClass().add("chat-message__action-btn--disliked");
                likeBtn.getStyleClass().remove("chat-message__action-btn--liked");
            }
        });

        actionBar.getChildren().addAll(copyBtn, likeBtn, dislikeBtn);
        return actionBar;
    }

    private void handleSendMessage() {
        if (this.sendField.getText().isBlank() && this.attachedFiles.isEmpty()) {
            return;
        }
        String text = this.sendField.getText();
        if (!this.chatScrollPane.isVisible()) {
            this.animateToChatState();
        }
        List<String> filePaths = this.attachedFiles.stream()
                .map(File::getAbsolutePath)
                .toList();

        // 构建显示文本和发送文本
        StringBuilder messageBuilder = new StringBuilder();
        if (!filePaths.isEmpty()) {
            for (String path : filePaths) {
                messageBuilder.append("- ").append(path).append("\n");
            }
            messageBuilder.append("\n");
        }
        if (!text.isBlank()) {
            messageBuilder.append(text);
        }

        // 添加用户消息卡片（显示纯文本，不含文件路径前缀）
        this.viewModel.addUserMessage(text);
        // 发送给 AI（包含文件路径）
        this.viewModel.sendMessage(messageBuilder.toString());
        this.sendField.clear();
        this.clearAttachedFiles();
    }

    private void handleAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择文件");
        File selectedFile = fileChooser.showOpenDialog(this.sendBox.getScene().getWindow());
        if (selectedFile != null && !this.attachedFiles.contains(selectedFile)) {
            this.attachedFiles.add(selectedFile);
            this.addFileTag(selectedFile);
            this.updateFileTagsPaneVisibility();
        }
    }

    /**
     * 将选中文本追加到对话框输入框末尾（编辑器面板联动入口）。
     * 已有内容时用换行分隔。
     */
    public void appendTextToChat(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String current = sendField.getText();
        if (current != null && !current.isEmpty()) {
            sendField.setText(current + "\n" + text);
        } else {
            sendField.setText(text);
        }
        sendField.requestFocus();
        sendField.end();
        // 仅追加文本，不触发界面状态切换动画（避免 logo 消失）
    }

    /**
     * 添加附件文件（供拖拽调用，去重处理）。
     */
    public void addAttachedFile(File file) {
        if (file != null && !this.attachedFiles.contains(file)) {
            this.attachedFiles.add(file);
            this.addFileTag(file);
            this.updateFileTagsPaneVisibility();
        }
    }

    private void handleOpenCanvas() {
        windowManager.showDialog("cn/bitloom/view/CanvasDialog.fxml", this.sendBox.getScene().getWindow(), controller -> {
            if (controller instanceof CanvasDialogController canvasController) {
                canvasController.setOnSendToChat(this::handleCanvasContent);
            }
        });
    }

    private void handleCanvasContent(String canvasContent) {
        if (!this.chatScrollPane.isVisible()) {
            this.animateToChatState();
        }
        this.viewModel.addUserMessage(canvasContent);
        this.viewModel.sendMessage(canvasContent);
    }

    private void addFileTag(File file) {
        HBox tag = new HBox();
        tag.getStyleClass().add("home-page__file-tag");
        tag.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        tag.setSpacing(2);

        SvgImageView fileIcon = new SvgImageView();
        fileIcon.setFitWidth(12);
        fileIcon.setFitHeight(12);
        fileIcon.setSvgPath("/cn/bitloom/images/file.svg");
        fileIcon.getStyleClass().add("home-page__file-tag-icon");

        Label nameLabel = new Label(file.getName());
        nameLabel.getStyleClass().add("home-page__file-tag-name");

        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().add("home-page__file-tag-close");
        closeBtn.setOnAction(event -> {
            this.attachedFiles.remove(file);
            this.fileTagsPane.getChildren().remove(tag);
            this.updateFileTagsPaneVisibility();
        });

        tag.getChildren().addAll(fileIcon, nameLabel, closeBtn);
        this.fileTagsPane.getChildren().add(tag);
    }

    private void clearAttachedFiles() {
        this.attachedFiles.clear();
        this.fileTagsPane.getChildren().clear();
        this.updateFileTagsPaneVisibility();
    }

    private void updateFileTagsPaneVisibility() {
        boolean hasFiles = !this.attachedFiles.isEmpty();
        // 由外层 ScrollPane 控制可见性，FlowPane 跟随
        this.fileTagsScroll.setVisible(hasFiles);
        this.fileTagsScroll.setManaged(hasFiles);
    }

    private void adjustTextAreaHeight() {
        // AutoResizeTextArea 通过 computePrefHeight 自动调整高度，此方法保留为空实现
        // 以避免破坏其他可能的调用点（如有）
    }

    private void scrollToBottom() {
        shouldScrollToBottom.set(true);
    }

    private void animateToChatState() {
        Timeline timeline = new Timeline();
        KeyFrame keyFrame = new KeyFrame(Duration.millis(600),
                new KeyValue(this.icon.opacityProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(this.icon.translateYProperty(), -60, Interpolator.EASE_BOTH));

        timeline.getKeyFrames().add(keyFrame);

        timeline.setOnFinished(event -> {
            this.icon.setVisible(false);
            this.icon.setManaged(false);

            this.homePage.setAlignment(Pos.BOTTOM_CENTER);

            this.chatScrollPane.setVisible(true);
            this.chatScrollPane.setManaged(true);
        });

        timeline.play();
    }

    /**
     * 根据智能体类型控制项目按钮显示/隐藏
     */
    private void updateProjectButtonBarVisibility(String agentId) {
        boolean show = "coder".equals(agentId);
        projectSelectButton.setVisible(show);
        projectSelectButton.setManaged(show);
        branchDisplayButton.setVisible(show);
        branchDisplayButton.setManaged(show);
    }

    /**
     * 根据是否已有对话消息锁定/解锁项目选择按钮。
     * 一个 session 只能绑定一个智能体和一个项目：有了对话后不可修改，
     * 新建会话或清除对话后自动解锁。
     * 智能体切换入口已移至侧边栏分段按钮（不受此锁定影响，切换会创建新会话）。
     */
    private void updateSelectorLockState(boolean locked) {
        projectSelectButton.setDisable(locked);
    }

    /**
     * 设置项目选择下拉菜单
     * 第一项：选择文件夹（打开 DirectoryChooser）
     * 后续项：最近打开的项目列表
     */
    private void setupProjectMenu() {
        refreshProjectMenu();
        refreshProjectMenuText(viewModel.getCurrentProject());
    }

    /**
     * 刷新项目下拉菜单内容
     */
    private void refreshProjectMenu() {
        projectSelectButton.getItems().clear();

        // 第一项：选择文件夹
        MenuItem openFolderItem = new MenuItem("选择文件夹...");
        openFolderItem.setOnAction(e -> handleOpenLocalFolder());
        projectSelectButton.getItems().add(openFolderItem);

        // 分隔线
        projectSelectButton.getItems().add(new SeparatorMenuItem());

        // 最近项目列表
        List<ProjectInfo> projects = viewModel.listProjects();
        for (ProjectInfo project : projects) {
            MenuItem item = new MenuItem(project.name());
            item.setOnAction(e -> {
                viewModel.setCurrentProject(project);
                refreshBranchDisplay(project);
            });
            projectSelectButton.getItems().add(item);
        }
    }

    /**
     * 刷新项目选择按钮显示文本
     */
    private void refreshProjectMenuText(ProjectInfo project) {
        if (project != null) {
            projectSelectButton.setText(project.name());
        } else {
            projectSelectButton.setText("选择项目");
        }
    }

    /**
     * 打开本地文件夹选择器
     */
    private void handleOpenLocalFolder() {
        try {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle("选择项目文件夹");
            javafx.stage.Stage stage = (javafx.stage.Stage) projectSelectButton.getScene().getWindow();
            File selectedDir = dirChooser.showDialog(stage);
            if (selectedDir != null) {
                String path = selectedDir.getAbsolutePath();
                String name = selectedDir.getName();
                viewModel.registerLocalProject(path, name);
                refreshProjectMenu();
            }
        } catch (Exception e) {
            log.error("打开文件夹选择器失败", e);
        }
    }

    /**
     * 刷新分支显示
     * 默认只显示图标，选择项目后显示分支名称
     */
    private void refreshBranchDisplay(ProjectInfo project) {
        if (project == null || project.gitBranch() == null || project.gitBranch().isBlank()) {
            branchDisplayButton.setText("");
        } else {
            branchDisplayButton.setText(project.gitBranch());
        }
    }

    @Override
    public void show() {
        homePage.setVisible(true);
        homePage.setManaged(true);
    }

    @Override
    public void hide() {
        homePage.setVisible(false);
        homePage.setManaged(false);
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        // coder 模式才显示终端/项目按钮；default 模式只显示新对话+工具+待办
        boolean isCoder = "coder".equals(Store.currentAgent.get());
        List<ButtonBarHolder.ButtonConfig> configs = new ArrayList<>();
        configs.add(new ButtonBarHolder.ButtonConfig(
                "newChatButton",
                "",
                "button-bar__icon-btn",
                "/cn/bitloom/images/chat-new.svg",
                _ -> {
                    this.viewModel.createNewSession();
                    resetForNewSession();
                }));
        if (isCoder) {
            configs.add(new ButtonBarHolder.ButtonConfig(
                    "terminalButton",
                    "终端",
                    "button-bar__icon-btn",
                    "/cn/bitloom/images/terminal.svg",
                    ButtonBarHolder.Alignment.RIGHT,
                    _ -> {
                        if (indexController != null) {
                            indexController.toggleTerminalPanel();
                        }
                    }));
            configs.add(new ButtonBarHolder.ButtonConfig(
                    "projectButton",
                    "项目",
                    "button-bar__icon-btn",
                    "/cn/bitloom/images/folder.svg",
                    ButtonBarHolder.Alignment.RIGHT,
                    _ -> {
                        if (indexController != null) {
                            indexController.toggleProjectPanel();
                        }
                    }));
        }
        configs.add(new ButtonBarHolder.ButtonConfig(
                "toolCallsButton",
                "工具",
                "button-bar__icon-btn",
                "/cn/bitloom/images/plug.svg",
                ButtonBarHolder.Alignment.RIGHT,
                _ -> {
                    if (indexController != null) {
                        indexController.toggleToolCallsPanel();
                    }
                }));
        configs.add(new ButtonBarHolder.ButtonConfig(
                "todoButton",
                "待办",
                "button-bar__icon-btn",
                "/cn/bitloom/images/list.svg",
                ButtonBarHolder.Alignment.RIGHT,
                _ -> {
                    if (indexController != null) {
                        indexController.toggleTodoPanel();
                    }
                }));
        return configs;
    }

    public void resetForNewSession() {
        this.sendField.setText("");
        this.clearAttachedFiles();
        this.chatContainer.getChildren().clear();
        // 清空 EditorPanel 中的工具调用和待办卡片，并重置 ToolUIBridge 的 currentTodoCard 引用
        toolUIBridge.resetTodoCard();
        if (indexController != null && indexController.getEditorPanelController() != null) {
            indexController.getEditorPanelController().clearToolCalls();
            indexController.getEditorPanelController().clearTodos();
        }
        // 清空对话框上方的 diff 审查卡片条
        this.diffReviewList.getChildren().clear();
        this.diffReviewExpanded = false;
        this.diffReviewListScroll.setVisible(false);
        this.diffReviewListScroll.setManaged(false);
        this.diffReviewBar.setMaxHeight(Region.USE_PREF_SIZE);
        this.updateDiffReviewBar();

        this.homePage.setAlignment(Pos.CENTER);
        VBox.setMargin(this.sendBox, new Insets(0, 0, 0, 0));
        this.chatScrollPane.setVisible(false);
        this.chatScrollPane.setManaged(false);

        this.icon.setVisible(true);
        this.icon.setManaged(true);
        this.icon.setOpacity(1);
        this.icon.setTranslateY(0);

        if (this.viewModel.hasHistoricalMessages()) {
            this.animateToChatState();
            this.viewModel.prepareHistoricalMessages();
        }
    }

}
