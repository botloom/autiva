package cn.bitloom.bridge.weixin.ilink.model;

import lombok.Data;

@Data
public class LoginContext {

    private final String botToken;
    private final String userId;
    private final String botId;
    private final String baseUrl;

    public LoginContext(String botToken, String userId, String botId, String baseUrl) {
        this.botToken = botToken;
        this.userId = userId;
        this.botId = botId;
        this.baseUrl = baseUrl != null ? baseUrl : "https://ilinkai.weixin.qq.com";
    }
}
