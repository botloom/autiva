package cn.bitloom.agentic.tool.a2ui;

import cn.bitloom.agentic.a2ui.A2UIAction;
import cn.bitloom.agentic.a2ui.A2UICheck;
import cn.bitloom.agentic.a2ui.A2UIComponent;
import cn.bitloom.agentic.a2ui.A2UIComponentType;
import cn.bitloom.agentic.a2ui.A2UIMessage;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A2UI 动态界面生成工具。
 * <p>
 * Agent 通过此工具生成 A2UI v0.9.1 消息,驱动 UI 渲染层创建交互式界面。
 * 工作流程:
 * <ol>
 *   <li>create: 创建 Surface</li>
 *   <li>update_components: 定义组件(扁平列表,ID 引用)</li>
 *   <li>update_data: 填充数据</li>
 *   <li>等待用户交互(自动回流)</li>
 *   <li>delete: 关闭 Surface</li>
 * </ol>
 */
@Slf4j
public class A2UITool extends AbstractTool<A2UITool.Input> {

    private static final String VERSION = "v0.9.1";
    private static final String CATALOG_ID = "https://a2ui.org/specification/v0.9.1-a2ui/catalogs/basic/catalog.json";

    private final A2UIHandler handler;

    public record Input(
            @ToolParam(description = "操作类型: create / update_components / update_data / delete")
            String operation,
            @ToolParam(description = "Surface ID,唯一标识一个界面")
            String surfaceId,
            @ToolParam(description = "组件列表(JSON 数组,仅 update_components 时需要)。每个组件包含 id/component/properties/action/checks 字段")
            String components,
            @ToolParam(description = "数据路径(JSON Pointer,如 /user/name,仅 update_data 时需要,默认 /)")
            String path,
            @ToolParam(description = "数据值(JSON,仅 update_data 时需要)")
            String value,
            @ToolParam(description = "主题配置(JSON 对象,仅 create 时可选)")
            Object theme
    ) {}

    private A2UITool(A2UIHandler handler) {
        super("A2UI", """
                生成动态用户界面(A2UI v0.9.1 协议)。用于需要复杂交互的场景:
                - 多字段表单(优于多次 AskUserQuestion)
                - 数据展示(列表、卡片)
                - 配置向导(分步骤引导)

                工作流程:
                1. create: 创建 Surface
                2. update_components: 定义组件(扁平列表,ID 引用)
                3. update_data: 填充数据(可选)
                4. 等待用户交互(自动回流,无需轮询)
                5. delete: 关闭 Surface

                组件类型(Basic Catalog):
                - 布局: Row(水平), Column(垂直), List(列表)
                - 显示: Text, Image, Icon, Divider
                - 交互: Button, TextField, CheckBox, Slider, ChoicePicker, DateTimeInput
                - 容器: Tabs, Card

                组件属性:
                - Text: text(字符串), variant(h1/h2/h3/h4/h5/caption/body)
                - TextField: label, value(可绑定 {path: "/xxx"}), textFieldType(shortText/longText/number/obscured)
                - Button: child(子组件ID), variant(primary), action({event: {name: "xxx"}} 或 {functionCall: {call: "xxx"}})
                - CheckBox: label, value(布尔,可绑定)
                - Slider: value(数字,可绑定), minValue, maxValue
                - ChoicePicker: options([{label, description}]), selections(可绑定), maxAllowedSelections
                - Row/Column/List: children(子组件ID数组)

                示例 - 用户信息表单:
                operation: create, surfaceId: user_form
                operation: update_components, surfaceId: user_form, components: [
                  {"id":"root","component":"Column","properties":{"children":["name_label","name_field","submit_btn"]}},
                  {"id":"name_label","component":"Text","properties":{"text":"姓名","variant":"h4"}},
                  {"id":"name_field","component":"TextField","properties":{"label":"请输入姓名","value":{"path":"/name"}}},
                  {"id":"submit_btn","component":"Button","properties":{"child":"submit_text"},"action":{"event":{"name":"submit_form"}}},
                  {"id":"submit_text","component":"Text","properties":{"text":"提交"}}
                ]
                """, Input.class);
        this.handler = handler;
    }

    @Override
    public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
        String sessionId = context != null ? (String) context.getContext().get("sessionId") : null;
        if (sessionId == null) {
            return ToolResult.error("无法获取 sessionId");
        }

