package cn.bitloom.controller;

import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.*;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private TextArea sendField;
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

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.modelSelector.valueProperty().bindBidirectional(Store.selectedModel);

        this.sendButton.setOnAction(event -> this.handleSendMessage());
        this.stopButton.setOnAction(event -> this.viewModel.pauseGeneration());

        // Ctrl+Enter 发送消息，Enter 换行
        this.sendField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown() && !event.isControlDown()) {
                event.consume();
                this.handleSendMessage();
            }
        });

        // TextArea 自动调整高度
        this.sendField.textProperty().addListener((obs, oldVal, newVal) -> adjustTextAreaHeight());

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

        this.viewModel.getMessages().addListener((ListChangeListener<MessageCard>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (MessageCard card : change.getAddedSubList()) {
                        addChatCard(card);
                    }
                }
            }
        });

        if (this.viewModel.hasHistoricalMessages()) {
            this.animateToChatState();
            this.viewModel.prepareHistoricalMessages();
        }
    }

    private void addChatCard(MessageCard card) {
        if (card.getMessageType() == MessageEvent.Type.TOOL && card instanceof ToolMessageCard toolCard) {
            String toolName = toolCard.getToolName();
            addToolToGroup(toolCard, toolName);
            scrollToBottom();
            return;
        }

        // 非 TOOL 消息中断当前工具分组
        currentToolGroup = null;

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

    private void addToolToGroup(Node toolCard, String toolName) {
        if (currentToolGroup != null) {
            currentToolGroup.addToolCard(toolCard, toolName);
        } else {
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
        this.fileTagsPane.setVisible(hasFiles);
        this.fileTagsPane.setManaged(hasFiles);
    }

    private void adjustTextAreaHeight() {
        String text = this.sendField.getText();
        int lineCount = 1;
        if (text != null && !text.isEmpty()) {
            lineCount = (int) text.chars().filter(c -> c == '\n').count() + 1;
        }
        double lineHeight = 22;
        double padding = 16;
        double height = Math.max(48, Math.min(200, lineCount * lineHeight + padding));
        this.sendField.setPrefHeight(height);
    }

    private void scrollToBottom() {
        shouldScrollToBottom.set(true);
    }

    private void loadMoreMessages() {
        isLoadingMore = true;
        List<MessageCard> olderCards = viewModel.loadMoreMessages(30);
        if (olderCards.isEmpty()) {
            isLoadingMore = false;
            return;
        }

        double oldVvalue = chatScrollPane.getVvalue();
        double oldContentHeight = chatContainer.getHeight();

        viewModel.prependHistoricalMessages(olderCards);

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
        this.agentSelector.setValue("default");
        updateAgentSelectorWidth();

        this.agentSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                viewModel.switchAgent(newVal);
                updateAgentSelectorWidth();
            }
        });

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
        this.sendField.setText("");
        this.clearAttachedFiles();
        this.chatContainer.getChildren().clear();
        currentToolGroup = null;

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
