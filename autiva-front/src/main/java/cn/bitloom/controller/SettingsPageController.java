package cn.bitloom.controller;

import cn.bitloom.bridge.wechat.WechatILinkClient;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.holder.PageHolder;
import cn.bitloom.store.Store;
import cn.bitloom.vm.SettingsPageViewModel;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsPageController implements Initializable, ButtonBarHolder, PageHolder {

    private final SettingsPageViewModel viewModel;
    private final ApplicationContext applicationContext;

    @FXML
    private VBox settingsPage;
    @FXML
    private VBox userCard;
    @FXML
    private ImageView userAvatar;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userIdLabel;
    @FXML
    private Button loginButton;
    @FXML
    private Button logoutButton;
    @FXML
    private TextField dingTalkClientIdField;
    @FXML
    private PasswordField dingTalkClientSecretField;
    @FXML
    private ImageView weixinQrImageView;
    @FXML
    private VBox weixinQrRow;
    @FXML
    private Label weixinQrHintLabel;
    @FXML
    private VBox weixinConnectedOverlay;
    @FXML
    private VBox weixinExpiredOverlay;
    @FXML
    private Button weixinRebindButton;
    @FXML
    private Button weixinRefreshButton;
    @FXML
    private PasswordField bochaApiKeyField;
    @FXML
    private PasswordField deepseekApiKeyField;
    @FXML
    private TextField deepseekBaseUrlField;
    @FXML
    private TextField deepseekCompletionsPathField;
    @FXML
    private TextField deepseekChatModelField;

    @Getter
    @Setter
    private IndexController indexController;

    private ChangeListener<WechatILinkClient.State> weixinStateListener;
    private ChangeListener<String> userIdListener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bindViewModel();
        this.bindUserCard();
    }

    private void bindViewModel() {
        dingTalkClientIdField.textProperty().bindBidirectional(viewModel.getDingTalkClientId());
        dingTalkClientSecretField.textProperty().bindBidirectional(viewModel.getDingTalkClientSecret());
        bochaApiKeyField.textProperty().bindBidirectional(viewModel.getBochaApiKey());
        deepseekApiKeyField.textProperty().bindBidirectional(viewModel.getDeepseekApiKey());
        deepseekBaseUrlField.textProperty().bindBidirectional(viewModel.getDeepseekBaseUrl());
        deepseekCompletionsPathField.textProperty().bindBidirectional(viewModel.getDeepseekCompletionsPath());
        deepseekChatModelField.textProperty().bindBidirectional(viewModel.getDeepseekChatModel());
    }

    private void bindUserCard() {
        updateUserInfo();
        userIdListener = (obs, oldVal, newVal) -> Platform.runLater(this::updateUserInfo);
        Store.userId.addListener(userIdListener);
    }

    private void updateUserInfo() {
        String userId = Store.userId.get();
        boolean isLoggedIn = userId != null && !"default".equals(userId);
        if (isLoggedIn) {
            userNameLabel.setText(userId);
            userIdLabel.setText("ID: " + userId);
            loginButton.setVisible(false);
            loginButton.setManaged(false);
            logoutButton.setVisible(true);
            logoutButton.setManaged(true);
        } else {
            userNameLabel.setText("点击登录");
            userIdLabel.setText("未登录");
            loginButton.setVisible(true);
            loginButton.setManaged(true);
            logoutButton.setVisible(false);
            logoutButton.setManaged(false);
        }
    }

    @FXML
    private void handleLogin() {
        showLoginDialog();
    }

    @FXML
    private void handleLogout() {
        Store.userId.set("default");
        updateUserInfo();
    }

    private void showLoginDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNIFIED);
        dialog.setTitle("");

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setStyle("-fx-background-color: #ffffff;");
        root.setPrefWidth(340);

        // 设置弹窗图标
        dialog.getIcons().add(userAvatar.getImage());

        // 头像
        ImageView avatarView = new ImageView(userAvatar.getImage());
        avatarView.setFitWidth(64);
        avatarView.setFitHeight(64);

        // 标题
        Label titleLabel = new Label("登录 Autiva");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; " +
                "-fx-font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "-fx-text-fill: #1d1d1f;");

        Label subtitleLabel = new Label("输入你的用户名即可登录");
        subtitleLabel.setStyle("-fx-font-size: 13px; " +
                "-fx-font-family: 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "-fx-text-fill: #86868b;");

        // 输入框
        TextField usernameField = new TextField();
        usernameField.setPromptText("输入用户名");
        usernameField.setStyle("-fx-font-size: 15px; " +
                "-fx-font-family: 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "-fx-background-color: rgba(0,0,0,0.03); -fx-background-radius: 10px; " +
                "-fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1px; -fx-border-radius: 10px; " +
                "-fx-pref-height: 40px; -fx-padding: 8 14; " +
                "-fx-focus-color: #0071e3; -fx-faint-focus-color: rgba(0,113,227,0.1);");
        usernameField.setPrefWidth(260);

        // 按钮行
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: 500; " +
                "-fx-font-family: 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "-fx-background-color: #f5f5f7; -fx-background-radius: 10px; " +
                "-fx-text-fill: #1d1d1f; -fx-pref-height: 40px; -fx-pref-width: 120px; " +
                "-fx-border-color: transparent; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = new Button("登录");
        confirmBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; " +
                "-fx-font-family: 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
                "-fx-background-color: #0071e3; -fx-background-radius: 10px; " +
                "-fx-text-fill: #ffffff; -fx-pref-height: 40px; -fx-pref-width: 120px; " +
                "-fx-border-color: transparent; -fx-cursor: hand;");
        confirmBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            if (!username.isEmpty()) {
                Store.userId.set(username);
                updateUserInfo();
                dialog.close();
            }
        });

        // 回车确认
        usernameField.setOnAction(e -> confirmBtn.fire());

        buttonRow.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(avatarView, titleLabel, subtitleLabel, usernameField, buttonRow);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.setResizable(false);

        dialog.showAndWait();

        // 自动聚焦输入框
        usernameField.requestFocus();
    }

    @Override
    public void show() {
        this.settingsPage.setVisible(true);
        this.settingsPage.setManaged(true);
        viewModel.loadFromStore();
        updateWeixinStatus();
    }

    @Override
    public void hide() {
        this.settingsPage.setVisible(false);
        this.settingsPage.setManaged(false);
    }

    private void updateWeixinStatus() {
        if (weixinStateListener != null) {
            try {
                WechatILinkClient oldClient = applicationContext.getBean(WechatILinkClient.class);
                oldClient.stateProperty().removeListener(weixinStateListener);
            } catch (Exception ignored) {
            }
            weixinStateListener = null;
        }

        try {
            WechatILinkClient client = applicationContext.getBean(WechatILinkClient.class);
            updateWeixinUi(client.getState(), client);

            weixinStateListener = (obs, oldVal, newVal) ->
                    Platform.runLater(() -> updateWeixinUi(newVal, client));
            client.stateProperty().addListener(weixinStateListener);

            weixinRebindButton.setOnAction(event -> handleRebind(client));
            weixinRefreshButton.setOnAction(event -> handleRefresh(client));
        } catch (Exception e) {
            weixinQrRow.setVisible(false);
            weixinQrRow.setManaged(false);
        }
    }

    private void updateWeixinUi(WechatILinkClient.State state, WechatILinkClient client) {
        weixinConnectedOverlay.setVisible(false);
        weixinExpiredOverlay.setVisible(false);

        switch (state) {
            case CONNECTED -> {
                weixinConnectedOverlay.setVisible(true);
                weixinQrHintLabel.setText("微信已绑定，点击重新加载可更换账号");
            }
            case CONNECTING -> {
                weixinQrHintLabel.setText("连接中...");
            }
            case DISCONNECTED -> {
                String qrContent = client.getQrCodeContent();
                if (qrContent != null) {
                    renderQrCode(qrContent);
                }
                weixinQrHintLabel.setText("扫码即可绑定微信");
            }
            case QR_EXPIRED -> {
                weixinExpiredOverlay.setVisible(true);
                weixinQrHintLabel.setText("二维码已过期，请点击重新加载");
            }
        }
    }

    private void handleRebind(WechatILinkClient client) {
        client.restartLogin();
    }

    private void handleRefresh(WechatILinkClient client) {
        client.startLogin();
    }

    private void renderQrCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 160, 160);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            WritableImage fxImage = new WritableImage(width, height);
            fxImage.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
            weixinQrImageView.setImage(fxImage);
        } catch (WriterException e) {
            log.error("生成二维码失败", e);
        }
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return List.of(
                new ButtonBarHolder.ButtonConfig(
                        "saveSettingsButton",
                        "保存",
                        "dynamic-btn",
                        event -> viewModel.save()
                ),
                new ButtonBarHolder.ButtonConfig(
                        "resetSettingsButton",
                        "重置",
                        "dynamic-btn",
                        event -> viewModel.reset()
                )
        );
    }

}
