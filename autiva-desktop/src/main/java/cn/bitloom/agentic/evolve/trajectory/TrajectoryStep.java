package cn.bitloom.agentic.evolve.trajectory;

import java.time.Instant;

/**
 * 轨迹步骤，记录 Agent 执行过程中的单个原子操作。
 *
 * @param index    步骤序号（从 0 开始递增）
 * @param type     步骤类型
 * @param content  步骤内容（模型回复文本 / 工具响应摘要等）
 * @param toolName 工具名称（仅 TOOL_CALL / TOOL_RESPONSE 有值，其余为 null）
 * @param success  该步骤是否成功
 * @param timestamp 步骤发生时间
 */
public record TrajectoryStep(
        int index,
        StepType type,
        String content,
        String toolName,
        boolean success,
        Instant timestamp
) {

    /** 步骤类型 */
    public enum StepType {
        /** 模型调用（LLM 生成回复或工具调用请求） */
        MODEL_CALL,
        /** 工具调用（Agent 请求执行工具） */
        TOOL_CALL,
        /** 工具响应（工具执行完成后的返回结果） */
        TOOL_RESPONSE,
        /** 用户输入 */
        USER_INPUT
    }
}
