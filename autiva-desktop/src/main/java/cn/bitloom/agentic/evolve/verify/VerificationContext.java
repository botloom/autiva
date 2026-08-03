package cn.bitloom.agentic.evolve.verify;

import java.util.List;
import java.util.Map;

/**
 * 验证上下文，携带验证所需的环境配置和策略。
 *
 * @param projectPath    项目路径（用于编译检查等环境真值验证）
 * @param allowedTools   允许使用的工具列表（为空表示不限制）
 * @param policies       策略映射（维度名称 -> 策略描述）
 * @param runCompileCheck 是否执行编译检查
 * @param runTestCheck    是否执行测试检查
 */
public record VerificationContext(
        String projectPath,
        List<String> allowedTools,
        Map<String, String> policies,
        boolean runCompileCheck,
        boolean runTestCheck
) {

    /**
     * 创建默认验证上下文（不限制工具、不执行编译/测试检查）。
     */
    public static VerificationContext defaults() {
        return new VerificationContext(
                null,
                List.of(),
                Map.of(),
                false,
                false
        );
    }
}
