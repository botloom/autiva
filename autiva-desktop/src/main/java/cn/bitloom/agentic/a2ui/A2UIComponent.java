package cn.bitloom.agentic.a2ui;

import java.util.List;
import java.util.Map;

/**
 * A2UI 组件定义(扁平结构 + ID 引用)。
 * <p>
 * 采用邻接列表模型:组件以扁平列表提供,通过 ID 引用定义父子关系。
 *
 * @param id         组件唯一 ID
 * @param component  组件类型
 * @param properties 组件属性(text/value/children/label 等)
 * @param action     可选:交互动作
 * @param checks     可选:验证规则列表
 */
public record A2UIComponent(
        String id,
        A2UIComponentType component,
        Map<String, Object> properties,
        A2UIAction action,
        List<A2UICheck> checks
) {
    /** 便捷方法:获取属性值 */
    public Object get(String key) {
        return properties != null ? properties.get(key) : null;
    }

    /** 便捷方法:获取字符串属性 */
    public String getString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    /** 便捷方法:获取子组件 ID 列表 */
    public List<String> getChildren() {
        Object children = get("children");
        if (children instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}