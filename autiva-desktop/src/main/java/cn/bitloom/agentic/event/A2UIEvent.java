package cn.bitloom.agentic.event;

import cn.bitloom.agentic.a2ui.A2UIMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * A2UI 事件(从 Agent → UI)。
 * <p>
 * 携带 A2UI 消息体,通过 EventBus outBox 流转到 UI 渲染层。
 */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
public final class A2UIEvent extends AbstractEvent {

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.A2UI;

    /** A2UI 消息体 */
    private A2UIMessage message;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    /** 静态工厂方法 */
    public static A2UIEvent of(String sessionId, A2UIMessage message) {
        return A2UIEvent.builder().sessionId(sessionId).message(message).build();
    }
}
