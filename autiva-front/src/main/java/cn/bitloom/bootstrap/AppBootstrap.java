package cn.bitloom.bootstrap;

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
            initAppDirs();
        } catch (Exception e) {
            log.error("创建应用目录失败", e);
        }
        try {
            initSettingsFile();
        } catch (IOException e) {
            log.error("创建配置文件失败", e);
        }
        try {
            initDefaultAgent();
        } catch (Exception e) {
            log.error("初始化默认智能体失败", e);
        }
        try {
            initSubagents();
        } catch (Exception e) {
            log.error("初始化子智能体失败", e);
        }
    }

    private static void initAppDirs() throws IOException {
        if (!Files.exists(AppConstants.Base.APP_DIR)) {
            Files.createDirectories(AppConstants.Base.APP_DIR);
        }
        if (!Files.exists(AppConstants.Base.LOGS_DIR)) {
            Files.createDirectories(AppConstants.Base.LOGS_DIR);
        }
        if (!Files.exists(AppConstants.Base.SKILLS_DIR)) {
            Files.createDirectories(AppConstants.Base.SKILLS_DIR);
        }
        if (!Files.exists(AppConstants.Base.AGENTS_DIR)) {
            Files.createDirectories(AppConstants.Base.AGENTS_DIR);
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

    /**
     * 初始化默认主智能体：agents/default/ 目录
     * 从 classpath:bootstrap/agent/default/ 复制所有文件到 agents/default/
     * 同时创建 workspace/default/ 目录（仅 context/ 和 sessions/）
     */
    private static void initDefaultAgent() throws IOException {
        Path defaultAgentDir = AppConstants.Base.agentDir("default");
        if (Files.exists(defaultAgentDir)) {
            ensureWorkspaceDir("default");
            return;
        }

        Files.createDirectories(defaultAgentDir);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // 从 bootstrap/agent/default/ 复制所有文件到 agents/default/
        Resource[] defaultResources = resolver.getResources("classpath:bootstrap/agent/default/*");
        for (Resource resource : defaultResources) {
            String fileName = resource.getFilename();
            if (fileName != null) {
                copyResourceIfNotExists(resource, defaultAgentDir.resolve(fileName));
            }
        }

        // 创建 memory/ 目录
        Files.createDirectories(defaultAgentDir.resolve("memory"));

        // 创建 workspace/default/ 目录（仅 session 运行时数据）
        ensureWorkspaceDir("default");
    }

    /**
     * 初始化子智能体：agents/{name}/
     * 从 classpath:bootstrap/subagent/{name}/ 复制所有文件到 agents/{name}/
     */
    private static void initSubagents() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // 扫描 bootstrap/subagent/ 下的所有子目录中的文件
        Resource[] subagentResources = resolver.getResources("classpath:bootstrap/subagent/*/*");

        for (Resource resource : subagentResources) {
            String fileName = resource.getFilename();
            if (fileName == null) {
                continue;
            }

            // 从 URL 中提取子目录名作为 agentId
            // URL 格式: .../bootstrap/subagent/{agentId}/{fileName}
            String url = resource.getURL().toString();
            int subagentIdx = url.lastIndexOf("bootstrap/subagent/");
            if (subagentIdx < 0) continue;
            String relativePath = url.substring(subagentIdx + "bootstrap/subagent/".length());
            int slashIdx = relativePath.indexOf('/');
            if (slashIdx < 0) continue;
            String agentId = relativePath.substring(0, slashIdx);

            Path agentDir = AppConstants.Base.agentDir(agentId);
            if (Files.exists(agentDir.resolve("agent.md"))) {
                continue;
            }

            Files.createDirectories(agentDir);
            copyResourceIfNotExists(resource, agentDir.resolve(fileName));
            log.info("初始化子智能体: {} ({})", agentId, fileName);
        }
    }

    private static void ensureWorkspaceDir(String agentId) throws IOException {
        Path workspaceDir = AppConstants.Base.agentWorkspaceDir(agentId);
        Files.createDirectories(workspaceDir.resolve("sessions"));
        Files.createDirectories(workspaceDir.resolve("context"));
    }

    private static void copyResourceIfNotExists(Resource resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    }

}
