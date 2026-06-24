package cn.bitloom.agentic.a2ui;

import java.util.Map;

/**
 * A2UI Basic Catalog 组件类型枚举。
 */
public enum A2UIComponentType {
    // 布局组件
    ROW, COLUMN, LIST,
    // 显示组件
    TEXT, IMAGE, ICON, DIVIDER,
    // 交互组件
    BUTTON, TEXT_FIELD, CHECK_BOX, SLIDER, DATE_TIME_INPUT, CHOICE_PICKER,
    // 容器组件
    TABS, CARD;

    /** LLM 常见的拼写变体 → 标准枚举名映射 */
    private static final Map<String, A2UIComponentType> ALIASES = Map.ofEntries(
            Map.entry("TEXTFIELD", TEXT_FIELD),
            Map.entry("TEXT_INPUT", TEXT_FIELD),
            Map.entry("TEXTINPUT", TEXT_FIELD),
            Map.entry("CHECKBOX", CHECK_BOX),
            Map.entry("CHECKBOX_INPUT", CHECK_BOX),
            Map.entry("DATETIMEINPUT", DATE_TIME_INPUT),
            Map.entry("DATE_TIME", DATE_TIME_INPUT),
            Map.entry("DATETIME_INPUT", DATE_TIME_INPUT),
            Map.entry("CHOICEPICKER", CHOICE_PICKER),
            Map.entry("SELECT", CHOICE_PICKER),
            Map.entry("DROPDOWN", CHOICE_PICKER)
    );

    /**
     * 容错解析：先尝试标准枚举名，再查别名映射。
     */
    public static A2UIComponentType fromString(String value) {
        String upper = value.toUpperCase().replace("-", "_");
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            A2UIComponentType alias = ALIASES.get(upper);
            if (alias != null) {
                return alias;
            }
            throw e;
        }
    }
}