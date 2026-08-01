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
 * 应用启动初始化，按照新的目录结构组织：
 * ~/.autiva/
 * ├── settings.json
 * ├── agents/{work,code}/
 * ├── subagents/
 * ├── workspace/{work,code}/
 * └── skills/
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
            initWorkAgent();
        } catch (Exception e) {
            log.error("初始化 work 主智能体失败", e);
        }
        try {
            initCodeAgent();
        } catch (Exception e) {
            log.error("初始化 code 主智能体失败", e);
        }
        try {
            initSubagents();
        } catch (Exception e) {
            log.error("初始化子智能体失败", e);
        }
        try {
            initCodeGlobalRules();
        } catch (Exception e) {
            log.error("初始化 code 全局规则失败", e);
        }
        try {
            initWorkMemory();
        } catch (Exception e) {
            log.error("初始化 work 记忆目录失败", e);
        }
        try {
            initProjectsDir();
        } catch (Exception e) {
            log.error("初始化项目目录失败", e);
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
        if (!Files.exists(AppConstants.Base.SUBAGENTS_DIR)){
            Files.createDirectories(AppConstants.Base.SUBAGENTS_DIR);
        }
        if (!Files.exists(AppConstants.Base.SKILLS_DIR)){
            Files.createDirectories(AppConstants.Base.SKILLS_DIR);
        }
        if (!Files.exists(AppConstants.Base.LOGS_DIR)){
            Files.createDirectories(AppConstants.Base.LOGS_DIR);
        }
        // workspace/work/sessions
        Files.createDirectories(AppConstants.Base.WORKSPACE_DIR.resolve("work/sessions"));
        // workspace/code
        Files.createDirectories(AppConstants.Base.WORKSPACE_DIR.resolve("code"));
    }

    private static void initSettingsFile() throws IOException {
        Path settingsFile = AppConstants.Base.SETTINGS_FILE;
        if (Files.exists(settingsFile)) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource configResource = resolver.getResource("classpath:bootstrap/settings.yaml");
        Files.copy(configResource.getInputStream(), settingsFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 初始化 work 主智能体（从 classpath:bootstrap/agent/work/ 复制到 agents/work/）
     */
    private static void initWorkAgent() throws IOException {
        Path agentDir = AppConstants.Agents.agentDir(AppConstants.Agents.WORK_AGENT);
        if (Files.exists(agentDir)) {
            return;
        }
        Files.createDirectories(agentDir);
        copyClasspathResources("classpath:bootstrap/agent/work/*", agentDir);
    }

    /**
     * 初始化 code 主智能体（从 classpath:bootstrap/agent/code/ 复制到 agents/code/）
     */
    private static void initCodeAgent() throws IOException {
        Path agentDir = AppConstants.Agents.agentDir(AppConstants.Agents.CODE_AGENT);
        if (!Files.exists(agentDir)) {
            Files.createDirectories(agentDir);
        }
        copyClasspathResources("classpath:bootstrap/agent/code/*", agentDir);
    }

    /**
     * 初始化子智能体（从 classpath:bootstrap/subagent/ 下所有 agent.md 复制到 ~/.autiva/subagents/{name}/agent.md）
     * 首次启动复制，已存在则跳过
     */
    private static void initSubagents() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:bootstrap/subagent/*/agent.md");
        for (Resource resource : resources) {
            String subagentName = extractDirName(resource, "bootstrap/subagent");
            if (subagentName == null) continue;
            Path targetDir = AppConstants.Agents.subagentDir(subagentName);
            if (Files.exists(targetDir.resolve("agent.md"))) {
                continue;
            }
            Files.createDirectories(targetDir);
            Files.copy(resource.getInputStream(), targetDir.resolve("agent.md"), StandardCopyOption.REPLACE_EXISTING);
            log.info("复制子智能体: {}", subagentName);
        }
    }

    /**
     * 初始化 code 模式全局规则文件 workspace/code/AUTIVA.md
     */
    private static void initCodeGlobalRules() throws IOException {
        Path rulesFile = AppConstants.Rules.codeGlobalRulesFile();
        if (Files.exists(rulesFile)) {
            return;
        }
        Files.createDirectories(rulesFile.getParent());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource template = resolver.getResource("classpath:bootstrap/code-autiva-rules.md");
        Files.copy(template.getInputStream(), rulesFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 初始化 work 模式记忆目录 workspace/work/memory/MEMORY.md
     */
    private static void initWorkMemory() throws IOException {
        Path memoryDir = AppConstants.Memory.workMemoryDir();
        Path memoryFile = memoryDir.resolve("MEMORY.md");
        if (Files.exists(memoryFile)) {
            return;
        }
        Files.createDirectories(memoryDir);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource template = resolver.getResource("classpath:bootstrap/memory-template.md");
        Files.copy(template.getInputStream(), memoryFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 初始化项目目录
     */
    private static void initProjectsDir() throws IOException {
        if (!Files.exists(AppConstants.Base.PROJECTS_DIR)) {
            Files.createDirectories(AppConstants.Base.PROJECTS_DIR);
        }
    }

    /**
     * 从 classpath 复制资源到目标目录（已存在的文件跳过）
     */
    private static void copyClasspathResources(String pattern, Path targetDir) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(pattern);
        for (Resource resource : resources) {
            if (Objects.isNull(resource.getFilename())) {
                continue;
            }
            Path target = targetDir.resolve(resource.getFilename());
            if (Files.exists(target)) {
                continue;
            }
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 从 Resource URL 中提取目录名（如 subagent/doctor/agent.md → doctor）
     */
    private static String extractDirName(Resource resource, String prefix) {
        try {
            String url = resource.getURL().toString();
            int idx = url.indexOf(prefix + "/");
            if (idx < 0) return null;
            String after = url.substring(idx + prefix.length() + 1);
            int slash = after.indexOf("/");
            return slash > 0 ? after.substring(0, slash) : null;
        } catch (IOException e) {
            return null;
        }
    }

}
