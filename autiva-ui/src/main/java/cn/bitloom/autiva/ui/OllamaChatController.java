package cn.bitloom.autiva.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class OllamaChatController {

    @FXML
    private ImageView ollamaIcon;

    @FXML
    private Button newChatBtn;

    @FXML
    private Button settingsBtn;

    @FXML
    private VBox chatHistoryContainer;

    @FXML
    private ScrollPane chatContentScrollPane;

    @FXML
    private VBox chatContentContainer;

    @FXML
    private ImageView llamaIcon;

    @FXML
    private TextField messageField;

    @FXML
    private Button globeBtn;

    @FXML
    private Button cloudBtn;

    @FXML
    private ComboBox<String> modelSelector;

    @FXML
    private Button sendBtn;

    @FXML
    public void initialize() {
        // 初始化图标
        initializeIcons();

        // 设置模型选择器默认值
        modelSelector.setValue("gpt-oss:20b");

        // 绑定发送按钮事件
        sendBtn.setOnAction(e -> sendMessage());

        // 绑定回车键发送消息
        messageField.setOnAction(e -> sendMessage());

        // 绑定新建聊天按钮事件
        newChatBtn.setOnAction(e -> clearChat());
    }

    private void initializeIcons() {
        // 设置Ollama图标
        try {
            ollamaIcon.setImage(new Image(getClass().getResourceAsStream("ollama-icon.png")));
        } catch (Exception e) {
            // 如果没有图标文件，使用默认图标
            ollamaIcon.setImage(new Image("https://picsum.photos/24/24"));
        }

        // 设置羊驼图标
        try {
            llamaIcon.setImage(new Image(getClass().getResourceAsStream("llama-icon.png")));
        } catch (Exception e) {
            // 如果没有图标文件，使用默认图标
            llamaIcon.setImage(new Image("https://picsum.photos/24/24"));
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            // 添加用户消息到聊天内容区域
            HBox userMessageBox = new HBox(10);
            userMessageBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            Label userLabel = new Label("你");
            userLabel.setStyle("-fx-font-weight: bold;");
            Label messageLabel = new Label(message);
            messageLabel.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 10; -fx-background-radius: 10; -fx-wrap-text: true; -fx-max-width: 500;");
            userMessageBox.getChildren().addAll(userLabel, messageLabel);
            chatContentContainer.getChildren().add(userMessageBox);

            // 添加回复占位符
            HBox replyBox = new HBox(10);
            replyBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            ImageView icon = new ImageView(llamaIcon.getImage());
            icon.setFitWidth(24);
            icon.setFitHeight(24);
            Label replyLabel = new Label("(正在思考...)");
            replyLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 10; -fx-background-radius: 10; -fx-wrap-text: true; -fx-max-width: 500;");
            replyBox.getChildren().addAll(icon, replyLabel);
            chatContentContainer.getChildren().add(replyBox);

            // 清空输入框
            messageField.clear();

            // 自动滚动到底部
            chatContentScrollPane.vvalueProperty().bind(chatContentContainer.heightProperty());

            // 模拟AI回复
            simulateAIReply(replyLabel);
        }
    }

    private void simulateAIReply(Label replyLabel) {
        // 简单的模拟回复
        new Thread(() -> {
            try {
                // 模拟思考时间
                Thread.sleep(1500);

                // 更新UI必须在JavaFX应用线程
                javafx.application.Platform.runLater(() -> {
                    replyLabel.setText("我会JavaFX，可以帮助你创建各种界面和功能。你有什么具体需求吗？");
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void clearChat() {
        // 清空聊天内容
        chatContentContainer.getChildren().clear();
    }
}
