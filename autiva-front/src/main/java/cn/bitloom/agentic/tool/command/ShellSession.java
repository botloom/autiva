package cn.bitloom.agentic.tool.command;

import cn.bitloom.agentic.tool.command.shell.Shell;
import cn.bitloom.agentic.tool.command.shell.ShellDetector;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ShellSession {

    private static final String STATE_DIR = ".autiva";
    private static final String STATE_FILE = "shell-state.json";
    private static final int SCHEMA_VERSION = 2;

    private final Path stateFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Shell shell;
    @Getter
    private volatile ShellState state;

    public ShellSession(Shell shell) {
        this.shell = shell;
        Path home = Paths.get(System.getProperty("user.home"));
        Path dir = home.resolve(STATE_DIR);
        this.stateFile = dir.resolve(STATE_FILE);
        load();
    }

    public ShellSession() {
        this(ShellDetector.detect());
    }

    public Shell getShell() {
        return shell;
    }

    public String currentDir() {
        ShellState s = state;
        String cwd = (s != null && s.cwd != null && !s.cwd.isEmpty())
                ? s.cwd
                : System.getProperty("user.home");
        if (!isValidDirectory(cwd)) {
            String fallback = System.getProperty("user.home");
            log.warn("[ShellSession] cwd '{}' does not exist, falling back to '{}'", cwd, fallback);
            s.cwd = fallback;
            persist();
            return fallback;
        }
        return cwd;
    }

    public String resolveWorkdir(String perCallWorkdir) {
        if (perCallWorkdir != null && !perCallWorkdir.isEmpty() && isValidDirectory(perCallWorkdir)) {
            return perCallWorkdir;
        }
        return currentDir();
    }

    public Map<String, String> mergedEnv(Map<String, String> perCallEnv) {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        ShellState s = state;
        if (s != null && s.env != null) {
            env.putAll(s.env);
        }
        if (perCallEnv != null && !perCallEnv.isEmpty()) {
            env.putAll(perCallEnv);
        }
        return env;
    }

    public Map<String, String> mergedEnv() {
        return mergedEnv(null);
    }

    public synchronized void updateFromOutput(String output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        Pattern p = shell.pwdPattern();
        Matcher m = p.matcher(output);
        if (m.find()) {
            String newCwd = m.group(1);
            if (!newCwd.isEmpty() && !newCwd.equals(state.cwd)) {
                state.cwd = newCwd;
                persist();
                log.debug("[ShellSession] cwd updated to {}", newCwd);
            }
        }
    }

    public synchronized void setCwd(String cwd) {
        if (cwd != null && !cwd.isEmpty() && isValidDirectory(cwd)) {
            state.cwd = cwd;
            persist();
        } else if (cwd != null && !cwd.isEmpty()) {
            log.warn("[ShellSession] setCwd('{}') ignored: directory does not exist", cwd);
        }
    }

    public synchronized void setEnv(String key, String value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (state.env == null) {
            state.env = new ConcurrentHashMap<>();
        }
        state.env.put(key, value);
        persist();
    }

    private static boolean isValidDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        try {
            return Files.isDirectory(Paths.get(path));
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void load() {
        ShellState s = new ShellState();
        s.cwd = System.getProperty("user.home");
        s.env = new ConcurrentHashMap<>();
        s.schemaVersion = SCHEMA_VERSION;
        if (Files.exists(stateFile)) {
            try {
                ShellState loaded = mapper.readValue(stateFile.toFile(), ShellState.class);
                if (loaded != null) {
                    if (loaded.cwd != null && !loaded.cwd.isEmpty()) {
                        s.cwd = loaded.cwd;
                    }
                    if (loaded.env != null) {
                        s.env = new ConcurrentHashMap<>(loaded.env);
                    }
                }
            } catch (IOException e) {
                log.warn("[ShellSession] failed to load state from {}: {}",
                        stateFile, e.getMessage());
            }
        }
        this.state = s;
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(stateFile.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            log.warn("[ShellSession] failed to persist state: {}", e.getMessage());
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShellState {
        public int schemaVersion = SCHEMA_VERSION;
        public String cwd;
        public Map<String, String> env;
    }
}
