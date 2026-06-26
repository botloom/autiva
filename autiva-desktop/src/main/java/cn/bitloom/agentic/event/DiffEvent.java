package cn.bitloom.agentic.event;

import cn.bitloom.agentic.diff.FileDiff;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Diff 事件（从 Agent → UI）。
 * <p>
 * 当 DiffService 生成新的 Diff 时发布此事件，
 * 通过 EventBus outBox 流转到 RightPanelController 刷新 diff 列表。
 */
@Getter
@Setter
@SuperBuilder
public final class DiffEvent extends AbstractEvent {

    /** 文件 Diff 数据 */
    private FileDiff diff;

    /** 静态工厂方法 */
    public static DiffEvent of(String sessionId, FileDiff diff) {
        return DiffEvent.builder().sessionId(sessionId).diff(diff).build();
    }
}
