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

    @Value("${app.browser-path}")
    private String browserPath;
    
    @Value("${app.session.isolation}")
    private SessionIsolationEnum isolation;
    
    @Value("${dingtalk.app.client-id:}")
    private String dingTalkClientId;
    
    @Value("${dingtalk.app.client-secret:}")
    private String dingTalkClientSecret;
    
    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;
    
    @Value("${spring.ai.zhipuai.api-key:}")
    private String zApiKey;

    @Value("${weixin.ilink.enabled:false}")
    private boolean weixinILinkEnabled;
    
    public void save() {
        try (FileWriter writer = new FileWriter(AppConstants.Base.CONFIG_FILE.toFile())) {
            writer.write("# Application Settings\n");
            writer.write("app.browser-path=" + browserPath + "\n");
            writer.write("app.session.isolation=" + isolation.name() + "\n");
            writer.write("\n");
            
            writer.write("# DingTalk Configuration\n");
            writer.write("dingtalk.app.client-id=" + (dingTalkClientId != null ? dingTalkClientId : "") + "\n");
            writer.write("dingtalk.app.client-secret=" + (dingTalkClientSecret != null ? dingTalkClientSecret : "") + "\n");
            writer.write("\n");
            
            writer.write("# AI API Keys\n");
            writer.write("spring.ai.deepseek.api-key=" + (deepseekApiKey != null ? deepseekApiKey : "") + "\n");
            writer.write("spring.ai.zhipuai.api-key=" + (zApiKey != null ? zApiKey : "") + "\n");
            writer.write("\n");

            writer.write("# WeChat iLink Configuration\n");
            writer.write("weixin.ilink.enabled=" + weixinILinkEnabled + "\n");
            
            log.info("配置保存成功: {}", AppConstants.Base.CONFIG_FILE);
        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }

}