package cn.bitloom.bridge.weixin;

import cn.bitloom.agentic.event.Event;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.bridge.weixin.ilink.model.MessageItem;
import cn.bitloom.bridge.weixin.ilink.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "weixin.ilink", name = "enabled", havingValue = "true")
public class WeixinILinkMessageHandler {

    private static final String SOURCE = "wechat";
    private final SessionManager sessionManager;
    private final WeixinILinkClient weixinILinkClient;
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    public WeixinILinkMessageHandler(SessionManager sessionManager, @Lazy WeixinILinkClient weixinILinkClient) {
        this.sessionManager = sessionManager;
        this.weixinILinkClient = weixinILinkClient;
    }

    public void handleMessage(WeixinMessage message) {
        String userId = message.getFromUserId();
        String text = extractText(message);
        if (text == null || text.isBlank()) {
            return;
        }

        Session session;
        if (sessionMap.containsKey(userId)) {
            session = sessionMap.get(userId);
        } else {
            session = bindSession(userId);
        }

        EventBus.inBoxPublish(
                session.getId(),
                UserMessage.builder()
                        .text(text.trim())
                        .build()
        );
    }

    private Session bindSession(String userId) {
        Session session = sessionManager.getOrCreate(SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.BLOCK, userId);
        EventBus.outBoxSubscribe()
                .filter(event -> event.getSessionId().equals(session.getId()))
                .map(Event::getMessage)
                .subscribe(
                        msg -> weixinILinkClient.sendText(userId, msg.getText().trim()),
                        error -> log.error("微信回复发送失败，userId = {}", userId, error),
                        () -> log.info("微信消息处理完成，userId = {}", userId)
                );
        sessionMap.put(userId, session);
        return session;
    }

    private String extractText(WeixinMessage message) {
        if (message.getItemList() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (MessageItem item : message.getItemList()) {
            if (item.getTextItem() != null && item.getTextItem().getText() != null) {
                sb.append(item.getTextItem().getText());
            }
        }
        return sb.toString();
    }
}
