package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.memory.MemoryManager;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话实体类，唯一的状态源。
 * <p>
 * 所有持久化字段直接在本类中定义，序列化为 metadata.json。
 * 瞬态字段（agent、messages、memoryManager）不参与序列化。
 * 编排逻辑（消息循环、记忆事件处理）由 SessionRunner 负责。
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    private String id;
    private String agentId;
    private String userId;
    private SessionTypeEnum sessionType;
    private SessionRespTypeEnum respType;
    private String source;
    private ModelTypeEnum model;

    /** 父会话ID（子 Session 关联父 Session） */
    private String parentId;

    @Builder.Default
    private String title = "新对话";

    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    @Builder.Default
    private Long updateAt = System.currentTimeMillis();

    @Builder.Default
    private SessionState sessionState = SessionState.IDLE;

    // ===== 对话上下文 =====

    @Builder.Default
    private int messageCount = 0;

    @Builder.Default
    private int memoryCursor = 0;

    private String summary;

    // ===== 压缩上下文 =====

    @Builder.Default
    private int contextCapacity = 64000;

    @Builder.Default
    private double compactionThreshold = 0.8;

    @Builder.Default
    private int currentContextLength = 0;

    // ===== 元数据 =====

    private Long savedAt;

    @Builder.Default
    private boolean shutdownInterrupted = false;

    // ===== 瞬态字段（不序列化） =====

    @Getter
    @Setter
    @JsonIgnore
    private Agent agent;

    @Getter
    @Setter
    @JsonIgnore
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    /**
     * 内存列表第一条消息对应磁盘 messages.jsonl 的行号。
     * 压缩后清理内存时推进此值；加载更多历史消息时回退此值。
     * 初始值 = memoryCursor（内存从游标后开始加载）。
     */
    @Getter
    @Setter
    @JsonIgnore
    @Builder.Default
    private int memoryBaseOffset = 0;

    @Getter
    @Setter
    @JsonIgnore
    private MemoryManager memoryManager;

    public Boolean isStop() {
        return this.getSessionState() == SessionState.STOPPED;
    }

}
