package cn.bitloom.bridge.dingtalk;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dingtalk.app", name = {"client-id", "client-secret"})
public class DingTalkMessageConsumer implements OpenDingTalkCallbackListener<ChatbotMessage, Void> {

    private final FileSystemSessionManager fileSystemSessionManager;
    private static final String SOURCE = "dingTalk";
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @Override
    public Void execute(ChatbotMessage message) {
        Session session;
        if (this.sessionMap.containsKey(message.getConversationId())) {
            session = this.sessionMap.get(message.getConversationId());
        } else {
            session = this.bindSession(message);
        }
        EventBus.publishIn(
                MessageEvent.userMessage(session.getId(), message.getText().getContent().trim())
        );
        return null;
    }

    //todo
    private Session bindSession(ChatbotMessage botMessage) {
//        Session session = sessionManager.getOrCreate("default", botMessage.getConversationId(), SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.BLOCK, ModelTypeEnum.DEEPSEEK);
//        // SessionManager 自动绑定 Agent，无需手动 bindAgentAndStart
//        session.getMessageBus().outBoxSubscribe()
//                .subscribe(
//                        message -> {
//                            try {
//                                BotReplier.fromWebhook(botMessage.getSessionWebhook()).replyMarkdown("aaa", message.getText().trim());
//                            } catch (IOException e) {
//                                throw new RuntimeException(e);
//                            }
//                        },
//                        error -> log.error("Failed to send message", error),
//                        () -> log.info("Message processing completed")
//                );
//        this.sessionMap.put(botMessage.getConversationId(), session);
//        return session;
        return null;
    }

}
