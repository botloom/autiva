package cn.bitloom.controller;

import cn.bitloom.agentic.agent.ModelEnum;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.node.SvgImageView;
import cn.bitloom.service.SpeechRecognitionService;
import cn.bitloom.store.Store;
import cn.bitloom.util.MarkdownUtil;
import cn.bitloom.vm.HomePageViewModel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

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
    private Button voiceButton;
    @FXML
    private SvgImageView voiceIcon;
    @FXML
    private VBox icon;
    @FXML
    private WebView webView;
    @FXML
    private ComboBox<ModelEnum> modelSelector;

    @Getter
    private final HomePageViewModel viewModel;
    @Getter
    private final SpeechRecognitionService speechRecognitionService;

    @Getter
    @Setter
    private IndexController indexController;
    private WebEngine webEngine;
    private StringBuilder streamMessage = new StringBuilder();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.searchButton.setOnAction(event -> this.handleSendMessage());
        this.searchField.setOnAction(event -> this.handleSendMessage());
        this.voiceButton.setOnAction(event -> this.handleVoiceButton());

        this.modelSelector.getItems().addAll(ModelEnum.values());
        this.modelSelector.setValue(Store.selectedModel.get());
        this.updateModelSelectorWidth();
        this.modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Store.selectedModel.set(newVal);
                this.updateModelSelectorWidth();
                log.info("模型已切换为: {}", newVal);
            }
        });

        this.webEngine = this.webView.getEngine();
        this.webEngine.setJavaScriptEnabled(true);
        this.webEngine.loadContent(this.loadChatHtmlTemplate());

        this.webEngine.getLoadWorker().stateProperty().addListener((obState, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                List<Message> messages = this.viewModel.getHistoricalMessages();
                if (!messages.isEmpty()) {
                    this.animateToChatState();
                    JSONArray jsonArray = (JSONArray) JSON.toJSON(messages);
                    for (int i = 0; i < jsonArray.size(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.getString("messageType").equals(MessageType.ASSISTANT.name())) {
                            String text = MarkdownUtil.renderMarkdown(jsonObject.getString("text"));
                            jsonObject.put("text", text);
                        }
                    }
                    String script = String.format("window.chat.init(%s);", jsonArray.toJSONString());
                    this.webEngine.executeScript(script);
                }
                this.viewModel.getMessage().addListener((ob, oldMessage, newMessage) -> {
                    if (Objects.isNull(newMessage)) {
                        return;
                    }
                    JSONObject jsonObject = (JSONObject) JSON.toJSON(newMessage);
                    if (jsonObject.getString("messageType").equals(MessageType.ASSISTANT.name())) {
                        this.streamMessage.append(jsonObject.getString("text"));
                        jsonObject.put("text", MarkdownUtil.renderMarkdown(this.streamMessage.toString()));
                        String script = String.format("window.chat.add(%s);", jsonObject.toJSONString());
                        this.webEngine.executeScript(script);
                        if (StringUtils.isNotBlank(jsonObject.getJSONObject("metadata").getString("finishReason"))) {
                            this.streamMessage = new StringBuilder();
                        }
                    } else {
                        this.streamMessage = new StringBuilder();
                        String script = String.format("window.chat.add(%s);", jsonObject.toJSONString());
                        this.webEngine.executeScript(script);
                    }
                });
            }
        });
    }

    private void handleVoiceButton() {
        if (this.speechRecognitionService.isRecording()) {
            this.voiceButton.getStyleClass().remove("home-page__icon-btn--active");
            this.speechRecognitionService.stopRecordingAndTranscribe()
                    .thenAccept(text -> {
                        if (StringUtils.isNotBlank(text)) {
                            Platform.runLater(() -> {
                                this.searchField.setText(text);
                                this.searchField.requestFocus();
                                this.searchField.end();
                            });
                        }
                    });
        } else {
            this.voiceButton.getStyleClass().add("home-page__icon-btn--active");
            this.speechRecognitionService.startRecording();
        }
    }

    private String loadChatHtmlTemplate() {
        try (InputStream is = getClass().getResourceAsStream("/cn/bitloom/html/chat.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Failed to load chat.html template", e);
            return "<html><body><p>Error loading chat template</p></body></html>";
        }
    }

    private void executeScript(String script) {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(script);
            } catch (Exception e) {
                log.error("Error executing script: {}", script, e);
            }
        });
    }

    private void handleSendMessage() {
        if (this.searchField.getText().isBlank()) {
            return;
        }
        if (!this.webView.isVisible()) {
            this.animateToChatState();
        }
        UserMessage userMessage = UserMessage.builder()
                .text(this.searchField.getText())
                .build();
        executeScript(String.format("window.chat.add(%s);", JSON.toJSONString(userMessage)));
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

            this.webView.setVisible(true);
            this.webView.setManaged(true);
        });

        timeline.play();
    }

    private void updateModelSelectorWidth() {
        ModelEnum selectedModel = this.modelSelector.getValue();
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
                            this.executeScript("window.chat.clear();");

                            this.homePage.setAlignment(Pos.CENTER);
                            VBox.setMargin(this.searchBox, new Insets(0, 0, 0, 0));
                            this.webView.setVisible(false);
                            this.webView.setManaged(false);

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
