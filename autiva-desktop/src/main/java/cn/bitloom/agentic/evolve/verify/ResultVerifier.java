package cn.bitloom.agentic.evolve.verify;

import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryStep;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 结果层验证器 — 读取环境真值判断"事情是否真的办成"。
 * <p>
 * 验证策略：
 * <ol>
 *   <li>检查轨迹中所有 TOOL_CALL 步骤的 success 状态</li>
 *   <li>如果 runCompileCheck=true 且 projectPath 非空，执行 {@code mvn compile}</li>
 * </ol>
 * 纯代码验证，无外部依赖。
 */
@Slf4j
public class ResultVerifier {

    /** 编译超时时间（秒） */
    private static final long COMPILE_TIMEOUT_SECONDS = 120;

    /**
     * 验证结果层。
     *
     * @param trajectory 被验证的轨迹
     * @param context    验证上下文
     * @return 维度名称为 "task_result" 的验证结果
     */
    public DimensionResult verify(Trajectory trajectory, VerificationContext context) {
        StringBuilder evidence = new StringBuilder();

        // 1. 检查所有工具调用步骤的成功状态
        boolean allToolsSuccess = true;
        int toolCallCount = 0;
        int toolFailCount = 0;
        for (TrajectoryStep step : trajectory.steps()) {
            if (step.type() == TrajectoryStep.StepType.TOOL_CALL) {
                toolCallCount++;
                if (!step.success()) {
                    allToolsSuccess = false;
                    toolFailCount++;
                    evidence.append("工具调用失败: ").append(step.toolName()).append("; ");
                }
            }
        }
        evidence.append(String.format("工具调用统计: %d 次, 失败 %d 次; ", toolCallCount, toolFailCount));

        // 2. 执行编译检查（可选）
        boolean compileSuccess = true;
        if (context.runCompileCheck() && context.projectPath() != null && !context.projectPath().isBlank()) {
            compileSuccess = runCompileCheck(context.projectPath());
            if (compileSuccess) {
                evidence.append("编译通过; ");
            } else {
                evidence.append("编译失败; ");
            }
        }

        // 3. 综合判定
        boolean success = allToolsSuccess && compileSuccess;
        Verdict verdict = success ? Verdict.PASS : Verdict.FAIL;
        double score = success ? 1.0 : 0.0;
        String reason = success
                ? "所有工具调用成功" + (context.runCompileCheck() ? "且编译通过" : "")
                : "存在工具调用失败或编译失败";

        return new DimensionResult("task_result", verdict, evidence.toString(), score, reason);
    }

    /**
     * 执行 Maven 编译检查。
     *
     * @param projectPath 项目路径
     * @return 编译是否成功
     */
    private boolean runCompileCheck(String projectPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q")
                    .directory(new File(projectPath))
                    .redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出（防止进程因输出缓冲区满而阻塞）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[ResultVerifier] 编译超时（{}秒），项目: {}", COMPILE_TIMEOUT_SECONDS, projectPath);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.debug("[ResultVerifier] 编译失败，退出码: {}，输出: {}", exitCode,
                        output.substring(0, Math.min(500, output.length())));
            }
            return exitCode == 0;
        } catch (Exception e) {
            log.error("[ResultVerifier] 执行编译检查失败，项目: {}", projectPath, e);
            return false;
        }
    }
}
