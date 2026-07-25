package cn.bitloom.agentic.event;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * A2UI 用户动作事件(从 UI → Agent)。
 * <p>
 * 用户通过 A2UI 界面触发的交互,通过 EventBus inBox 回流到 Agent。
 */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
public final class A2UIActionEvent extends AbstractEvent {

    @Builder.Default
    private EventTypeEnum eventType = EventTypeEnum.A2UI_ACTION;

    /** Surface ID */
    private String surfaceId;

    /** 触发动作的组件 ID */
    private String sourceComponentId;

    /** 动作名称(Agent 识别) */
    private String actionName;

    /** 上下文数据(已解析的值) */
    private Map<String, Object> context;

    @Override
    public EventTypeEnum getEventType() { return eventType; }

    /** 静态工厂方法 */
    public static A2UIActionEvent of(String sessionId, String surfaceId,
                                     String sourceComponentId, String actionName,
                                     Map<String, Object> context) {
        return A2UIActionEvent.builder()
                .sessionId(sessionId)
                .surfaceId(surfaceId)
                .sourceComponentId(sourceComponentId)
                .actionName(actionName)
                .context(context)
                .build();
    }
}
