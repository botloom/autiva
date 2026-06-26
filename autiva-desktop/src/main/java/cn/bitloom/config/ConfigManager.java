package cn.bitloom.config;

import cn.bitloom.agentic.session.SessionIsolationEnum;
import cn.bitloom.constant.AppConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileWriter;
import java.io.IOException;

@Setter
@Getter
@Slf4j
@Configuration
public class ConfigManager {

    @Value("${app.session.isolation}")
    private SessionIsolationEnum isolation;
    @Value("${app.search.bocha-api-key:}")
    private String bochaApiKey;

    @Value("${spring.ai.deepseek.chat.base-url:}")
    private String deepseekBaseUrl;
    @Value("${spring.ai.deepseek.chat.completions-path:/v1/chat/completions}")
    private String deepseekCompletionsPath;
    @Value("${spring.ai.deepseek.chat.api-key:}")
    private String deepseekApiKey;
    @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")
    private String deepseekChatModel;

    public void save() {
        try (FileWriter writer = new FileWriter(AppConstants.Base.SETTINGS_FILE.toFile())) {
            writer.write("# Application Settings\n");
            writer.write("app.session.isolation=" + isolation.name() + "\n");
            writer.write("\n");

            writer.write("# DeepSeek Configuration\n");
            writer.write("spring.ai.deepseek.chat.base-url=" + (deepseekBaseUrl != null ? deepseekBaseUrl : "") + "\n");
            writer.write("spring.ai.deepseek.chat.completions-path=" + (deepseekCompletionsPath != null ? deepseekCompletionsPath : "") + "\n");
            writer.write("spring.ai.deepseek.chat.api-key=" + (deepseekApiKey != null ? deepseekApiKey : "") + "\n");
            writer.write("spring.ai.deepseek.chat.options.model=" + (deepseekChatModel != null ? deepseekChatModel : "") + "\n");
            writer.write("\n");

            if (org.apache.commons.lang3.StringUtils.isNotBlank(bochaApiKey)) {
                writer.write("\n# Search Configuration\n");
                writer.write("app.search.bocha-api-key=" + bochaApiKey + "\n");
            }

            log.info("配置保存成功: {}", AppConstants.Base.SETTINGS_FILE);
        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }

}
