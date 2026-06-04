package cn.bitloom.agentic.tool.command.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface Shell {

    String name();

    List<String> buildProcessCommand(String command, String cwd);

    ProcessBuilder createProcessBuilder(String command, String cwd, Map<String, String> env);

    Pattern pwdPattern();

    /**
     * Resolve cwd: return the given cwd if it's a valid directory, otherwise fall back to user.home.
     */
    default String resolveCwd(String cwd) {
        if (cwd != null && !cwd.isEmpty()) {
            try {
                if (Files.isDirectory(Paths.get(cwd))) return cwd;
            } catch (Exception ignored) {
            }
        }
        return System.getProperty("user.home");
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static Shell detect() {
        return ShellDetector.detect();
    }
}
