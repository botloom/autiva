package cn.bitloom.bridge.wechat;

import cn.bitloom.bridge.wechat.ilink.ILinkApiClient;
import cn.bitloom.bridge.wechat.ilink.model.LoginContext;
import cn.bitloom.bridge.wechat.ilink.model.WeixinMessage;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatILinkClient {

    public enum State { CONNECTING, CONNECTED, DISCONNECTED, QR_EXPIRED }

    private static final Path SESSION_FILE = Path.of(System.getProperty("user.home"), ".autiva", "wechat-session.json");

    private final WechatILinkProperties properties;
    private final WechatILinkMessageHandler messageHandler;

    private ILinkApiClient apiClient;
    private ExecutorService pollingExecutor;
    private ExecutorService loginExecutor;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final SimpleObjectProperty<State> state = new SimpleObjectProperty<>(State.CONNECTING);
    @Getter
    private volatile String qrCodeContent;

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public State getState() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED;
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

        loginExecutor.submit(this::tryRestoreOrLogin);
    }

    private void tryRestoreOrLogin() {
        LoginContext savedCtx = loadSession();
        if (savedCtx != null) {
            log.info("发现已保存的微信 iLink 会话，尝试恢复...");
            apiClient.restoreLogin(savedCtx);
            if (apiClient.tryRestoreSession()) {
                log.info("微信 iLink 会话恢复成功，botId = {}", savedCtx.getBotId());
                Platform.runLater(() -> state.set(State.CONNECTED));
                startPolling();
                return;
            }
            log.info("微信 iLink 会话已过期，需要重新扫码登录");
        }
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
                Platform.runLater(() -> state.set(State.DISCONNECTED));
                log.info("微信 iLink 二维码已生成，请扫码登录");

                LoginContext ctx = apiClient.pollLoginStatus(
                        qrResult.getQrcode(),
                        120000
                );

                log.info("微信 iLink 登录成功，botId = {}", ctx.getBotId());
                saveSession(ctx);
                Platform.runLater(() -> state.set(State.CONNECTED));
                startPolling();
            } catch (Exception e) {
                log.error("微信 iLink 登录失败: {}", e.getMessage());
                Platform.runLater(() -> state.set(State.QR_EXPIRED));
            }
        });
    }

    public void restartLogin() {
        stopPolling();
        deleteSession();
        qrCodeContent = null;
        Platform.runLater(() -> state.set(State.CONNECTING));
        startLogin();
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
                    Platform.runLater(() -> state.set(State.DISCONNECTED));
                    stopPolling();
                    deleteSession();
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

    private void saveSession(LoginContext ctx) {
        try {
            JSONObject json = new JSONObject();
            json.put("botToken", ctx.getBotToken());
            json.put("userId", ctx.getUserId());
            json.put("botId", ctx.getBotId());
            json.put("baseUrl", ctx.getBaseUrl());
            Files.writeString(SESSION_FILE, json.toJSONString());
            log.debug("微信 iLink 会话已保存到 {}", SESSION_FILE);
        } catch (Exception e) {
            log.warn("保存微信 iLink 会话失败", e);
        }
    }

    private LoginContext loadSession() {
        try {
            if (!Files.exists(SESSION_FILE)) {
                return null;
            }
            String content = Files.readString(SESSION_FILE);
            JSONObject json = JSON.parseObject(content);
            return new LoginContext(
                    json.getString("botToken"),
                    json.getString("userId"),
                    json.getString("botId"),
                    json.getString("baseUrl")
            );
        } catch (Exception e) {
            log.warn("加载微信 iLink 会话失败", e);
            return null;
        }
    }

    private void deleteSession() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (Exception e) {
            log.warn("删除微信 iLink 会话文件失败", e);
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
