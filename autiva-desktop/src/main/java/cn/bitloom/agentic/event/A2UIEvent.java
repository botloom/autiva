package cn.bitloom.agentic.event;

import cn.bitloom.agentic.a2ui.A2UIMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A2UI 事件(从 Agent → UI)。
 * <p>
 * 携带 A2UI 消息体,通过 EventBus outBox 流转到 UI 渲染层。
 */
@Getter
@Setter
@SuperBuilder
public final class A2UIEvent extends AbstractEvent {

    /** A2UI 消息体 */
    private A2UIMessage message;

    /** 静态工厂方法 */
    public static A2UIEvent of(String sessionId, A2UIMessage message) {
        return A2UIEvent.builder().sessionId(sessionId).message(message).build();
    }
}