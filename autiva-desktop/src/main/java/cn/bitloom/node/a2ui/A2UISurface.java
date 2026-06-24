package cn.bitloom.node.a2ui;

import cn.bitloom.agentic.a2ui.A2UIComponent;
import cn.bitloom.agentic.a2ui.A2UIMessage;
import cn.bitloom.agentic.a2ui.A2UIAction;
import javafx.scene.Node;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A2UI Surface 管理器。
 * <p>
 * 管理组件树和数据模型,处理 A2UI 消息,协调渲染器更新 UI。
 * 每个 Surface 对应一个独立的交互界面。
 */
@Slf4j
public class A2UISurface {

    private final String surfaceId;
    private final Map<String, A2UIComponent> components = new HashMap<>();
    private final Map<String, Object> dataModel = new HashMap<>();
    private final A2UIRenderer renderer;

    @Getter
    private Node rootNode;

    /** 用户交互回调:(actionName, context) → 通过 ToolUIBridge 回流到 Agent */
    private BiConsumer<String, Map<String, Object>> onUserAction;

    public A2UISurface(String surfaceId) {
        this.surfaceId = surfaceId;
        this.renderer = new A2UIRenderer(this);
    }

    public void setOnUserAction(BiConsumer<String, Map<String, Object>> onUserAction) {
        this.onUserAction = onUserAction;
    }

    /**
     * 处理 A2UI 消息,更新组件树和数据模型。
     */
    public void handleMessage(A2UIMessage message) {
        switch (message) {
            case A2UIMessage.CreateSurface cs -> handleCreate(cs);
            case A2UIMessage.UpdateComponents uc -> handleUpdateComponents(uc);
            case A2UIMessage.UpdateDataModel udm -> handleUpdateDataModel(udm);
            case A2UIMessage.DeleteSurface ds -> handleDelete();
        }
    }

    private void handleCreate(A2UIMessage.CreateSurface cs) {
        log.debug("Surface created: {}", surfaceId);
    }

    private void handleUpdateComponents(A2UIMessage.UpdateComponents uc) {
        // 更新组件树
        for (A2UIComponent component : uc.components()) {
            components.put(component.id(), component);
        }
        // 重新渲染
        render();
    }

    private void handleUpdateDataModel(A2UIMessage.UpdateDataModel udm) {
        String path = udm.path() != null ? udm.path() : "/";
        Object value = udm.value();

        if (value == null) {
            removeByPath(path);
        } else {
            setByPath(path, value);
        }

        // 数据模型更新后,刷新依赖绑定的组件
        render();
    }

    private void handleDelete() {
        components.clear();
        dataModel.clear();
        rootNode = null;
        log.debug("Surface deleted: {}", surfaceId);
    }

    /**
     * 重新渲染整个 Surface。
     */
    private void render() {
        // 查找根组件(第一个被引用为 children 的组件是根)
        String rootId = findRootComponentId();
        if (rootId == null) {
            log.warn("No root component found for surface: {}", surfaceId);
            return;
        }

        A2UIComponent rootComponent = components.get(rootId);
        if (rootComponent != null) {
            rootNode = renderer.render(rootComponent);
        }
    }

    /**
     * 查找根组件 ID(未被任何其他组件引用为子组件的组件)。
     */
    private String findRootComponentId() {
        if (components.size() == 1) {
            return components.keySet().iterator().next();
        }

        // 收集所有被引用为子组件的 ID
        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (A2UIComponent component : components.values()) {
            List<String> children = component.getChildren();
            referenced.addAll(children);

            // 检查 child 属性(Button 的子组件)
            String child = component.getString("child");
            if (child != null) {
                referenced.add(child);
            }
        }

        // 找到未被引用的组件(根组件)
        for (String id : components.keySet()) {
            if (!referenced.contains(id)) {
                return id;
            }
        }

        // 如果所有组件都被引用,返回第一个
        return components.keySet().stream().findFirst().orElse(null);
    }

    // ===== 数据模型操作(JSON Pointer) =====

    /**
     * 通过 JSON Pointer 获取数据模型中的值。
     */
    public Object getByPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            return dataModel;
        }
        String[] parts = path.split("/");
        Object current = dataModel;
        for (int i = 1; i < parts.length; i++) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(parts[i]);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void setByPath(String path, Object value) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            if (value instanceof Map<?, ?> map) {
                dataModel.clear();
                dataModel.putAll((Map<String, Object>) map);
            }
            return;
        }

        String[] parts = path.split("/");
        Map<String, Object> current = dataModel;
        for (int i = 1; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new HashMap<String, Object>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private void removeByPath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) {
            dataModel.clear();
            return;
        }

        String[] parts = path.split("/");
        Map<String, Object> current = dataModel;
        for (int i = 1; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return;
            }
            current = (Map<String, Object>) next;
        }
        current.remove(parts[parts.length - 1]);
    }

    /**
     * 解析属性值:支持字面值和 {path: "/xxx"} 数据绑定。
     */
    public Object resolveValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("path")) {
                String path = map.get("path").toString();
                return getByPath(path);
            }
        }
        return value;
    }

    /**
     * 获取组件 by ID。
     */
    public A2UIComponent getComponent(String id) {
        return components.get(id);
    }

    /**
     * 触发用户交互事件。
     */
    public void fireUserAction(String sourceComponentId, String actionName, Map<String, Object> context) {
        if (onUserAction != null) {
            onUserAction.accept(actionName, context);
        }
    }

    /**
     * 执行本地函数(FunctionCall)。
     */
    public Object executeFunction(String functionName, Map<String, Object> args) {
        return switch (functionName) {
            case "required" -> {
                Object value = args.get("value");
                yield value != null && !value.toString().isEmpty();
            }
            case "email" -> {
                Object value = args.get("value");
                if (value == null) yield false;
                yield value.toString().matches("^[^@]+@[^@]+\\.[^@]+$");
            }
            case "openUrl" -> {
                // 本地打开 URL(后续可实现)
                log.info("openUrl: {}", args.get("url"));
                yield true;
            }
            default -> {
                log.warn("Unknown function: {}", functionName);
                yield null;
            }
        };
    }

    /**
     * 评估验证规则。
     */
    public boolean evaluateCheck(cn.bitloom.agentic.a2ui.A2UICheck check) {
        if (check.condition() == null) {
            return true;
        }
        Object result = executeFunction(check.condition().call(), check.condition().args());
        return Boolean.TRUE.equals(result);
    }

    public String getSurfaceId() {
        return surfaceId;
    }

    public A2UIRenderer getRenderer() {
        return renderer;
    }

    /**
     * 供渲染器更新数据模型(public 入口)。
     */
    public void setByPathPublic(String path, Object value) {
        setByPath(path, value);
    }

    /**
     * 获取整个数据模型的副本。
     */
    public Map<String, Object> getAllData() {
        return new HashMap<>(dataModel);
    }
}
