package cn.bitloom.agentic.team;

import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.EventFilter;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MessageBus 邮箱 — session 事件流内的合成 MessageEvent（对标 learn-claude-code s13）。
 *
 * <p>投递 = JSONL 追加：写给队友的消息是 {@code branch=teammate.{name}} 的 user 事件
 * （metadata: synthetic + mailbox + from/to），队友唤醒时随 branch 历史自然进入上下文；
 * 写给 Lead 的消息是 root 事件 + notification 标记，复用 SessionMemoryAdvisor 的
 * 自动注入 + 一次性消费机制（push 模型）。
 *
 * <p>{@code consumed} 标记只影响唤醒判定（是否需要再次唤醒），不影响历史加载——
 * 邮件始终保留在队友的 branch 上下文中。
 */
@Slf4j
@Component
public class MailboxService {

    public static final String METADATA_MAILBOX = "mailbox";
    public static final String METADATA_FROM = "from";
    public static final String METADATA_TO = "to";
    public static final String LEAD_ADDRESS = "lead";

    private final FileSystemSessionManager sessionManager;

    public MailboxService(FileSystemSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /** 投递到队友邮箱（branch 内 user 事件，队友下次唤醒时可见） */
    public void deliverToTeammate(String sessionId, String from, String teammateName, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(MessageEvent.METADATA_SYNTHETIC, Boolean.TRUE);
        metadata.put(METADATA_MAILBOX, Boolean.TRUE);
        metadata.put(METADATA_FROM, from);
        metadata.put(METADATA_TO, teammateName);
        MessageEvent message = MessageEvent.builder()
                .sessionId(sessionId)
                .branch("teammate." + teammateName)
                .message(new UserMessage(wrap(from, text)))
                .metadata(metadata)
                .build();
        sessionManager.appendEvent(message);
        log.info("[Mailbox] 投递: session={}, {} -> teammate.{}", sessionId, from, teammateName);
    }

    /** 投递到 Lead（root + notification：下一轮自动注入主智能体上下文并一次性消费） */
    public void deliverToLead(String sessionId, String from, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(MessageEvent.METADATA_SYNTHETIC, Boolean.TRUE);
        metadata.put(MessageEvent.METADATA_NOTIFICATION, Boolean.TRUE);
        metadata.put(METADATA_MAILBOX, Boolean.TRUE);
        metadata.put(METADATA_FROM, from);
        metadata.put(METADATA_TO, LEAD_ADDRESS);
        MessageEvent message = MessageEvent.builder()
                .sessionId(sessionId)
                .message(new UserMessage(wrap(from, text)))
                .metadata(metadata)
                .build();
        sessionManager.appendEvent(message);
        log.info("[Mailbox] 投递: session={}, teammate -> lead", sessionId);
    }

    /** 检索队友 branch 内未消费的邮箱消息（唤醒判定 + 组装触发上下文） */
    public List<MessageEvent> unconsumed(String sessionId, String teammateName) {
        String branch = "teammate." + teammateName;
        return sessionManager.getEvents(sessionId, EventFilter.forBranch(branch)).stream()
                .filter(e -> e instanceof MessageEvent)
                .map(e -> (MessageEvent) e)
                .filter(this::isMailbox)
                .filter(m -> !isConsumed(m))
                .toList();
    }

    /** 标记为已消费（仅影响唤醒判定，事件仍保留在 branch 历史中） */
    public void markConsumed(String sessionId, List<MessageEvent> messages) {
        for (MessageEvent message : messages) {
            try {
                sessionManager.updateEventMetadata(sessionId, message.getId(),
                        MessageEvent.METADATA_CONSUMED, Boolean.TRUE);
            }
            catch (Exception e) {
                log.debug("[Mailbox] 标记 consumed 失败: eventId={}: {}", message.getId(), e.getMessage());
            }
        }
    }

    private boolean isMailbox(MessageEvent event) {
        Object v = event.getMetadata() != null ? event.getMetadata().get(METADATA_MAILBOX) : null;
        return v instanceof Boolean b && b;
    }

    private boolean isConsumed(MessageEvent event) {
        Object v = event.getMetadata() != null ? event.getMetadata().get(MessageEvent.METADATA_CONSUMED) : null;
        return v instanceof Boolean b && b;
    }

    private String wrap(String from, String text) {
        return "<teammate_message from=\"" + from + "\">\n" + text + "\n</teammate_message>";
    }
}
