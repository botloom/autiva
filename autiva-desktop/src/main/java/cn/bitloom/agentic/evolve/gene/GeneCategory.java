package cn.bitloom.agentic.evolve.gene;

/**
 * 基因分类枚举，标识基因在进化系统中的角色。
 */
public enum GeneCategory {
    /** 策略类基因：指导 Agent 的行为倾向 */
    STRATEGY,
    /** 规则类基因：约束 Agent 必须遵守的规则 */
    RULE,
    /** 约束类基因：限制 Agent 的操作边界 */
    CONSTRAINT,
    /** 流程类基因：定义 Agent 的执行步骤 */
    PROCEDURE
}
