package cn.bitloom.agentic.tool.command.shell;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
public final class ShellDetector {

    private static volatile Shell cached;

    private ShellDetector() {
    }

    public static Shell detect() {
        Shell local = cached;
        if (local != null) {
            return local;
        }
        synchronized (ShellDetector.class) {
            if (cached == null) {
                cached = Shell.isWindows() ? detectWindows() : detectUnix();
                log.info("[ShellDetector] detected shell: {}", cached.name());
            }
            return cached;
        }
    }

    private static Shell detectWindows() {
        Optional<Path> pwsh = findOnPath("pwsh.exe");
        if (pwsh.isPresent()) {
            return new PowerShellShell(pwsh.get().toString(), true);
        }
        Path ps5 = findOnPath("powershell.exe")
                .orElse(Paths.get(
                        System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                        "System32", "WindowsPowerShell", "v1.0", "powershell.exe"));
        return new PowerShellShell(ps5.toString(), false);
    }

    private static Shell detectUnix() {
        Optional<Path> bash = findOnPath("bash");
        if (bash.isPresent()) {
            return new BashShell(bash.get().toString());
        }
        Path sh = findOnPath("sh").orElse(Paths.get("/bin/sh"));
        return new ShShell(sh.toString());
    }

    public static Optional<Path> findOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }
        String sep = Shell.isWindows() ? ";" : ":";
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(sep))) {
            if (dir.isEmpty()) {
                continue;
            }
            Path candidate = Paths.get(dir, name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
