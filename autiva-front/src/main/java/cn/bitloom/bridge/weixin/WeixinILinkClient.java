package cn.bitloom.bridge.weixin;

import cn.bitloom.bridge.weixin.ilink.ILinkApiClient;
import cn.bitloom.bridge.weixin.ilink.model.LoginContext;
import cn.bitloom.bridge.weixin.ilink.model.WeixinMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "weixin.ilink", name = "enabled", havingValue = "true")
public class WeixinILinkClient {

    private final WeixinILinkProperties properties;
    private final WeixinILinkMessageHandler messageHandler;

    private ILinkApiClient apiClient;
    private ExecutorService pollingExecutor;
    private ExecutorService loginExecutor;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final SimpleBooleanProperty connected = new SimpleBooleanProperty(false);
    private volatile String qrCodeContent;

    public ReadOnlyBooleanProperty connectedProperty() {
        return connected;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getQrCodeContent() {
        return qrCodeContent;
    }

    @PostConstruct
    public void init() {
        apiClient = new ILinkApiClient(properties);

        pollingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ilink-polling");
            t.setDaemon(true);
            return t;
        });

        loginExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ilink-login");
            t.setDaemon(true);
            return t;
        });

        startLogin();
    }

    public void startLogin() {
        if (apiClient == null) {
            return;
        }
        loginExecutor.submit(() -> {
            try {
                ILinkApiClient.QRCodeResult qrResult = apiClient.getQRCode();
                qrCodeContent = qrResult.getQrcodeImgContent();
                log.info("微信 iLink 二维码已生成，请扫码登录");

                LoginContext ctx = apiClient.pollLoginStatus(
                        qrResult.getQrcode(),
                        120000
                );

                log.info("微信 iLink 登录成功，botId = {}", ctx.getBotId());
                Platform.runLater(() -> connected.set(true));
                startPolling();
            } catch (Exception e) {
                log.error("微信 iLink 登录失败: {}", e.getMessage());
                Platform.runLater(() -> connected.set(false));
            }
        });
    }

    private void startPolling() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        pollingExecutor.submit(() -> {
            log.info("微信 iLink 消息轮询已启动");
            while (polling.get()) {
                try {
                    List<WeixinMessage> messages = apiClient.getUpdates();
                    for (WeixinMessage msg : messages) {
                        try {
                            messageHandler.handleMessage(msg);
                        } catch (Exception e) {
                            log.error("处理微信消息失败，fromUserId = {}", msg.getFromUserId(), e);
                        }
                    }
                } catch (ILinkApiClient.SessionExpiredException e) {
                    log.warn("微信 iLink 会话过期，需要重新登录: {}", e.getMessage());
                    Platform.runLater(() -> connected.set(false));
                    stopPolling();
                    startLogin();
                } catch (Exception e) {
                    log.error("微信 iLink 消息轮询异常", e);
                    if (polling.get()) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            log.info("微信 iLink 消息轮询已停止");
        });
    }

    private void stopPolling() {
        polling.set(false);
    }

    public void sendText(String userId, String text) {
        try {
            apiClient.sendText(userId, text);
        } catch (Exception e) {
            log.error("发送微信消息失败，userId = {}", userId, e);
        }
    }

    @PreDestroy
    public void destroy() {
        stopPolling();
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.shutdownNow();
        }
        if (loginExecutor != null && !loginExecutor.isShutdown()) {
            loginExecutor.shutdownNow();
        }
        if (apiClient != null) {
            try {
                apiClient.close();
            } catch (Exception e) {
                log.error("关闭微信 iLink 客户端失败", e);
            }
        }
        log.info("微信 iLink 客户端已关闭");
    }
}
