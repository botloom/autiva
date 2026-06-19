package cn.bitloom.agentic.util;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

@Slf4j
public class MessageUtil {

    public static Message deserializeMessage(String json) {
        try {
            JsonNode obj = JsonUtils.parse(json);
            String messageTypeStr = obj.get("messageType").asText();
            if (messageTypeStr == null) {
                log.warn("消息缺少 messageType 字段: {}", json);
                return null;
            }
            MessageType messageType = MessageType.valueOf(messageTypeStr.toUpperCase());
            return switch (messageType) {
                case USER -> UserMessage.builder()
                        .text(obj.get("text").asText())
                        .media(JsonUtils.mapper().convertValue(obj.get("media"),
                                new TypeReference<List<Media>>() {}))
                        .metadata(JsonUtils.mapper().convertValue(obj.get("metadata"),
                                new TypeReference<Map<String, Object>>() {}))
                        .build();
                case ASSISTANT -> AssistantMessage.builder()
                        .content(obj.get("text").asText())
                        .media(JsonUtils.mapper().convertValue(obj.get("media"),
                                new TypeReference<List<Media>>() {}))
                        .properties(JsonUtils.mapper().convertValue(obj.get("metadata"),
                                new TypeReference<Map<String, Object>>() {}))
                        .toolCalls(JsonUtils.mapper().convertValue(obj.get("toolCalls"),
                                new TypeReference<List<AssistantMessage.ToolCall>>() {}))
                        .build();
                case TOOL -> ToolResponseMessage.builder()
                        .responses(JsonUtils.mapper().convertValue(obj.get("responses"),
                                new TypeReference<List<ToolResponseMessage.ToolResponse>>() {}))
                        .metadata(JsonUtils.mapper().convertValue(obj.get("metadata"),
                                new TypeReference<Map<String, Object>>() {}))
                        .build();
                case SYSTEM -> SystemMessage.builder()
                        .text(obj.get("text").asText())
                        .metadata(JsonUtils.mapper().convertValue(obj.get("metadata"),
                                new TypeReference<Map<String, Object>>() {}))
                        .build();
            };
        } catch (Exception e) {
            log.warn("反序列化消息失败: {}, 错误: {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * 构建异常兜底消息（统一文案，避免重复）
     */
    public static AssistantMessage buildFallbackMessage() {
        return AssistantMessage.builder()
                .content("""
                        ### 呜呜呜，小脑袋打了个盹儿…

                        出了一点小问题，暂时无法回复你 >_<

                        **试试以下方法：**
                        - **清空消息**后重新发送
                        - 如果还是不行，**重启应用**再试试

                        > 抱歉给你添麻烦啦～""")
                .properties(Map.of("finishReason", "STOP"))
                .build();
    }

}
