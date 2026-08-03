package cn.bitloom.agentic.evolve.trajectory;

/**
 * 轨迹结果类型。
 * <p>
 * 用于标记一次对话轨迹的最终执行结果。
 */
public enum TrajectoryOutcome {
    /** 完全成功 */
    SUCCESS,
    /** 部分成功（存在失败但有进展） */
    PARTIAL_SUCCESS,
    /** 失败 */
    FAILURE,
    /** 未知（无法判定） */
    UNKNOWN
}
