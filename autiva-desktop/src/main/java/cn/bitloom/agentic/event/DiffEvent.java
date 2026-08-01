package cn.bitloom.agentic.event;

import cn.bitloom.agentic.tool.file.FileDiff;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Diff 事件（从 Agent → UI）。
 * <p>
 * 当 DiffService 生成新的 Diff 时发布此事件，
 * 通过 agent 事件流传入 ViewModel，由 diffHandler 刷新 diff 列表。
 * <p>
 * DiffEvent 不参与 events.jsonl 持久化
 * （无 message 字段，被 SessionMemoryAdvisor 的 MessageFilter 过滤）。
 */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
public final class DiffEvent extends AbstractEvent {

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.DIFF;

    /** 文件 Diff 数据 */
    private FileDiff diff;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    /** 静态工厂方法 */
    public static DiffEvent of(String sessionId, FileDiff diff) {
        return DiffEvent.builder().sessionId(sessionId).diff(diff).build();
    }
}
