package cn.bitloom.bridge.wechat.ilink;

import cn.bitloom.bridge.wechat.ilink.model.LoginContext;
import cn.bitloom.bridge.wechat.ilink.model.WeixinMessage;
import cn.bitloom.bridge.wechat.WechatILinkProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ILinkApiClient implements AutoCloseable {

    private static final String BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String GET_QRCODE_URL = BASE_URL + "/ilink/bot/get_bot_qrcode?bot_type=3";
    private static final String GET_QRCODE_STATUS_URL = BASE_URL + "/ilink/bot/get_qrcode_status";
    private static final String GETUPDATES_PATH = "/ilink/bot/getupdates";
    private static final String SENDMESSAGE_PATH = "/ilink/bot/sendmessage";

    private final WechatILinkProperties properties;
    private final HttpClient httpClient;
    private final SecureRandom random = new SecureRandom();

    private volatile LoginContext loginContext;
    private volatile String cursor = "";
    private final Map<String, String> contextTokenMap = new ConcurrentHashMap<>();

    public ILinkApiClient(WechatILinkProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public QRCodeResult getQRCode() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GET_QRCODE_URL))
                .GET()
                .header("iLink-App-ClientVersion", "1")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = JSON.parseObject(response.body());

        String qrcode = json.getString("qrcode");
        String qrcodeImgContent = json.getString("qrcode_img_content");
        return new QRCodeResult(qrcode, qrcodeImgContent);
    }

    public LoginContext pollLoginStatus(String qrcode, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (!Thread.currentThread().isInterrupted()) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("login timeout");
            }

            String url = GET_QRCODE_STATUS_URL + "?qrcode=" + qrcode;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("iLink-App-ClientVersion", "1")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSON.parseObject(response.body());
            String status = json.getString("status");

            if ("wait".equalsIgnoreCase(status)) {
                Thread.sleep(2000);
                continue;
            }
            if ("scaned".equalsIgnoreCase(status) || "scanned".equalsIgnoreCase(status)) {
                Thread.sleep(2000);
                continue;
            }
            if ("expired".equalsIgnoreCase(status)) {
                throw new RuntimeException("qrcode expired");
            }
            if ("confirmed".equalsIgnoreCase(status)) {
                String botToken = json.getString("bot_token");
                String botId = json.getString("ilink_bot_id");
                String userId = json.getString("ilink_user_id");
                String baseUrl = json.getString("baseurl");

                loginContext = new LoginContext(botToken, userId, botId, baseUrl);
                return loginContext;
            }

            Thread.sleep(2000);
        }

        throw new RuntimeException("login cancelled");
    }

    public List<WeixinMessage> getUpdates() throws Exception {
        requireLogin();

        JSONObject body = new JSONObject();
        body.put("get_updates_buf", cursor);
        body.put("base_info", new JSONObject().fluentPut("channel_version", properties.getChannelVersion()));

        String bodyStr = body.toJSONString();
        HttpRequest request = buildPostRequest(GETUPDATES_PATH, bodyStr);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = JSON.parseObject(response.body());

        int ret = json.getIntValue("ret");
        if (ret == -14 || json.getIntValue("errcode") == -14) {
            clearState();
            throw new SessionExpiredException("session expired");
        }

        String newCursor = json.getString("get_updates_buf");
        if (newCursor != null) {
            cursor = newCursor;
        }

        JSONArray msgsArray = json.getJSONArray("msgs");
        if (msgsArray == null || msgsArray.isEmpty()) {
            return Collections.emptyList();
        }

        List<WeixinMessage> messages = new ArrayList<>();
        for (int i = 0; i < msgsArray.size(); i++) {
            WeixinMessage msg = msgsArray.getObject(i, WeixinMessage.class);
            if (msg.getFromUserId() != null
                    && msg.getContextToken() != null
                    && !msg.getContextToken().isBlank()) {
                contextTokenMap.put(msg.getFromUserId(), msg.getContextToken());
            }
            messages.add(msg);
        }

        return messages;
    }

    public void sendText(String toUserId, String text) throws Exception {
        requireLogin();
        String contextToken = contextTokenMap.get(toUserId);
        if (contextToken == null) {
            throw new ILinkException("missing latest context token for userId=" + toUserId);
        }

        JSONObject msgObj = new JSONObject();
        msgObj.put("to_user_id", toUserId);
        msgObj.put("client_id", generateClientId());
        msgObj.put("context_token", contextToken);

        JSONArray itemList = new JSONArray();
        JSONObject textItem = new JSONObject();
        textItem.put("type", 1);
        textItem.put("text_item", new JSONObject().fluentPut("text", text));
        itemList.add(textItem);
        msgObj.put("item_list", itemList);

        JSONObject body = new JSONObject();
        body.put("msg", msgObj);
        body.put("base_info", new JSONObject().fluentPut("channel_version", properties.getChannelVersion()));

        String bodyStr = body.toJSONString();
        HttpRequest request = buildPostRequest(SENDMESSAGE_PATH, bodyStr);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = JSON.parseObject(response.body());

        int ret = json.getIntValue("ret");
        if (ret == -14 || json.getIntValue("errcode") == -14) {
            clearState();
            throw new SessionExpiredException("session expired");
        }
        if (ret != 0) {
            throw new ILinkException("send message failed: ret=" + ret + ", errcode=" + json.get("errcode") + ", errmsg=" + json.get("errmsg"));
        }
    }

    public void clearContext(String userId) {
        contextTokenMap.remove(userId);
    }

    public void clearAllContexts() {
        contextTokenMap.clear();
    }

    public LoginContext getLoginContext() {
        return loginContext;
    }

    public void restoreLogin(LoginContext ctx) {
        this.loginContext = ctx;
    }

    public boolean tryRestoreSession() {
        if (loginContext == null) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("get_updates_buf", "");
            body.put("base_info", new JSONObject().fluentPut("channel_version", properties.getChannelVersion()));

            String bodyStr = body.toJSONString();
            HttpRequest request = buildPostRequest(GETUPDATES_PATH, bodyStr);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSON.parseObject(response.body());

            int ret = json.getIntValue("ret");
            if (ret == -14 || json.getIntValue("errcode") == -14) {
                clearState();
                return false;
            }

            String newCursor = json.getString("get_updates_buf");
            if (newCursor != null) {
                cursor = newCursor;
            }

            JSONArray msgsArray = json.getJSONArray("msgs");
            if (msgsArray != null) {
                for (int i = 0; i < msgsArray.size(); i++) {
                    WeixinMessage msg = msgsArray.getObject(i, WeixinMessage.class);
                    if (msg.getFromUserId() != null
                            && msg.getContextToken() != null
                            && !msg.getContextToken().isBlank()) {
                        contextTokenMap.put(msg.getFromUserId(), msg.getContextToken());
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("恢复微信 iLink 会话失败: {}", e.getMessage());
            clearState();
            return false;
        }
    }

    public void clearState() {
        cursor = "";
        contextTokenMap.clear();
        loginContext = null;
    }

    private void requireLogin() {
        if (loginContext == null) {
            throw new NotLoginException("not logged in");
        }
    }

    private HttpRequest buildPostRequest(String path, String bodyStr) {
        String baseUrl = loginContext != null && loginContext.getBaseUrl() != null
                ? loginContext.getBaseUrl()
                : BASE_URL;
        String url = normalize(baseUrl) + path;

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .header("AuthorizationType", "ilink_bot_token")
                .header("Authorization", "Bearer " + loginContext.getBotToken())
                .header("X-WECHAT-UIN", generateWechatUin())
                .build();
    }

    private String generateWechatUin() {
        int randomUint32 = random.nextInt();
        return java.util.Base64.getEncoder().encodeToString(String.valueOf(randomUint32).getBytes(StandardCharsets.UTF_8));
    }

    private String generateClientId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String normalize(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public void close() {
        clearState();
    }

    public static class QRCodeResult {
        private final String qrcode;
        private final String qrcodeImgContent;

        public QRCodeResult(String qrcode, String qrcodeImgContent) {
            this.qrcode = qrcode;
            this.qrcodeImgContent = qrcodeImgContent;
        }

        public String getQrcode() {
            return qrcode;
        }

        public String getQrcodeImgContent() {
            return qrcodeImgContent;
        }
    }

    public static class SessionExpiredException extends RuntimeException {
        public SessionExpiredException(String message) {
            super(message);
        }
    }

    public static class NotLoginException extends RuntimeException {
        public NotLoginException(String message) {
            super(message);
        }
    }

    public static class ILinkException extends RuntimeException {
        public ILinkException(String message) {
            super(message);
        }
    }
}
