package cn.bitloom.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeepSeekV4CompatConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer thinkingDisabledCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.setSerializerModifier(new ThinkingDisabledBeanSerializerModifier());
            builder.modules(module);
        };
    }
}
