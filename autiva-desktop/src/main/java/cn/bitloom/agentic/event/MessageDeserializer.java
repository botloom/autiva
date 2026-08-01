package cn.bitloom.agentic.event;

import cn.bitloom.agentic.util.MessageUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;

/**
 * Jackson 反序列化器：将 JSON 中的 message 对象根据 messageType 字段
 * 映射到 Spring AI 的具体 Message 子类（UserMessage/AssistantMessage/ToolResponseMessage/SystemMessage）。
 * <p>
 * 委托 {@link MessageUtil#deserializeMessage(String)} 实现。
 */
public class MessageDeserializer extends JsonDeserializer<Message> {

    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // 读取当前 token 的完整 JSON 树，转字符串后委托 MessageUtil
        String json = p.readValueAsTree().toString();
        return MessageUtil.deserializeMessage(json);
    }
}
