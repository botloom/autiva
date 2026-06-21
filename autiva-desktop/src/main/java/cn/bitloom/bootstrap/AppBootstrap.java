package cn.bitloom.bootstrap;

import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 应用启动初始化，按照 AppConstants 的层级结构组织：
 * Base → Agents → MainAgent → Workspace
 */
@Slf4j
public class AppBootstrap {

    private AppBootstrap() {
    }

    public static void initialize() {
        try {
            initBaseDirs();
        } catch (Exception e) {
            log.error("创建应用目录失败", e);
        }
        try {
            initSettingsFile();
        } catch (IOException e) {
            log.error("创建配置文件失败", e);
        }
        try {
            initDefaultMainAgent();
        } catch (Exception e) {
            log.error("初始化默认主智能体失败", e);
        }

    }

    private static void initBaseDirs() throws IOException {
        if (!Files.exists(AppConstants.APP_DIR)){
            Files.createDirectories(AppConstants.APP_DIR);
        }
        if (!Files.exists(AppConstants.Base.WORKSPACE_DIR)){
            Files.createDirectories(AppConstants.Base.WORKSPACE_DIR);
        }
        if (!Files.exists(AppConstants.Base.AGENTS_DIR)){
            Files.createDirectories(AppConstants.Base.AGENTS_DIR);
        }
        if (!Files.exists(AppConstants.Base.SKILLS_DIR)){
            Files.createDirectories(AppConstants.Base.SKILLS_DIR);
        }
        if (!Files.exists(AppConstants.Base.LOGS_DIR)){
            Files.createDirectories(AppConstants.Base.LOGS_DIR);
        }
    }

    private static void initSettingsFile() throws IOException {
        Path settingsFile = AppConstants.Base.SETTINGS_FILE;
        if (Files.exists(settingsFile)) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource configResource = resolver.getResource("classpath:bootstrap/settings.properties");
        Files.copy(configResource.getInputStream(), settingsFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void initDefaultMainAgent() throws IOException {
        Path agentDir = AppConstants.Agents.agentDir(AppConstants.Agents.DEFAULT_AGENT_NAME);
        if (Files.exists(agentDir)) {
            return;
        }

        Files.createDirectories(agentDir);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // 从 bootstrap/agent/default/ 复制所有文件到 agents/default/
        Resource[] defaultResources = resolver.getResources("classpath:bootstrap/agent/default/*");
        for (Resource resource : defaultResources) {
            if (Objects.isNull(resource.getFilename())){
                continue;
            }
            Path target = agentDir.resolve(resource.getFilename());
            if (Files.exists(target)) {
                continue;
            }
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        }

        // 创建 workspace 目录
        Files.createDirectories(AppConstants.MainAgent.sessionsDir(AppConstants.Agents.DEFAULT_AGENT_NAME));
        Files.createDirectories(AppConstants.MainAgent.contextDir(AppConstants.Agents.DEFAULT_AGENT_NAME));
    }


}
