package cn.bitloom.agentic.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * 压缩事件：在上下文压缩完成时持久化，UI 在聊天消息展示处渲染为
 * "上下文已压缩"提示卡片，让用户感知到此处发生了上下文压缩。
 * <p>
 * 非 MessageEvent，因此 SessionMemoryAdvisor.before() 只读 MessageEvent 时
 * 自动排除，不会进入 LLM context。
 */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
public final class CompactionEvent extends AbstractEvent {

    public enum Status { STARTED, COMPLETED }

    @lombok.Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.COMPACT;

    /** 压缩策略名（如 recursive-summarization） */
    private String strategy;
    /** 归档事件数 */
    private int archivedCount;
    /** 保留事件数 */
    private int activeCount;
    /** 压缩状态 */
    @lombok.Builder.Default
    private Status status = Status.COMPLETED;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    public static CompactionEvent completed(String sessionId, String strategy, int archivedCount, int activeCount) {
        return CompactionEvent.builder()
                .sessionId(sessionId)
                .strategy(strategy)
                .archivedCount(archivedCount)
                .activeCount(activeCount)
                .status(Status.COMPLETED)
                .build();
    }
}
