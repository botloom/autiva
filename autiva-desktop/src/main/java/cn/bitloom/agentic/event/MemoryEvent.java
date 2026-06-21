package cn.bitloom.agentic.event;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 记忆事件，统一发布到 EventBus.inBox，由 Session 分类处理。
 * <p>
 * 类型：
 * - CONTEXT_COMPACT：上下文需要压缩（由 UsageAdvisor 在 token 超阈值时发布）
 */
@Getter
@Setter
@SuperBuilder
public final class MemoryEvent extends AbstractEvent {

    public enum Type {
        CONTEXT_COMPACT
    }

    private Type type;
    private String agentId;
    /** 当前 token 使用量（CONTEXT_COMPACT 用） */
    private int currentTokens;
    /** 模型上下文上限（CONTEXT_COMPACT 用） */
    private int maxTokens;

    public static MemoryEvent contextCompact(String sessionId, String agentId,
                                              int currentTokens, int maxTokens) {
        return MemoryEvent.builder()
                .sessionId(sessionId)
                .type(Type.CONTEXT_COMPACT)
                .agentId(agentId)
                .currentTokens(currentTokens)
                .maxTokens(maxTokens)
                .build();
    }
}
