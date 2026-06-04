package cn.bitloom.agentic.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具统一返回值类型。
 * <p>
 * 所有工具方法应返回 {@code ToolResult.toString()}，使 LLM 消费纯文本的同时，
 * UI 可通过 {@link #fromJson} 解析结构化信息进行差异化渲染。
 * <p>
 * 结构：
 * - status: SUCCESS / ERROR / WARNING — 用于 UI 状态颜色
 * - message: 简短描述 — 用于 UI header 摘要
 * - data: 结构化键值对 — 用于 UI 详情标签展示
 * - rawOutput: 原始输出 — 用于 LLM 消费和 UI 输出区展示
 */
public class ToolResult {

    public enum Status {
        SUCCESS, ERROR, WARNING
    }

    private final Status status;
    private final String message;
    private final Map<String, Object> data;
    private final String rawOutput;

    private ToolResult(Status status, String message, Map<String, Object> data, String rawOutput) {
        this.status = status;
        this.message = message;
        this.data = data != null ? data : Collections.emptyMap();
        this.rawOutput = rawOutput;
    }

    // ========== 静态工厂方法 ==========

    public static ToolResult success(String message) {
        return new ToolResult(Status.SUCCESS, message, null, null);
    }

    public static ToolResult success(String message, Map<String, Object> data) {
        return new ToolResult(Status.SUCCESS, message, data, null);
    }

    public static ToolResult success(String message, Map<String, Object> data, String rawOutput) {
        return new ToolResult(Status.SUCCESS, message, data, rawOutput);
    }

    public static ToolResult error(String message) {
        return new ToolResult(Status.ERROR, message, null, null);
    }

    public static ToolResult error(String message, String rawOutput) {
        return new ToolResult(Status.ERROR, message, null, rawOutput);
    }

    public static ToolResult error(String message, Map<String, Object> data) {
        return new ToolResult(Status.ERROR, message, data, null);
    }

    public static ToolResult warning(String message) {
        return new ToolResult(Status.WARNING, message, null, null);
    }

    public static ToolResult warning(String message, Map<String, Object> data) {
        return new ToolResult(Status.WARNING, message, data, null);
    }

    public static ToolResult warning(String message, Map<String, Object> data, String rawOutput) {
        return new ToolResult(Status.WARNING, message, data, rawOutput);
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Status status = Status.SUCCESS;
        private String message;
        private Map<String, Object> data;
        private String rawOutput;

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder data(String key, Object value) {
            if (this.data == null) {
                this.data = new LinkedHashMap<>();
            }
            this.data.put(key, value);
            return this;
        }

        public Builder rawOutput(String rawOutput) {
            this.rawOutput = rawOutput;
            return this;
        }

        public ToolResult build() {
            return new ToolResult(status, message, data, rawOutput);
        }
    }

    // ========== Getter ==========

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    // ========== 序列化 ==========

    /**
     * 返回 JSON 字符串，供 Spring AI 框架和 UI 端消费。
     * <p>
     * Spring AI 调用此方法将返回值转为文本传给 LLM，
     * UI 端可通过 {@link #fromJson} 解析结构化信息进行差异化渲染。
     * <p>
     * JSON 包含 status、message、data、rawOutput 四个字段，
     * LLM 可从 rawOutput 读取原始输出文本。
     */
    @Override
    public String toString() {
        return toJson();
    }

    /**
     * 转换为 JSON 字符串。
     */
    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("status", status.name());
        obj.put("message", message);
        obj.put("data", data);
        obj.put("rawOutput", rawOutput);
        return obj.toJSONString();
    }

    /**
     * 从 JSON 字符串解析 ToolResult。
     * <p>
     * 如果解析失败返回 null，UI 应降级到纯文本展示。
     */
    public static ToolResult fromJson(String json) {
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) {
                return null;
            }
            String statusStr = obj.getString("status");
            if (statusStr == null) {
                return null;
            }
            Status status = Status.valueOf(statusStr);
            String message = obj.getString("message");
            Map<String, Object> data = obj.getJSONObject("data") != null
                    ? new LinkedHashMap<>(obj.getJSONObject("data"))
                    : null;
            String rawOutput = obj.getString("rawOutput");
            return new ToolResult(status, message, data, rawOutput);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断一个字符串是否是 ToolResult JSON 格式。
     */
    public static boolean isToolResultJson(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            JSONObject obj = JSON.parseObject(text);
            return obj != null && obj.containsKey("status") && obj.containsKey("message");
        } catch (Exception e) {
            return false;
        }
    }
}
