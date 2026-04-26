package cn.bitloom.controller;

import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.AssistantMessageCard;
import cn.bitloom.node.ChatMessage;
import cn.bitloom.node.ToolMessageCard;
import cn.bitloom.node.UserMessageCard;
import cn.bitloom.store.Store;
import cn.bitloom.store.ToolUIBridge;
import cn.bitloom.vm.HomePageViewModel;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
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
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private VBox icon;
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatContainer;
    @FXML
    private ComboBox<ModelTypeEnum> modelSelector;

    @Getter
    private final HomePageViewModel viewModel;
    @Getter
    private final ToolUIBridge toolUIBridge;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.searchButton.setOnAction(event -> this.handleSendMessage());
        this.searchField.setOnAction(event -> this.handleSendMessage());

        this.modelSelector.getItems().addAll(ModelTypeEnum.values());
        this.modelSelector.setValue(Store.selectedModel.get());
        this.updateModelSelectorWidth();
        this.modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Store.selectedModel.set(newVal);
                this.updateModelSelectorWidth();
                log.info("模型已切换为: {}", newVal);
            }
        });

        this.toolUIBridge.setOnNodeAdded(this::addChatNode);

        this.chatContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
        });

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
        Node card = createMessageCard(msg);
        if (card != null) {
            if (card instanceof Region region) {
                double ratio = msg.getType() == ChatMessage.Type.TOOL ? 0.85 : 0.75;
                region.maxWidthProperty().bind(
                        Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(ratio))
                );
            }
            HBox row = createMessageRow(card, msg.getType());
            chatContainer.getChildren().add(row);
        } else {
            log.warn("addChatMessage: createMessageCard returned null for type={}", msg.getType());
        }
    }

    private void addChatNode(Node node) {
        if (node instanceof Region region) {
            region.maxWidthProperty().bind(
                    Bindings.max(100, chatScrollPane.widthProperty().subtract(32).multiply(0.85))
            );
        }
        HBox row = new HBox();
        row.getStyleClass().add("chat-row");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(node);
        chatContainer.getChildren().add(row);
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
            case ASSISTANT -> new AssistantMessageCard(msg);
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

    private void handleSendMessage() {
        if (this.searchField.getText().isBlank()) {
            return;
        }
        if (!this.chatScrollPane.isVisible()) {
            this.animateToChatState();
        }
        String text = this.searchField.getText();
        this.viewModel.addUserMessage(text);
        UserMessage userMessage = UserMessage.builder()
                .text(text)
                .build();
        this.viewModel.sendMessage(userMessage);
        this.searchField.clear();
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
                        "clearButton",
                        "清除",
                        "dynamic-btn",
                        event -> {
                            Store.statusText.set("正在清除对话...");
                            this.viewModel.clear();
                            this.searchField.setText("");
                            this.chatContainer.getChildren().clear();

                            this.homePage.setAlignment(Pos.CENTER);
                            VBox.setMargin(this.searchBox, new Insets(0, 0, 0, 0));
                            this.chatScrollPane.setVisible(false);
                            this.chatScrollPane.setManaged(false);

                            this.icon.setVisible(true);
                            this.icon.setManaged(true);
                            this.icon.setOpacity(0);
                            this.icon.setTranslateY(-60);

                            Timeline timeline = new Timeline();
                            KeyFrame keyFrame = new KeyFrame(Duration.millis(600),
                                    new KeyValue(this.icon.opacityProperty(), 1, Interpolator.EASE_BOTH),
                                    new KeyValue(this.icon.translateYProperty(), 0, Interpolator.EASE_BOTH));

                            timeline.getKeyFrames().add(keyFrame);
                            timeline.setOnFinished(e -> Store.statusText.set("就绪"));
                            timeline.play();
                        }));
    }

}