        try {
            A2UIMessage message = buildMessage(input);
            if (message == null) {
                return ToolResult.error("无效的操作类型: " + input.operation());
            }

            CompletableFuture<String> future = handler.handle(message, sessionId);
            String result = future.get();
            return ToolResult.success(result);
        } catch (Exception e) {
            log.error("A2UI tool execution failed", e);
            return ToolResult.error("A2UI 操作失败: " + e.getMessage());
        }
    }

    private A2UIMessage buildMessage(Input input) {
        String op = input.operation() != null ? input.operation().toLowerCase() : "";

        return switch (op) {
            case "create" -> new A2UIMessage.CreateSurface(
                    VERSION,
                    input.surfaceId(),
                    CATALOG_ID,
                    parseTheme(input.theme()),
                    false
            );
            case "update_components" -> new A2UIMessage.UpdateComponents(
                    VERSION,
                    input.surfaceId(),
                    parseComponents(input.components())
            );
            case "update_data" -> new A2UIMessage.UpdateDataModel(
                    VERSION,
                    input.surfaceId(),
                    input.path() != null ? input.path() : "/",
                    parseValue(input.value())
            );
            case "delete" -> new A2UIMessage.DeleteSurface(
                    VERSION,
                    input.surfaceId()
            );
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTheme(Object theme) {
        if (theme == null) {
            return Collections.emptyMap();
        }
        if (theme instanceof Map) {
            return (Map<String, Object>) theme;
        }
        if (theme instanceof String s) {
            if (s.isBlank()) {
                return Collections.emptyMap();
            }
            try {
                return JsonUtils.mapper().readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse theme JSON: {}", s, e);
                return Collections.emptyMap();
            }
        }
        // 其他类型尝试转换
        try {
            return JsonUtils.mapper().convertValue(theme, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to convert theme: {}", theme, e);
            return Collections.emptyMap();
        }
    }

    private List<A2UIComponent> parseComponents(String componentsJson) {
        if (componentsJson == null || componentsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode array = JsonUtils.parse(componentsJson);
            if (array == null || !array.isArray()) {
                throw new RuntimeException("components 必须是 JSON 数组格式，例如 [{\"id\": \"...\", \"component\": \"...\"}]");
            }
            List<A2UIComponent> components = new ArrayList<>();
            for (JsonNode node : array) {
                components.add(parseComponent(node));
            }
            return components;
        } catch (RuntimeException e) {
            log.error("Failed to parse components JSON", e);
            throw new RuntimeException("JSON 格式错误: " + e.getCause().getMessage() + "。请检查 components 字段是否为有效的 JSON 数组格式，确保数组用 ] 结束而非 }，且所有对象正确闭合。");
        }
    }

    private A2UIComponent parseComponent(JsonNode node) {
        String id = node.get("id").asText();
        A2UIComponentType type = A2UIComponentType.fromString(node.get("component").asText());

        Map<String, Object> properties = Collections.emptyMap();
        if (node.has("properties") && node.get("properties").isObject()) {
            properties = JsonUtils.mapper().convertValue(node.get("properties"),
                    new TypeReference<Map<String, Object>>() {});
        }

        A2UIAction action = null;
        if (node.has("action") && !node.get("action").isNull()) {
            action = parseAction(node.get("action"));
        }

        List<A2UICheck> checks = List.of();
        if (node.has("checks") && node.get("checks").isArray()) {
            checks = new ArrayList<>();
            for (JsonNode checkNode : node.get("checks")) {
                checks.add(parseCheck(checkNode));
            }
        }

        return new A2UIComponent(id, type, properties, action, checks);
    }

    private A2UIAction parseAction(JsonNode node) {
        if (node.has("event")) {
            JsonNode eventNode = node.get("event");
            String name = eventNode.get("name").asText();
            Map<String, Object> context = Collections.emptyMap();
            if (eventNode.has("context")) {
                context = JsonUtils.mapper().convertValue(eventNode.get("context"),
                        new TypeReference<Map<String, Object>>() {});
            }
            return new A2UIAction.Event(name, context);
        } else if (node.has("functionCall")) {
            JsonNode funcNode = node.get("functionCall");
            String call = funcNode.get("call").asText();
            Map<String, Object> args = Collections.emptyMap();
            if (funcNode.has("args")) {
                args = JsonUtils.mapper().convertValue(funcNode.get("args"),
                        new TypeReference<Map<String, Object>>() {});
            }
            return new A2UIAction.FunctionCall(call, args);
        }
        return null;
    }

    private A2UICheck parseCheck(JsonNode node) {
        A2UIAction.FunctionCall condition = null;
        if (node.has("condition")) {
            JsonNode condNode = node.get("condition");
            if (condNode.has("functionCall")) {
                JsonNode funcNode = condNode.get("functionCall");
                String call = funcNode.get("call").asText();
                Map<String, Object> args = Collections.emptyMap();
                if (funcNode.has("args")) {
                    args = JsonUtils.mapper().convertValue(funcNode.get("args"),
                            new TypeReference<Map<String, Object>>() {});
                }
                condition = new A2UIAction.FunctionCall(call, args);
            }
        }
        String message = node.has("message") ? node.get("message").asText() : "";
        return new A2UICheck(condition, message);
    }

    private Object parseValue(String valueJson) {
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonUtils.parse(valueJson);
            return JsonUtils.mapper().convertValue(node, Object.class);
        } catch (Exception e) {
            return valueJson;
        }
    }

    /**
     * A2UI 消息处理器接口,由 ToolUIBridge 实现。
     */
    @FunctionalInterface
    public interface A2UIHandler {
        CompletableFuture<String> handle(A2UIMessage message, String sessionId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private A2UIHandler handler;

        public Builder handler(A2UIHandler handler) {
            this.handler = handler;
            return this;
        }

        public A2UITool build() {
            if (handler == null) {
                throw new IllegalStateException("handler must not be null");
            }
            return new A2UITool(handler);
        }
    }
}
