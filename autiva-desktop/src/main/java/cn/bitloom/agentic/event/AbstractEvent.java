package cn.bitloom.agentic.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 所有事件的抽象基类。
 * <p>
 * 使用 @JsonTypeInfo + @JsonSubTypes 支持 Jackson 多态序列化/反序列化，
 * 通过已有的 eventType 字段作为类型标识（EXISTING_PROPERTY），
 * 避免新增 @type 字段污染序列化结构。
 * <p>
 * 反序列化时根据 eventType 值（MESSAGE/MEMORY/A2UI/A2UI_ACTION/DIFF/UI_CARD）还原正确的子类类型。
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageEvent.class, name = "MESSAGE"),
        @JsonSubTypes.Type(value = MemoryEvent.class, name = "MEMORY"),
        @JsonSubTypes.Type(value = A2UIEvent.class, name = "A2UI"),
        @JsonSubTypes.Type(value = A2UIActionEvent.class, name = "A2UI_ACTION"),
        @JsonSubTypes.Type(value = DiffEvent.class, name = "DIFF"),
        @JsonSubTypes.Type(value = UICardEvent.class, name = "UI_CARD")
})
public abstract sealed class AbstractEvent implements IEvent permits MessageEvent, MemoryEvent, A2UIEvent, A2UIActionEvent, DiffEvent, UICardEvent {
    private String sessionId;
    private String messageId;
    /** 是否持久化到 events.jsonl（autiva 特有，UICardEvent 等需要细粒度持久化控制） */
    private boolean persist;

    /**
     * 事件类型，由子类通过 final 字段固定返回，确保编译期强制指定。
     * 同时作为 Jackson 多态反序列化的类型标识（使用 EXISTING_PROPERTY）。
     */
    public abstract EventTypeEnum getEventType();
}
