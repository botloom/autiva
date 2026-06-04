package cn.bitloom.bridge.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.ilink")
public class WechatILinkProperties {

    private int connectTimeoutMs = 35000;
    private int readTimeoutMs = 35000;
    private int writeTimeoutMs = 35000;
    private int httpMaxRetries = 3;
    private int retryBaseDelayMs = 1000;
    private int retryMaxDelayMs = 10000;
    private boolean heartbeatEnabled = true;
    private int heartbeatIntervalMs = 30000;
    private String channelVersion = "2.0.0";
}
