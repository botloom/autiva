package cn.bitloom.bootstrap;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
public class AppBootstrap {

    private AppBootstrap() {
    }

    public static void initialize() {
        try {
            createAppDirsIfNotExist();
        } catch (Exception e) {
            log.error("创建应用目录失败", e);
        }
        try {
            createConfigFileIfNotExist();
        } catch (Exception e) {
            log.error("创建配置文件失败", e);
        }
        try {
            initAgentWorkspaces();
        } catch (Exception e) {
            log.error("初始化主智能体工作区失败", e);
        }
        try {
            initSubagentWorkspace();
        } catch (Exception e) {
            log.error("初始化子智能体工作区失败", e);
        }
    }

    private static void createAppDirsIfNotExist() {
        try {
            if (!Files.exists(AppConstants.Base.APP_DIR)) {
                Files.createDirectories(AppConstants.Base.APP_DIR);
            }
            if (!Files.exists(AppConstants.Base.LOGS_DIR)) {
                Files.createDirectories(AppConstants.Base.LOGS_DIR);
            }
            if (!Files.exists(AppConstants.Base.SKILL_DIR)) {
                Files.createDirectories(AppConstants.Base.SKILL_DIR);
            }
            if (!Files.exists(AppConstants.Base.MCP_DIR)) {
                Files.createDirectories(AppConstants.Base.MCP_DIR);
            }
            if (!Files.exists(AppConstants.Base.WORKSPACE_DIR)) {
                Files.createDirectories(AppConstants.Base.WORKSPACE_DIR);
            }
            if (!Files.exists(AppConstants.Base.MCP_CONFIG_FILE)) {
                Files.writeString(AppConstants.Base.MCP_CONFIG_FILE, "{\"mcpServers\":{}}");
            }
        } catch (Exception e) {
            log.error("创建应用目录失败", e);
        }
    }

    private static void createConfigFileIfNotExist() {
        if (!Files.exists(AppConstants.Base.CONFIG_FILE)) {
            try {
                Files.writeString(AppConstants.Base.CONFIG_FILE, DEFAULT_CONFIG_TEMPLATE);
                log.info("创建默认配置文件: {}", AppConstants.Base.CONFIG_FILE);
            } catch (IOException e) {
                log.error("创建配置文件失败", e);
            }
        }
    }

    private static final String DEFAULT_CONFIG_TEMPLATE = """
            # Autiva Application Settings
            # 首次运行自动生成，请在设置页面中修改

            app.session.isolation=PER_PEER
            app.search.bocha-api-key=

            # DeepSeek Configuration
            spring.ai.deepseek.chat.base-url=https://api.deepseek.com
            spring.ai.deepseek.chat.completions-path=/v1/chat/completions
            spring.ai.deepseek.chat.api-key=
            spring.ai.deepseek.chat.options.model=deepseek-chat

            # ZhiPu AI Configuration
            spring.ai.zhipuai.chat.base-url=https://open.bigmodel.cn/api/paas/v4
            spring.ai.zhipuai.chat.completions-path=/chat/completions
            spring.ai.zhipuai.api-key=
            spring.ai.zhipuai.chat.options.model=glm-4-flash

            # WeChat iLink
            wechat.ilink.enabled=false
            
            """;

    private static void initAgentWorkspaces() {
        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            if (!identity.isMain()) {
                continue;
            }
            try {
                Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                    log.info("初始化工作目录: {}", dir);
                }
                copyClasspathTemplates("bootstrap/" + identity.name(), dir);
            } catch (Exception e) {
                log.warn("初始化主智能体工作区失败(跳过): {}, 原因: {}", identity.name(), e.getMessage());
            }
        }
    }

    private static void copyClasspathTemplates(String classpathDir, Path targetDir) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + classpathDir + "/*.md");

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                Path targetPath = null;
                if (fileName != null) {
                    targetPath = targetDir.resolve(fileName);
                }
                if (targetPath != null && !Files.exists(targetPath)) {
                    try {
                        Files.copy(resource.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("复制模板文件: {}", targetPath);
                    } catch (IOException e) {
                        log.error("复制模板文件失败: {}", fileName, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("扫描模板目录失败: {}", classpathDir, e);
        }
    }

    private static void initSubagentWorkspace() {
        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            if (!identity.isSubagent()) {
                continue;
            }
            if (identity == AgentIdentityEnum.A2A) {
                continue;
            }

            try {
                Path dir = AppConstants.Base.WORKSPACE_DIR.resolve(identity.name());
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                    log.info("初始化子智能体工作目录: {}", dir);
                }
                copyClasspathTemplates("bootstrap/" + identity.name(), dir);
            } catch (Exception e) {
                log.warn("初始化子智能体工作区失败(跳过): {}, 原因: {}", identity.name(), e.getMessage());
            }
        }
    }
}
