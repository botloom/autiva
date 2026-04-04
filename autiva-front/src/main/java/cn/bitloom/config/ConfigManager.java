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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    @Value("${app.agent.main.tools:read,write,edit,exec,web_search,web_fetch,cron_create,cron_list,cron_delete,cron_trigger}")
    private String mainAgentTools;
    
    private Map<String, String> agentToolsMap = new HashMap<>();

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
            
            writer.write("# Agent Configuration\n");
            writer.write("app.agent.main.tools=" + (mainAgentTools != null ? mainAgentTools : "") + "\n");
            
            for (Map.Entry<String, String> entry : agentToolsMap.entrySet()) {
                writer.write("app.agent." + entry.getKey() + ".tools=" + (entry.getValue() != null ? entry.getValue() : "") + "\n");
            }
            
            log.info("配置保存成功: {}", AppConstants.Base.CONFIG_FILE);
        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }
    
    public List<String> getMainAgentToolList() {
        if (mainAgentTools == null || mainAgentTools.trim().isEmpty()) {
            return List.of();
        }
        return List.of(mainAgentTools.split(","));
    }
    
    public List<String> getAgentToolList(String agentName) {
        String tools = agentToolsMap.get(agentName);
        if (tools == null || tools.trim().isEmpty()) {
            return getMainAgentToolList();
        }
        return List.of(tools.split(","));
    }
    
    public void setAgentTools(String agentName, List<String> tools) {
        String toolsString = String.join(",", tools);
        agentToolsMap.put(agentName, toolsString);
    }
    
    public void setAgentTools(String agentName, String tools) {
        agentToolsMap.put(agentName, tools);
    }

}