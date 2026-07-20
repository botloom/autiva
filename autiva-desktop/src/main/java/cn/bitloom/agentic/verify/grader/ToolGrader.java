package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;

import java.util.List;

/**
 * 工具级校验器：在工具调用前后进行即时校验。
 * <p>
 * 用于 VerificationHook 的 {@code beforeToolCall} 和 {@code afterToolCall} 回调。
 * 不通过时返回带 block 原因的 Feedback，LLM 会根据反馈自动调整参数或行为重试。
 */
public interface ToolGrader {

    /**
     * 校验工具输入参数（beforeToolCall）。
     *
     * @param toolName 工具名称
     * @param input    原始输入参数（JSON 字符串）
     * @param rubrics  关联的 RUBRIC Gene 列表（type=RUBRIC, targetId=toolName）
     * @return 校验反馈，passed=false 时 VerificationHook 会 block 工具调用
     */
    Feedback checkArgs(String toolName, String input, List<Gene> rubrics);

    /**
     * 校验工具返回结果（afterToolCall）。
     *
     * @param toolName 工具名称
     * @param result   原始结果（JSON 字符串，通常是 ToolResult.toJson()）
     * @param rubrics  关联的 RUBRIC Gene 列表
     * @return 校验反馈，passed=false 时 VerificationHook 会将 result 替换为错误信息
     */
    Feedback checkResult(String toolName, String result, List<Gene> rubrics);

    /**
     * 是否支持该工具。默认支持所有工具。
     */
    default boolean supports(String toolName) {
        return true;
    }
}
