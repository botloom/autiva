package cn.bitloom.config;

import cn.bitloom.agentic.session.SessionIsolationEnum;
import cn.bitloom.constant.AppConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

@Setter
@Getter
@Slf4j
@Configuration
public class ConfigManager {

    @Value("${app.session.isolation}")
    private SessionIsolationEnum isolation;
    @Value("${app.search.bocha-api-key:}")
    private String bochaApiKey;

    /** 每轮对话的工具调用预算（防 LLM 工具调用死循环；编码任务需覆盖读文件+修改+编译验证多轮） */
    @Value("${app.agent.max-tool-calls:150}")
    private int maxToolCalls;

    @Value("${spring.ai.deepseek.chat.base-url:}")
    private String deepseekBaseUrl;
    @Value("${spring.ai.deepseek.chat.completions-path:/v1/chat/completions}")
    private String deepseekCompletionsPath;
    @Value("${spring.ai.deepseek.chat.api-key:}")
    private String deepseekApiKey;
    @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")
    private String deepseekChatModel;

    /**
     * 以 YAML 格式保存配置到 ~/.autiva/settings.yaml
     */
    public void save() {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("app.session.isolation", isolation.name());
        flat.put("spring.ai.deepseek.chat.base-url", deepseekBaseUrl != null ? deepseekBaseUrl : "");
        flat.put("spring.ai.deepseek.chat.completions-path", deepseekCompletionsPath != null ? deepseekCompletionsPath : "");
        flat.put("spring.ai.deepseek.chat.api-key", deepseekApiKey != null ? deepseekApiKey : "");
        flat.put("spring.ai.deepseek.chat.options.model", deepseekChatModel != null ? deepseekChatModel : "");
        if (org.apache.commons.lang3.StringUtils.isNotBlank(bochaApiKey)) {
            flat.put("app.search.bocha-api-key", bochaApiKey);
        }

        Map<String, Object> nested = nest(flat);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        try {
            Files.createDirectories(AppConstants.Base.SETTINGS_FILE.getParent());
            Files.writeString(AppConstants.Base.SETTINGS_FILE, yaml.dump(nested));
            log.info("配置保存成功: {}", AppConstants.Base.SETTINGS_FILE);
        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }

    /**
     * 将扁平 key（如 "spring.ai.deepseek.chat.api-key"）转为嵌套 Map 结构。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> nest(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        flat.forEach((key, value) -> {
            String[] parts = key.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object existing = current.get(parts[i]);
                if (!(existing instanceof Map)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    current.put(parts[i], node);
                    current = node;
                } else {
                    current = (Map<String, Object>) existing;
                }
            }
            current.put(parts[parts.length - 1], value);
        });
        return root;
    }

}
