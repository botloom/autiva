package cn.bitloom.agentic.a2ui;

import java.util.List;
import java.util.Map;

/**
 * A2UI v0.9.1 消息模型。
 * <p>
 * 四种消息类型对应 A2UI 协议的核心操作:
 * <ul>
 *   <li>{@link CreateSurface} - 创建界面画布</li>
 *   <li>{@link UpdateComponents} - 更新组件(扁平列表 + ID 引用)</li>
 *   <li>{@link UpdateDataModel} - 更新数据模型(JSON Pointer)</li>
 *   <li>{@link DeleteSurface} - 删除界面</li>
 * </ul>
 */
public sealed interface A2UIMessage permits
        A2UIMessage.CreateSurface,
        A2UIMessage.UpdateComponents,
        A2UIMessage.UpdateDataModel,
        A2UIMessage.DeleteSurface {

    /** 协议版本,固定为 "v0.9.1" */
    String version();

    /** Surface 唯一标识符 */
    String surfaceId();

    /** 创建 Surface */
    record CreateSurface(
            String version,
            String surfaceId,
            String catalogId,
            Map<String, Object> theme,
            boolean sendDataModel
    ) implements A2UIMessage {}

    /** 更新组件(扁平列表,通过 ID 引用构建 UI 树) */
    record UpdateComponents(
            String version,
            String surfaceId,
            List<A2UIComponent> components
    ) implements A2UIMessage {}

    /** 更新数据模型 */
    record UpdateDataModel(
            String version,
            String surfaceId,
            String path,
            Object value
    ) implements A2UIMessage {}

    /** 删除 Surface */
    record DeleteSurface(
            String version,
            String surfaceId
    ) implements A2UIMessage {}
}
