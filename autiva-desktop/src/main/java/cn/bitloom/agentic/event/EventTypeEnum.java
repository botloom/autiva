package cn.bitloom.agentic.event;

/**
 * 事件类型枚举，作为 Jackson 多态反序列化的类型标识。
 * 使用 @JsonTypeInfo(EXISTING_PROPERTY) 时，子类的 eventType 字段值与此枚举名对应。
 */
public enum EventTypeEnum {
    MESSAGE,
    MEMORY,
    A2UI,
    A2UI_ACTION,
    DIFF,
    UI_CARD
}
