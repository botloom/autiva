package cn.bitloom.agentic.tool.command.shell;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Base class for POSIX-compatible shells (bash, sh).
 * Eliminates code duplication between BashShell and ShShell.
 */
@Slf4j
public abstract class AbstractPosixShell implements Shell {

    private static final Pattern PWD_PATTERN = Pattern.compile(
            "__PWD__([^\\s\"]+)");

    private final String shellPath;

    protected AbstractPosixShell(String shellPath) {
        this.shellPath = shellPath;
    }

    /**
     * Return extra arguments for the shell (e.g., "--noprofile", "--norc" for bash).
     */
    protected abstract List<String> shellArgs();

    @Override
    public List<String> buildProcessCommand(String command, String cwd) {
        String fullCommand = "cd '" + escapeSingleQuote(cwd) + "' ; " + command
                + " ; echo \"__PWD__$(pwd)\"";
        List<String> cmd = new ArrayList<>();
        cmd.add(shellPath);
        cmd.addAll(shellArgs());
        cmd.add("-c");
        cmd.add(fullCommand);
        return cmd;
    }

    @Override
    public ProcessBuilder createProcessBuilder(String command, String cwd, Map<String, String> env) {
        List<String> cmd = buildProcessCommand(command, cwd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(resolveCwd(cwd)));
        pb.redirectErrorStream(true);

        Map<String, String> pbEnv = pb.environment();
        if (env != null) {
            pbEnv.putAll(env);
        }

        log.info("[{}] starting: {} (cwd={})", name(), String.join(" ", cmd), resolveCwd(cwd));
        return pb;
    }

    @Override
    public Pattern pwdPattern() {
        return PWD_PATTERN;
    }

    public String shellPath() {
        return shellPath;
    }

    protected static String escapeSingleQuote(String s) {
        return s.replace("'", "'\\''");
    }
}
