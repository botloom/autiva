package cn.bitloom.controller;

import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.message.AssistantMessageCard;
import cn.bitloom.node.message.MessageCard;
import cn.bitloom.node.message.NodeMessageCard;
import cn.bitloom.node.message.ToolMessageCard;
import cn.bitloom.node.AutoResizeTextArea;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.node.tool.TaskCard;
import cn.bitloom.node.tool.TodoCard;
import cn.bitloom.store.Store;
import cn.bitloom.vm.AbstractHomePageViewModel;
import cn.bitloom.window.WindowManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import org.springframework.ai.chat.messages.MessageType;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 首页控制器抽象基类。
 * <p>
 * 包含通用的消息列表、发送框、文件附件、拖拽、动画等逻辑。
 * 子类（CoderHomePageController / WorkHomePageController）实现模式专有逻辑。
 */
@Slf4j
public abstract class AbstractHomePageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    protected VBox homePage;
    @FXML
    protected VBox sendBox;
    @FXML
    protected AutoResizeTextArea sendField;
    @FXML
    protected Button sendButton;
    @FXML
    protected Button stopButton;
    @FXML
    protected Button addFileButton;
    @FXML
    protected Button canvasButton;
    @FXML
    protected FlowPane fileTagsPane;
    @FXML
    protected ScrollPane fileTagsScroll;
    @FXML
    protected VBox icon;
    @FXML
    protected ListView<MessageCard> chatListView;

    protected final List<File> attachedFiles = new ArrayList<>();
    /**
     * 历史消息加载提示卡片（包装为 NodeMessageCard 加入 messages 列表）
     */
    private NodeMessageCard loadingIndicatorCard = null;

    @Getter
    protected final ToolUIBridge toolUIBridge;
    @Getter
    protected final WindowManager windowManager;

    @Getter
    @Setter
    protected IndexController indexController;

    protected AbstractHomePageController(ToolUIBridge toolUIBridge, WindowManager windowManager) {
        this.toolUIBridge = toolUIBridge;
        this.windowManager = windowManager;
    }

    /**
     * 获取当前 ViewModel（子类返回具体类型的 ViewModel）
     */
    public abstract AbstractHomePageViewModel getViewModel();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 默认使用 DeepSeek 模型
        Store.selectedModel.set(ModelTypeEnum.DEEPSEEK);

        this.sendButton.setOnAction(event -> this.handleSendMessage());
        this.stopButton.setOnAction(event -> this.getViewModel().pauseGeneration());

        // Ctrl+Enter 发送消息，Enter 换行
        this.sendField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown() && !event.isControlDown()) {
                event.consume();
                this.handleSendMessage();
            }
        });

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

        // 初始化消息 ListView：直接使用原始 ObservableList，
        // TOOL 类型卡片通过 toolCardHandler 回调直接路由到 EditorPanel，不进入 messages 列表
        this.chatListView.setFocusTraversable(false);
        this.chatListView.setItems(this.getViewModel().getMessages());
        this.chatListView.setCellFactory(list -> new MessageListCell());

        // 注入工具卡片路由回调：ToolMessageCard 直接发送到 EditorPanel，不进 messages 列表
        this.getViewModel().setToolCardHandler(this::addToolToEditorPanel);

        // 配置 stick-to-bottom 跟随模式
        setupStickToBottom();

        // 历史消息加载期间：禁用发送按钮和输入框，显示加载提示
        this.getViewModel().historyLoadingProperty().addListener((obs, oldVal, newVal) -> {
            boolean loading = newVal != null && newVal;
            this.sendButton.setDisable(loading);
            this.sendField.setDisable(loading);
            this.addFileButton.setDisable(loading);
            if (loading) {
                if (loadingIndicatorCard == null) {
                    loadingIndicatorCard = new NodeMessageCard(createLoadingIndicator());
                    this.getViewModel().getMessages().add(loadingIndicatorCard);
                }
            } else {
                if (loadingIndicatorCard != null) {
                    this.getViewModel().getMessages().remove(loadingIndicatorCard);
                    loadingIndicatorCard = null;
                }
            }
        });

        this.toolUIBridge.setOnNodeAdded(this::addChatNode);

        this.getViewModel().getMessages().addListener((ListChangeListener<MessageCard>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    scrollToBottom();
                }
            }
            onMessagesChanged(!this.getViewModel().getMessages().isEmpty());
        });

        if (this.getViewModel().hasHistoricalMessages()) {
            this.animateToChatState();
            this.getViewModel().prepareHistoricalMessages();
        }

        // 注册对话框为拖拽目标（接收来自文件树/Diff 列表的文件拖拽）
        this.setupDragDrop();
    }

    /**
     * 消息列表变化时的回调（子类可 override 实现模式专有逻辑，如锁定选择器）
     */
    protected void onMessagesChanged(boolean hasMessages) {
        // 默认空实现，子类可 override
    }

    /**
     * 创建历史消息加载指示器（Apple 风格 ProgressIndicator + 文字）
     */
    private javafx.scene.layout.VBox createLoadingIndicator() {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setPadding(new javafx.geometry.Insets(32));
        box.setUserData("history-loading");

        javafx.scene.control.ProgressIndicator indicator = new javafx.scene.control.ProgressIndicator();
        indicator.setPrefSize(24, 24);
        indicator.setStyle("-fx-progress-color: #86868b;");

        javafx.scene.control.Label label = new javafx.scene.control.Label("加载历史对话...");
        label.setStyle("-fx-text-fill: #86868b; -fx-font-size: 13px;");

        box.getChildren().addAll(indicator, label);
        return box;
    }

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
        }
        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * 为消息卡片组装行视图（card + actionBar + row 对齐）。
     * 由 MessageListCell 在 updateItem 中调用。
     */
    private HBox buildMessageRow(MessageCard card) {
        card.maxWidthProperty().bind(
                Bindings.max(100, chatListView.widthProperty().subtract(32).multiply(0.75))
        );

        VBox messageWrapper = new VBox();
        messageWrapper.getStyleClass().add("chat-message-wrapper");
        messageWrapper.getChildren().add(card);

        HBox actionBar = createActionBar(card);
        messageWrapper.getChildren().add(actionBar);

        if (card instanceof AssistantMessageCard assistantCard) {
            assistantCard.setActionBar(actionBar);
            assistantCard.setOnContentChanged(c -> {
                // 标记 ListView 需要重新布局，使 scrollToBottom 中的 layout() 能基于最新 cell 高度计算滚动条范围
                chatListView.requestLayout();
                scrollToBottom();
            });
        }

        return createMessageRow(messageWrapper, card.getMessageType());
    }

    /**
     * 消息列表 cell：根据卡片类型渲染。
     * - NodeMessageCard：直接 setGraphic(node)，左对齐
     * - USER/ASSISTANT：组装 messageWrapper + actionBar + row
     */
    private class MessageListCell extends ListCell<MessageCard> {
        @Override
        protected void updateItem(MessageCard card, boolean empty) {
            super.updateItem(card, empty);
            if (empty || card == null) {
                setGraphic(null);
                setStyle(null);
            } else if (card instanceof NodeMessageCard nmc) {
                setGraphic(nmc.getNode());
                setStyle(null);
            } else {
                setGraphic(buildMessageRow(card));
                setStyle(null);
            }
        }
    }

    private void addToolToEditorPanel(ToolMessageCard toolCard) {
        if (indexController == null || indexController.getEditorPanelController() == null) return;
        indexController.getEditorPanelController().addToolCallCard(toolCard);
    }

    /**
     * toolUIBridge 回调：将工具节点添加到聊天区或编辑器面板。
     * 由 HomePageRouter 在模式切换时重绑定。
     */
    public void addChatNode(Node node) {
        if (node instanceof TodoCard todoCard) {
            if (indexController != null && indexController.getEditorPanelController() != null) {
                indexController.getEditorPanelController().addTodoCard(todoCard);
            }
            return;
        }
        if (node instanceof Region region) {
            region.maxWidthProperty().bind(
                    Bindings.max(100, chatListView.widthProperty().subtract(32).multiply(0.85))
            );
        }
        if (node instanceof TaskCard taskCard) {
            taskCard.setOnContentChanged(c -> scrollToBottom());
        }
        this.getViewModel().getMessages().add(new NodeMessageCard(node));
    }

    private HBox createMessageRow(Node card, MessageType type) {
        HBox row = new HBox();
        row.getStyleClass().add("chat-row");
        row.setMaxWidth(Double.MAX_VALUE);

        if (type == MessageType.USER) {
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
        if (card.getMessageType() == MessageType.USER) {
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

    protected void handleSendMessage() {
        if (this.sendField.getText().isBlank() && this.attachedFiles.isEmpty()) {
            return;
        }
        String text = this.sendField.getText();
        if (!this.chatListView.isVisible()) {
            this.animateToChatState();
        }
        List<String> filePaths = this.attachedFiles.stream()
                .map(File::getAbsolutePath)
                .toList();

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

        this.getViewModel().addUserMessage(text);
        this.getViewModel().sendMessage(messageBuilder.toString());
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
    }

    /**
     * 从 DiffService 刷新 diff 审查条（通用基类空实现，coder 模式 override）
     * 在 diff 看板中撤销/保留文件后，通知首页同步更新 diff 卡片列表。
     */
    public void refreshDiffReviewBarFromService() {
        // work 模式不支持 diff 审查条
    }

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
        if (!this.chatListView.isVisible()) {
            this.animateToChatState();
        }
        this.getViewModel().addUserMessage(canvasContent);
        this.getViewModel().sendMessage(canvasContent);
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
        this.fileTagsScroll.setVisible(hasFiles);
        this.fileTagsScroll.setManaged(hasFiles);
    }

    // ===== stick-to-bottom 跟随模式 =====
    // 用户向上滚动时停止自动跟随，滚回底部时恢复跟随。
    // 流式内容增加导致 vvalue 下降不会误判（只响应鼠标滚轮）。
    private boolean stickToBottom = true;
    private boolean scrollBarBound = false;

    private void setupStickToBottom() {
        // 鼠标滚轮向上滚动 → 停止跟随
        chatListView.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() > 0) {
                stickToBottom = false;
            }
        });

        // skin 加载后绑定垂直滚动条，监听是否滚回底部
        chatListView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null && !scrollBarBound) {
                Platform.runLater(this::bindVerticalScrollBar);
            }
        });
    }

    private void bindVerticalScrollBar() {
        if (scrollBarBound) return;
        Node bar = chatListView.lookup(".scroll-bar:vertical");
        if (bar instanceof ScrollBar scrollBar) {
            scrollBar.valueProperty().addListener((o, ov, nv) -> {
                // 滚动条到底部附近 → 恢复跟随
                if (nv.doubleValue() >= 0.95) {
                    stickToBottom = true;
                }
            });
            scrollBarBound = true;
        }
    }

    private void scrollToBottom() {
        if (!stickToBottom) return;
        Platform.runLater(() -> {
            int size = chatListView.getItems().size();
            if (size <= 0) return;
            // 先 layout 让 VirtualFlow 基于最新 cell 内容重新计算高度与滚动条 max
            chatListView.layout();
            // scrollTo 是 ListView 官方滚动 API，会触发 VirtualFlow 内部测量与滚动，
            // 比 scrollBar.setValue(getMax()) 更可靠（后者在 cell 高度变化后 max 可能仍是旧值）
            chatListView.scrollTo(size - 1);
        });
    }

    protected void animateToChatState() {
        Timeline timeline = new Timeline();
        KeyFrame keyFrame = new KeyFrame(Duration.millis(600),
                new KeyValue(this.icon.opacityProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(this.icon.translateYProperty(), -60, Interpolator.EASE_BOTH));

        timeline.getKeyFrames().add(keyFrame);

        timeline.setOnFinished(event -> {
            this.icon.setVisible(false);
            this.icon.setManaged(false);

            this.homePage.setAlignment(Pos.BOTTOM_CENTER);

            this.chatListView.setVisible(true);
            this.chatListView.setManaged(true);
        });

        timeline.play();
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

    /**
     * 创建通用按钮配置（newChat/tool/todo），子类可在此基础上扩展。
     */
    protected List<ButtonBarHolder.ButtonConfig> createCommonButtons() {
        List<ButtonBarHolder.ButtonConfig> configs = new ArrayList<>();
        configs.add(new ButtonBarHolder.ButtonConfig(
                "newChatButton",
                "",
                "button-bar__icon-btn",
                "/cn/bitloom/images/chat-new.svg",
                _ -> {
                    this.getViewModel().createNewSession();
                    resetForNewSession();
                }));
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

    /**
     * 重置为新会话状态（通用逻辑 + 子类专有逻辑）
     */
    public void resetForNewSession() {
        this.sendField.setText("");
        this.clearAttachedFiles();
        this.getViewModel().getMessages().clear();
        toolUIBridge.resetTodoCard();
        if (indexController != null && indexController.getEditorPanelController() != null) {
            indexController.getEditorPanelController().clearToolCalls();
            indexController.getEditorPanelController().clearTodos();
        }

        this.homePage.setAlignment(Pos.CENTER);
        VBox.setMargin(this.sendBox, new Insets(0, 0, 0, 0));
        this.chatListView.setVisible(false);
        this.chatListView.setManaged(false);

        this.icon.setVisible(true);
        this.icon.setManaged(true);
        this.icon.setOpacity(1);
        this.icon.setTranslateY(0);

        // 子类专有重置逻辑
        onResetForNewSession();

        if (this.getViewModel().hasHistoricalMessages()) {
            this.animateToChatState();
            this.getViewModel().prepareHistoricalMessages();
        }
    }

    /**
     * 子类实现模式专有的重置逻辑（如清空 diff 审查条）
     */
    protected abstract void onResetForNewSession();

    /**
     * 释放资源（模式切换时调用，取消事件订阅）。
     * 子类可 override 扩展清理逻辑，但必须 super.dispose()。
     */
    public void dispose() {
        getViewModel().dispose();
    }
}
