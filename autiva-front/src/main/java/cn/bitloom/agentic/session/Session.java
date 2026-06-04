package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
@Builder
public class Session {

    private String id;
    private AgentIdentityEnum agentId;
    private SessionTypeEnum type;
    private SessionRespTypeEnum respType;
    private ModelTypeEnum model;
    private String source;
    private String target;
    private String parentId;

    @JSONField(serialize = false)
    @Builder.Default
    private Map<MessageChannel, List<Message>> channelMessages = new EnumMap<>(MessageChannel.class);

    @Builder.Default
    private Integer memoryCursor = 0;

    @Builder.Default
    private Integer journalCursor = 0;

    @Builder.Default
    private SessionState state = SessionState.IDLE;

    private String title;

    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    @JSONField(serialize = false)
    public Map<MessageChannel, List<Message>> getChannelMessages() {
        if (this.channelMessages == null) {
            this.channelMessages = new EnumMap<>(MessageChannel.class);
        }
        return this.channelMessages;
    }

    @JSONField(serialize = false)
    public List<Message> getChannelMessages(MessageChannel channel) {
        return this.getChannelMessages().computeIfAbsent(channel, k -> new ArrayList<>());
    }

    @JSONField(serialize = false)
    public List<Message> getMessages() {
        return getChannelMessages(MessageChannel.USER);
    }

    @JSONField(serialize = false)
    public Map<MessageChannel, List<Message>> getAllChannelMessages() {
        return this.getChannelMessages();
    }

    public Integer getMemoryCursor() {
        return this.memoryCursor != null ? this.memoryCursor : 0;
    }

    public Integer getJournalCursor() {
        return this.journalCursor != null ? this.journalCursor : 0;
    }

    @JSONField(serialize = false)
    public String getDisplayTitle() {
        if (this.title != null && !this.title.isBlank()) {
            return this.title;
        }
        List<Message> userMessages = getMessages();
        for (Message msg : userMessages) {
            if (msg.getMessageType() == MessageType.USER && msg.getText() != null && !msg.getText().isBlank()) {
                String text = msg.getText().replace("\n", " ").trim();
                return text.length() > 20 ? text.substring(0, 20) + "..." : text;
            }
        }
        return "新对话";
    }

    public Long getCreatedAt() {
        return this.createdAt != null ? this.createdAt : 0L;
    }
}
