package cn.bitloom.agentic.evolve.runtime;

import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ShellGeneExecutor implements GeneExecutor {

    private static final long SHELL_TIMEOUT_SECONDS = 30;

    @Override
    public GeneRuntimeType supportedType() {
        return GeneRuntimeType.SHELL;
    }

    @Override
    public GeneResult execute(String code, String input) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", code);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (input != null && !input.isEmpty()) {
                process.outputWriter(StandardCharsets.UTF_8).write(input);
                process.outputWriter(StandardCharsets.UTF_8).flush();
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return GeneResult.fail("Shell执行超时 (" + SHELL_TIMEOUT_SECONDS + "s)", System.currentTimeMillis() - start);
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return GeneResult.ok(output.toString().trim(), System.currentTimeMillis() - start);
            } else {
                return GeneResult.fail("Shell退出码: " + exitCode + ", 输出: " + output.toString().trim(), System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            return GeneResult.fail("Shell执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
