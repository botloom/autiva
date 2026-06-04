package cn.bitloom.agentic.tool.command.shell;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class PowerShellShell implements Shell {

    private static final Pattern PWD_PATTERN = Pattern.compile(
            "__PWD__\"?([^\"`\\s]+)\"?");

    private final String shellPath;
    private final boolean isCore;

    public PowerShellShell(String shellPath, boolean isCore) {
        this.shellPath = shellPath;
        this.isCore = isCore;
    }

    @Override
    public String name() {
        return isCore ? "PowerShell Core (pwsh)" : "PowerShell 5 (Windows PowerShell)";
    }

    @Override
    public List<String> buildProcessCommand(String command, String cwd) {
        // Build the full command string, then encode as Base64 UTF-16LE for -EncodedCommand.
        // This avoids PowerShell parsing issues with special characters (quotes, parentheses, etc.)
        // in the user command, and ensures encoding settings execute before any user code.
        String fullCommand = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
                + "$OutputEncoding=[System.Text.Encoding]::UTF8; "
                + "Set-Location -LiteralPath '" + escapeSingleQuote(cwd) + "'; "
                + command + "; "
                + "Write-Output \"__PWD__$((Get-Location).Path)\"";

        byte[] utf16leBytes = fullCommand.getBytes(StandardCharsets.UTF_16LE);
        String encodedCommand = Base64.getEncoder().encodeToString(utf16leBytes);

        List<String> cmd = new ArrayList<>();
        cmd.add(shellPath);
        cmd.add("-NoProfile");
        cmd.add("-NonInteractive");
        cmd.add("-ExecutionPolicy");
        cmd.add("Bypass");
        cmd.add("-OutputFormat");
        cmd.add("Text");
        cmd.add("-EncodedCommand");
        cmd.add(encodedCommand);
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
        pbEnv.put("PYTHONIOENCODING", "utf-8");
        pbEnv.put("PYTHONUTF8", "1");
        pbEnv.put("LANG", "en_US.UTF-8");
        pbEnv.put("TERM", "dumb");
        pbEnv.put("NO_COLOR", "1");

        log.info("[PowerShellShell] starting: {} (cwd={})", String.join(" ", cmd), resolveCwd(cwd));
        return pb;
    }

    @Override
    public Pattern pwdPattern() {
        return PWD_PATTERN;
    }

    public boolean isCore() {
        return isCore;
    }

    public String shellPath() {
        return shellPath;
    }

    private static String escapeSingleQuote(String s) {
        return s.replace("'", "''");
    }
}
