package cn.bitloom.agentic.evolve.verify;

/**
 * 验证判定结果。
 * <p>
 * 用于标记单个验证维度的判定。
 */
public enum Verdict {
    /** 通过 */
    PASS,
    /** 失败 */
    FAIL,
    /** 不确定（证据不足，需人工复核） */
    UNCERTAIN
}
