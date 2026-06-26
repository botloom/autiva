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
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bindViewModel();
    }

    private void bindViewModel() {
        bochaApiKeyField.textProperty().bindBidirectional(viewModel.getBochaApiKey());
        deepseekApiKeyField.textProperty().bindBidirectional(viewModel.getDeepseekApiKey());
        deepseekBaseUrlField.textProperty().bindBidirectional(viewModel.getDeepseekBaseUrl());
        deepseekCompletionsPathField.textProperty().bindBidirectional(viewModel.getDeepseekCompletionsPath());
        deepseekChatModelField.textProperty().bindBidirectional(viewModel.getDeepseekChatModel());
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
