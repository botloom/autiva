package cn.bitloom.controller;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.AssistantMessageCard;
import cn.bitloom.node.ChatMessage;
import cn.bitloom.node.SvgImageView;
import cn.bitloom.node.TaskCard;
import cn.bitloom.node.ToolGroupCard;
import cn.bitloom.node.ToolMessageCard;
import cn.bitloom.node.UserMessageCard;
import cn.bitloom.store.Store;
import cn.bitloom.store.ToolUIBridge;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageController implements Initializable, ButtonBarHolder, PageHolder {

    @FXML
    private VBox homePage;
    @FXML
    private VBox searchBox;
    @FXML
    private TextArea searchField;
    @FXML
    private Button searchButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button addFileButton;
    @FXML
    private Button canvasButton;
    @FXML
    private FlowPane fileTagsPane;
    @FXML
    private VBox icon;
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatContainer;
    @FXML
    private ComboBox<ModelTypeEnum> modelSelector;
    @FXML
    private ComboBox<String> agentSelector;

    private final List<File> attachedFiles = new ArrayList<>();
    private final BooleanProperty shouldScrollToBottom = new SimpleBooleanProperty(false);
    private ToolGroupCard currentToolGroup = null;
    private boolean isLoadingMore = false;

    @Getter
    private final HomePageViewModel viewModel;
    @Getter
    private final ToolUIBridge toolUIBridge;
    @Getter
    private final WindowManager windowManager;
    private final AgentManager agentManager;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.modelSelector.valueProperty().bindBidirectional(Store.selectedModel);

        this.searchButton.setOnAction(event -> this.handleSendMessage());
        this.stopButton.setOnAction(event -> this.viewModel.pauseGeneration());

        // Ctrl+Enter 发送消息，Enter 换行
        this.searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown() && !event.isControlDown()) {
                event.consume();
                this.handleSendMessage();
            }
        });

        // TextArea 自动调整高度
        this.searchField.textProperty().addListener((obs, oldVal, newVal) -> adjustTextAreaHeight());

        // 添加文件按钮
        this.addFileButton.setOnAction(event -> this.handleAddFile());

        // 画布按钮
        this.canvasButton.setOnAction(event -> this.handleOpenCanvas());

        Store.isStreaming.addListener((obs, oldVal, newVal) -> {
            boolean streaming = newVal != null && newVal;
            boolean paused = Store.isPaused.get();
            boolean showSend = !streaming || paused;
            this.searchButton.setVisible(showSend);
            this.searchButton.setManaged(showSend);
            this.stopButton.setVisible(streaming && !paused);
            this.stopButton.setManaged(streaming && !paused);
        });

        Store.isPaused.addListener((obs, oldVal, newVal) -> {
            boolean streaming = Store.isStreaming.get();
            boolean paused = newVal != null && newVal;
            boolean showSend = !streaming || paused;
            this.searchButton.setVisible(showSend);
            this.searchButton.setManaged(showSend);
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

        // 滚动到顶部时加载更多历史消息
        chatScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() <= 0.01 && !isLoadingMore && viewModel.hasMoreMessages()) {
                loadMoreMessages();
            }
        });

        this.modelSelector.getItems().addAll(ModelTypeEnum.values());
        this.modelSelector.setValue(ModelTypeEnum.DEEPSEEK);
        this.updateModelSelectorWidth();
        this.modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                this.updateModelSelectorWidth();
            }
        });

        // 智能体选择按钮
        this.setupAgentSelector();

        this.toolUIBridge.setOnNodeAdded(this::addChatNode);

        this.viewModel.getMessages().addListener((ListChangeListener<ChatMessage>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (ChatMessage msg : change.getAddedSubList()) {
                        addChatMessage(msg);
                    }
                }
            }
        });

        if (this.viewModel.hasHistoricalMessages()) {
            this.animateToChatState();
            this.viewModel.prepareHistoricalMessages();
        }
    }

    private void addChatMessage(ChatMessage msg) {
        if (msg.getType() == ChatMessage.Type.TOOL) {
            Node toolCard = createToolMessageCard(msg);
            if (toolCard != null) {
                String toolName = extractToolName(msg);
                addToolToGroup(toolCard, toolName);
                scrollToBottom();
            }
            return;
        }

        // 非 TOOL 消息中断当前工具分组
        currentToolGroup = null;

        Node card = createMessageCard(msg);
        if (card != null) {
            if (card instanceof Region region) {
                region.maxWidthProperty().bind(
                        Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.75))
                );
            }

            // 创建消息包装容器：卡片 + 操作按钮
            VBox messageWrapper = new VBox();
            messageWrapper.getStyleClass().add("chat-message-wrapper");
            messageWrapper.getChildren().add(card);

            // 创建操作按钮栏
            HBox actionBar = createActionBar(msg);
            messageWrapper.getChildren().add(actionBar);

            // 对于助手消息，流式输出期间隐藏操作栏
            if (msg.getType() == ChatMessage.Type.ASSISTANT) {
                actionBar.setVisible(!msg.isStreaming());
                actionBar.setManaged(!msg.isStreaming());
                msg.streamingProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal) {
                        actionBar.setVisible(true);
                        actionBar.setManaged(true);
                    }
                });
            }

            HBox row = createMessageRow(messageWrapper, msg.getType());
            chatContainer.getChildren().add(row);
            scrollToBottom();
        } else {
            log.warn("addChatMessage: createMessageCard returned null for type={}", msg.getType());
        }
    }

    private void addToolToGroup(Node toolCard, String toolName) {
        if (currentToolGroup != null) {
            // 追加到现有分组
            currentToolGroup.addToolCard(toolCard, toolName);
        } else {
            // 创建新的工具分组
            currentToolGroup = new ToolGroupCard();
            currentToolGroup.maxWidthProperty().bind(
                    Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.85))
            );
            currentToolGroup.addToolCard(toolCard, toolName);

            HBox row = new HBox();
            row.getStyleClass().add("chat-row");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(currentToolGroup);
            chatContainer.getChildren().add(row);
        }
    }

    private String extractToolName(ChatMessage msg) {
        if (!msg.getToolCalls().isEmpty()) {
            return msg.getToolCalls().get(0).name();
        }
        if (!msg.getResponses().isEmpty()) {
            return msg.getResponses().get(0).name();
        }
        return null;
    }

    private void addChatNode(Node node) {
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

    private HBox createMessageRow(Node card, ChatMessage.Type type) {
        HBox row = new HBox();
        row.getStyleClass().add("chat-row");
        row.setMaxWidth(Double.MAX_VALUE);

        if (type == ChatMessage.Type.USER) {
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

    private Node createMessageCard(ChatMessage msg) {
        return switch (msg.getType()) {
            case USER -> new UserMessageCard(msg.getContent());
            case ASSISTANT -> {
                AssistantMessageCard card = new AssistantMessageCard(msg);
                card.setOnContentChanged(c -> scrollToBottom());
                yield card;
            }
            case TOOL -> createToolMessageCard(msg);
        };
    }

    private Node createToolMessageCard(ChatMessage msg) {
        if (!msg.getToolCalls().isEmpty()) {
            ChatMessage.ToolCallInfo tc = msg.getToolCalls().get(0);
            return new ToolMessageCard(tc.name(), tc.arguments(), true);
        }
        if (!msg.getResponses().isEmpty()) {
            ChatMessage.ToolResponseInfo resp = msg.getResponses().get(0);
            return new ToolMessageCard(resp.name(), resp.responseData(), false);
        }
        return null;
    }

    private HBox createActionBar(ChatMessage msg) {
        HBox actionBar = new HBox();
        actionBar.getStyleClass().add("chat-message__actions");
        if (msg.getType() == ChatMessage.Type.USER) {
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
            String content = msg.getContent();
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
        if (this.searchField.getText().isBlank() && this.attachedFiles.isEmpty()) {
            return;
        }
        String text = this.searchField.getText();
        if (!this.chatScrollPane.isVisible()) {
            this.animateToChatState();
        }
        List<String> filePaths = this.attachedFiles.stream()
                .map(File::getAbsolutePath)
                .toList();
        this.viewModel.addUserMessage(text, filePaths);
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
        UserMessage userMessage = UserMessage.builder().text(messageBuilder.toString()).build();
        this.viewModel.sendMessage(userMessage);
        this.searchField.clear();
        this.clearAttachedFiles();
    }

    private void handleAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择文件");
        File selectedFile = fileChooser.showOpenDialog(this.searchBox.getScene().getWindow());
        if (selectedFile != null && !this.attachedFiles.contains(selectedFile)) {
            this.attachedFiles.add(selectedFile);
            this.addFileTag(selectedFile);
            this.updateFileTagsPaneVisibility();
        }
    }

    private void handleOpenCanvas() {
        windowManager.showDialog("cn/bitloom/view/CanvasDialog.fxml", this.searchBox.getScene().getWindow(), controller -> {
            if (controller instanceof CanvasDialogController canvasController) {
                canvasController.setOnSendToChat(this::handleCanvasContent);
            }
        });
    }

    private void handleCanvasContent(String canvasContent) {
        if (!this.chatScrollPane.isVisible()) {
            this.animateToChatState();
        }
        this.viewModel.addUserMessage(canvasContent, List.of());
        UserMessage userMessage = UserMessage.builder().text(canvasContent).build();
        this.viewModel.sendMessage(userMessage);
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
        this.fileTagsPane.setVisible(hasFiles);
        this.fileTagsPane.setManaged(hasFiles);
    }

    private void adjustTextAreaHeight() {
        String text = this.searchField.getText();
        int lineCount = 1;
        if (text != null && !text.isEmpty()) {
            lineCount = (int) text.chars().filter(c -> c == '\n').count() + 1;
        }
        double lineHeight = 22;
        double padding = 16;
        double height = Math.max(48, Math.min(200, lineCount * lineHeight + padding));
        this.searchField.setPrefHeight(height);
    }

    private void scrollToBottom() {
        shouldScrollToBottom.set(true);
    }

    private void loadMoreMessages() {
        isLoadingMore = true;
        List<Message> olderMessages = viewModel.loadMoreMessages(30);
        if (olderMessages.isEmpty()) {
            isLoadingMore = false;
            return;
        }

        // 记录当前滚动位置和内容高度，用于恢复位置
        double oldVvalue = chatScrollPane.getVvalue();
        double oldContentHeight = chatContainer.getHeight();

        // 在头部插入消息卡片（逆序插入，因为 olderMessages 是从旧到新）
        int insertIndex = 0;
        for (Message msg : olderMessages) {
            ChatMessage chatMsg = convertToChatMessage(msg);
            if (chatMsg != null) {
                Node card = createMessageCard(chatMsg);
                if (card != null) {
                    if (card instanceof Region region) {
                        region.maxWidthProperty().bind(
                                Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.75))
                        );
                    }
                    VBox messageWrapper = new VBox();
                    messageWrapper.getStyleClass().add("chat-message-wrapper");
                    messageWrapper.getChildren().add(card);
                    HBox actionBar = createActionBar(chatMsg);
                    messageWrapper.getChildren().add(actionBar);
                    if (chatMsg.getType() == ChatMessage.Type.ASSISTANT) {
                        actionBar.setVisible(false);
                        actionBar.setManaged(false);
                    }
                    HBox row = createMessageRow(messageWrapper, chatMsg.getType());
                    chatContainer.getChildren().add(insertIndex, row);
                    insertIndex++;
                }
            }
        }

        // 恢复滚动位置：保持视觉位置不变
        Platform.runLater(() -> {
            double newContentHeight = chatContainer.getHeight();
            double heightDiff = newContentHeight - oldContentHeight;
            if (heightDiff > 0 && oldContentHeight > 0) {
                double newVvalue = (oldVvalue * oldContentHeight + heightDiff) / newContentHeight;
                chatScrollPane.setVvalue(Math.min(1.0, newVvalue));
            }
            isLoadingMore = false;
        });
    }

    private ChatMessage convertToChatMessage(Message msg) {
        if (msg instanceof UserMessage userMsg) {
            ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.USER);
            chatMsg.setContent(userMsg.getText());
            return chatMsg;
        } else if (msg instanceof AssistantMessage assistantMsg) {
            Map<String, Object> metadata = assistantMsg.getMetadata();
            String finishReason = (String) metadata.get("finishReason");
            String text = assistantMsg.getText();
            ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.ASSISTANT);
            chatMsg.setContent(text);
            if ("TOOL_CALLS".equals(finishReason)) {
                chatMsg.setFinishReason(ChatMessage.FinishReason.TOOL_CALLS);
            } else {
                chatMsg.setFinishReason(ChatMessage.FinishReason.STOP);
            }
            return chatMsg;
        } else if (msg instanceof ToolResponseMessage toolMsg) {
            ChatMessage chatMsg = new ChatMessage(ChatMessage.Type.TOOL);
            for (ToolResponseMessage.ToolResponse resp : toolMsg.getResponses()) {
                chatMsg.getResponses().add(new ChatMessage.ToolResponseInfo(resp.name(), resp.responseData()));
            }
            if (!chatMsg.getResponses().isEmpty()) {
                return chatMsg;
            }
        }
        return null;
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

    private void updateModelSelectorWidth() {
        ModelTypeEnum selectedModel = this.modelSelector.getValue();
        if (selectedModel == null) {
            return;
        }

        Text text = new Text(selectedModel.name());
        text.setFont(javafx.scene.text.Font.font("SF Pro Text", 13));

        double textWidth = text.getLayoutBounds().getWidth();
        double padding = 26;
        double width = Math.max(37, textWidth + padding);

        this.modelSelector.setPrefWidth(width);
        this.modelSelector.setMinWidth(width);
        this.modelSelector.setMaxWidth(width);
    }

    private void setupAgentSelector() {
        // 从 AgentManager 获取主智能体列表
        for (AgentManager.AgentFolder agent : agentManager.loadAgentFolders()) {
            this.agentSelector.getItems().add(agent.getName());
        }
        this.agentSelector.setValue("default");
        updateAgentSelectorWidth();

        this.agentSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 切换当前 session 的活跃 agent
                viewModel.switchAgent(newVal);
                updateAgentSelectorWidth();
            }
        });

        // 反向绑定：Store.currentAgent 变更时同步 agentSelector
        Store.currentAgent.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(this.agentSelector.getValue())) {
                this.agentSelector.setValue(newVal);
            }
        });
    }

    private void updateAgentSelectorWidth() {
        String current = this.agentSelector.getValue();
        String name = current != null ? current : "default";
        Text text = new Text(name);
        text.setFont(javafx.scene.text.Font.font("SF Pro Text", 13));
        double textWidth = text.getLayoutBounds().getWidth();
        double padding = 28;
        double width = Math.max(50, textWidth + padding);
        this.agentSelector.setPrefWidth(width);
        this.agentSelector.setMinWidth(width);
        this.agentSelector.setMaxWidth(width);
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
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "newChatButton",
                        "新对话",
                        "dynamic-btn",
                        event -> {
                            this.viewModel.createNewSession();
                            resetForNewSession();
                        }));
    }

    public void resetForNewSession() {
        this.searchField.setText("");
        this.clearAttachedFiles();
        this.chatContainer.getChildren().clear();
        currentToolGroup = null;

        this.homePage.setAlignment(Pos.CENTER);
        VBox.setMargin(this.searchBox, new Insets(0, 0, 0, 0));
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
