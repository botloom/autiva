package cn.bitloom.agentic.tool.command;

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
 * Persists custom environment variables across restarts.
 */
@Slf4j
public class ShellSession {

    private static final Path STATE_FILE = Paths.get(
            System.getProperty("user.home"), ".autiva", "shell-env.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Map<String, String> env;

    public ShellSession() {
        load();
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
                EnvState s = mapper.readValue(STATE_FILE.toFile(), EnvState.class);
                if (s != null && s.env != null) {
                    this.env = new ConcurrentHashMap<>(s.env);
                }
            } catch (IOException e) {
                log.warn("[ShellSession] failed to load env: {}", e.getMessage());
            }
        }
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(), new EnvState(env));
        } catch (IOException e) {
            log.warn("[ShellSession] failed to persist env: {}", e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnvState(Map<String, String> env) {}
}
