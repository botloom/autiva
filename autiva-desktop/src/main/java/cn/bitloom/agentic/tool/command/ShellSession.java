package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.ShellExecutor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化 Shell 会话状态（cwd + env）。
 *
 * <p>原由 PtySession 的 Shell 进程维护 cwd，现在改为通过此类的状态文件持久化。
 * 每次命令执行时，cwd 通过命令前缀（cd /d 或 cd）注入。</p>
 */
@Slf4j
public class ShellSession {

    private static final Path STATE_FILE = Paths.get(
            System.getProperty("user.home"), ".autiva", "shell-state.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String cwd;
    private volatile Map<String, String> env;

    public ShellSession() {
        load();
    }

    /** 获取当前工作目录，验证有效性 */
    public synchronized String getCwd() {
        if (cwd != null) {
            Path p = Paths.get(cwd);
            if (Files.isDirectory(p)) {
                return cwd;
            }
            // cwd 无效，回退到 user.home 并持久化
            log.warn("[ShellSession] persisted cwd '{}' does not exist, falling back to user.home", cwd);
            cwd = System.getProperty("user.home");
            persist();
        }
        return System.getProperty("user.home");
    }

    /** 设置当前工作目录 */
    public synchronized void setCwd(String cwd) {
        if (cwd != null && !cwd.isEmpty()) {
            this.cwd = cwd;
            persist();
        }
    }

    /** 更新 cwd（从命令输出中提取） */
    public synchronized void updateCwd(String newCwd) {
        if (newCwd != null && !newCwd.isEmpty()) {
            this.cwd = newCwd;
            persist();
        }
    }

    /**
     * 解析工作目录：per-call workdir 优先，否则使用持久化的 cwd。
     * 如果 workdir 无效，回退到 user.home 并返回回退通知。
     */
    public synchronized String resolveWorkdir(String perCallWorkdir) {
        if (perCallWorkdir != null && !perCallWorkdir.isEmpty()) {
            Path p = Paths.get(perCallWorkdir);
            if (Files.isDirectory(p)) {
                return perCallWorkdir;
            }
            // workdir 无效，回退
            log.warn("[ShellSession] workdir '{}' does not exist, falling back to '{}'", perCallWorkdir, getCwd());
            return getCwd();
        }
        return getCwd();
    }

    /** Merge system env + persisted env + per-call overrides. */
    public Map<String, String> mergedEnv(Map<String, String> perCallEnv) {
        Map<String, String> result = new LinkedHashMap<>(System.getenv());
        if (env != null) result.putAll(env);
        if (perCallEnv != null) result.putAll(perCallEnv);
        return result;
    }

    public synchronized void setEnv(String key, String value) {
        if (key == null || key.isEmpty()) return;
        if (env == null) env = new ConcurrentHashMap<>();
        env.put(key, value);
        persist();
    }

    private synchronized void load() {
        if (Files.exists(STATE_FILE)) {
            try {
                ShellState s = mapper.readValue(STATE_FILE.toFile(), ShellState.class);
                if (s != null) {
                    if (s.cwd != null && !s.cwd.isEmpty()) {
                        this.cwd = s.cwd;
                    }
                    if (s.env != null) {
                        this.env = new ConcurrentHashMap<>(s.env);
                    }
                }
            } catch (IOException e) {
                log.warn("[ShellSession] failed to load state: {}", e.getMessage());
            }
        }
        if (cwd == null) {
            cwd = System.getProperty("user.home");
        }
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(),
                    new ShellState(cwd, env));
        } catch (IOException e) {
            log.warn("[ShellSession] failed to persist state: {}", e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShellState(String cwd, Map<String, String> env) {}

    /**
     * 构建环境信息块（对标 Claude Code 的 {@code <env>} 注入），供系统提示使用。
     *
     * <p>读取当前持久化 cwd + 平台名称，让模型无需调用工具即可感知工作目录和运行平台。
     *
     * @return 形如 {@code "\n\n<env>\nWorking directory: ...\nPlatform: ...\n</env>"} 的字符串
     */
    public static String envBlock() {
        String cwd = new ShellSession().getCwd();
        String platform = ShellExecutor.create().platformName();
        return "\n\n<env>\nWorking directory: " + cwd + "\nPlatform: " + platform + "\n</env>";
    }

    /**
     * 构建环境信息块（带项目路径覆盖）。
     *
     * <p>code 模式下传入项目路径作为 Working directory，让模型感知项目根目录。
     * projectPath 为 null 时回退到持久化 cwd。
     *
     * @param projectPath 项目路径（覆盖 cwd）；null 时用持久化 cwd
     * @return env 块字符串
     */
    public static String envBlock(String projectPath) {
        String cwd = (projectPath != null && !projectPath.isBlank())
                ? projectPath
                : new ShellSession().getCwd();
        String platform = ShellExecutor.create().platformName();
        return "\n\n<env>\nWorking directory: " + cwd + "\nPlatform: " + platform + "\n</env>";
    }
}
