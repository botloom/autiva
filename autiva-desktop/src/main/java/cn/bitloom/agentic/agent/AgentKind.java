package cn.bitloom.agentic.agent;

/**
 * 智能体类型枚举。
 * MAIN: 主智能体，直接面向用户，拥有 workspace 和 agents/ 下的长期配置
 * SUBAGENT: 子智能体，被其他 Agent 通过 Task 工具调用，内置不可配置
 */
public enum AgentKind {
    MAIN,
    SUBAGENT
}
