package cn.bitloom.bridge.dingtalk;

import cn.bitloom.config.ConfigManager;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dingtalk.app", name = {"client-id", "client-secret"})
public class DingTalkMessageListener {

    private OpenDingTalkClient client;
    private final ConfigManager configManager;
    private final DingTalkMessageConsumer botEchoTextConsumer;


    @PostConstruct
    public void init() throws Exception {
        client = OpenDingTalkStreamClientBuilder
                .custom()
                .credential(new AuthClientCredential(configManager.getDingTalkClientId(), configManager.getDingTalkClientSecret()))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, botEchoTextConsumer)
                .build();
        client.start();
    }

    @PreDestroy
    public void destroy() throws Exception {
        client.stop();
    }
}