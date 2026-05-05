package cn.bitloom.controller;

import cn.bitloom.bridge.weixin.WeixinILinkClient;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.window.WindowChromeHelper;
import cn.bitloom.window.WindowManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class WeixinLoginController implements WindowManager.StageAware, DialogHolder {

    @FXML private BorderPane rootContainer;
    @FXML private HBox toolbar;
    @FXML private ImageView qrCodeImageView;
    @FXML private VBox loadingPane;
    @FXML private VBox successPane;
    @FXML private VBox errorPane;
    @FXML private Label errorText;
    @FXML private Label statusLabel;
    @FXML private Button closeBtn;

    @Getter
    private Stage stage;

    private WeixinILinkClient weixinILinkClient;
    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weixin-login");
        t.setDaemon(true);
        return t;
    });
    private ChangeListener<Boolean> connectedListener;

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> cleanup());

        Platform.runLater(() ->
                WindowChromeHelper.setup(stage, toolbar, rootContainer, null, null, closeBtn, 320, 400)
        );
    }

    public void setWeixinILinkClient(WeixinILinkClient client) {
        this.weixinILinkClient = client;
        initLogin();
    }

    private void initLogin() {
        if (weixinILinkClient == null) {
            showError("微信 iLink 未启用");
            return;
        }

        if (weixinILinkClient.isConnected()) {
            showSuccess();
            return;
        }

        connectedListener = (obs, oldVal, newVal) -> {
            if (newVal) {
                Platform.runLater(this::showSuccess);
            }
        };
        weixinILinkClient.connectedProperty().addListener(connectedListener);

        String qrContent = weixinILinkClient.getQrCodeContent();
        if (qrContent != null) {
            showQrCode(qrContent);
        } else {
            loginExecutor.submit(() -> {
                weixinILinkClient.startLogin();
                String content = weixinILinkClient.getQrCodeContent();
                if (content != null) {
                    Platform.runLater(() -> showQrCode(content));
                } else {
                    Platform.runLater(() -> showError("二维码生成失败"));
                }
            });
        }
    }

    private void showQrCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 200, 200);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            qrCodeImageView.setImage(fxImage);
            loadingPane.setVisible(false);
            successPane.setVisible(false);
            errorPane.setVisible(false);
            statusLabel.setText("等待扫码...");
        } catch (WriterException e) {
            log.error("生成二维码失败", e);
            showError("二维码生成失败");
        }
    }

    private void showSuccess() {
        loadingPane.setVisible(false);
        successPane.setVisible(true);
        errorPane.setVisible(false);
        qrCodeImageView.setVisible(false);
        statusLabel.setText("已登录");
    }

    private void showError(String message) {
        loadingPane.setVisible(false);
        successPane.setVisible(false);
        errorPane.setVisible(true);
        qrCodeImageView.setVisible(false);
        errorText.setText(message);
        statusLabel.setText("登录失败");
    }

    private void cleanup() {
        if (weixinILinkClient != null && connectedListener != null) {
            weixinILinkClient.connectedProperty().removeListener(connectedListener);
            connectedListener = null;
        }
    }

    @Override
    public double getWidth() {
        return 320;
    }

    @Override
    public double getHeight() {
        return 400;
    }

    @Override
    public StageStyle getStageStyle() {
        return StageStyle.TRANSPARENT;
    }
}
