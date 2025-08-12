package cn.bitloom.autiva.agent.acm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The type Autiva agent properties.
 *
 * @author bitloom
 */
@Data
@ConfigurationProperties(prefix = "autiva")
public class AutivaConfig {

    private String workDir;
    private BaseConfig base;
    private BrowserConfig browser;

    /**
     * The type Base config.
     */
    @Data
    public static class BaseConfig {
        private String defaultSystemPrompt;
    }

    /**
     * The type Browser config.
     */
    @Data
    public static class BrowserConfig{
        private String defaultSystemPrompt;
    }
}
