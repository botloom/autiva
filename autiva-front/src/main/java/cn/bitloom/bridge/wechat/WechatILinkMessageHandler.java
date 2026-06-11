package cn.bitloom.bridge.wechat;

import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.bridge.wechat.ilink.model.MessageItem;
import cn.bitloom.bridge.wechat.ilink.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WechatILinkMessageHandler {

    private static final String SOURCE = "wechat";
    private final SessionManager sessionManager;
    private final AgentManager agentManager;
    private final WechatILinkClient wechatILinkClient;
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    public WechatILinkMessageHandler(SessionManager sessionManager, AgentManager agentManager, @Lazy WechatILinkClient wechatILinkClient) {
        this.sessionManager = sessionManager;
        this.agentManager = agentManager;
        this.wechatILinkClient = wechatILinkClient;
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

        sessionManager.publishMessage(
                session.getId(),
                UserMessage.builder()
                        .text(text.trim())
                        .build()
        );
    }

    private Session bindSession(String userId) {
        Session session = sessionManager.getOrCreate("default", userId, SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.BLOCK, ModelTypeEnum.DEEPSEEK);
        // SessionManager 自动绑定 Agent，无需手动 bindAgentAndStart
        session.getMessageBus().outBoxSubscribe()
                .subscribe(
                        msg -> wechatILinkClient.sendText(userId, msg.getText().trim()),
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
