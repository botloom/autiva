package cn.bitloom.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;

public class ThinkingDisabledBeanSerializerModifier extends BeanSerializerModifier {

    private static final String TARGET_CLASS = "org.springframework.ai.deepseek.api.DeepSeekApi$ChatCompletionRequest";

    @Override
    @SuppressWarnings("unchecked")
    public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                               BeanDescription beanDesc,
                                               JsonSerializer<?> serializer) {
        if (TARGET_CLASS.equals(beanDesc.getBeanClass().getName())) {
            return new ThinkingDisabledSerializer((JsonSerializer<Object>) serializer);
        }
        return serializer;
    }

    static class ThinkingDisabledSerializer extends StdSerializer<Object> {

        private final JsonSerializer<Object> delegate;

        ThinkingDisabledSerializer(JsonSerializer<Object> delegate) {
            super(Object.class);
            this.delegate = delegate;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            TokenBuffer tb = new TokenBuffer(gen.getCodec(), false);
            delegate.serialize(value, tb, provider);

            gen.writeStartObject();

            JsonParser parser = tb.asParser();
            parser.nextToken();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                gen.copyCurrentEvent(parser);
                parser.nextToken();
                gen.copyCurrentStructure(parser);
            }

            gen.writeFieldName("thinking");
            gen.writeStartObject();
            gen.writeStringField("type", "disabled");
            gen.writeEndObject();

            gen.writeEndObject();
            parser.close();
        }
    }
}
