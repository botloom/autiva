package cn.bitloom.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON 工具类，基于 Jackson ObjectMapper，提供静态方法访问。
 * <p>
 * 替代原 fastjson2 的 JSON / JSONObject / JSONArray 工具方法。
 * 使用 FAIL_ON_UNKNOWN_PROPERTIES=false 以兼容已有数据文件的反序列化。
 * 注册 JavaTimeModule 以支持 java.time.* 日期时间类型。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    public static String toPrettyJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败", e);
        }
    }

    public static ObjectNode createObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArray() {
        return MAPPER.createArrayNode();
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 从 JSON 字符串中提取字段值，支持多个候选字段名（按顺序取第一个非空）。
     *
     * <p>容错：解析失败或字段不存在/为 null 时返回 null（不抛异常，
     * 区别于 {@link #parse} 的抛异常语义）。
     *
     * @param json       工具输入 JSON
     * @param fieldNames 候选字段名（按顺序取第一个非空）
     * @return 字段值；解析失败或字段不存在时为 null
     */
    public static String extractString(String json, String... fieldNames) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            for (String field : fieldNames) {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull()) {
                    return value.asText();
                }
            }
        } catch (Exception e) {
            // 容错：解析失败返回 null
        }
        return null;
    }

    private JsonUtils() {
    }
}
