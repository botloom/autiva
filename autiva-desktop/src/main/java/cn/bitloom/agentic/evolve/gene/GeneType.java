package cn.bitloom.agentic.evolve.gene;

/**
 * Gene 配置类型枚举。
 * <p>
 * Gene 是 L4 爬山循环可优化的配置单元，type 决定该 Gene 优化哪类配置：
 * <ul>
 *   <li>{@link #PROMPT} - Agent 的 Prompt 片段（agent.md 中的 system/role/instructions）</li>
 *   <li>{@link #TOOL_DESC} - 工具定义描述（name/description/参数描述）</li>
 *   <li>{@link #RUBRIC} - L2 Grader 的评分规则</li>
 *   <li>{@link #SKILL_CONFIG} - 技能配置（agent.md 中的 skills 列表 + 技能参数）</li>
 * </ul>
 */
public enum GeneType {
    PROMPT,
    TOOL_DESC,
    RUBRIC,
    SKILL_CONFIG
}
