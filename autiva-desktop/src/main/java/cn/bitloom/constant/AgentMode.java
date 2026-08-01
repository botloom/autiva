package cn.bitloom.constant;

/**
 * 智能体模式枚举（应用层语义）。
 * core 层不感知 work/code 差异，所有模式判断由应用层通过此枚举处理。
 */
public enum AgentMode {
    WORK("work"),
    CODER("code");

    private final String agentId;

    AgentMode(String agentId) {
        this.agentId = agentId;
    }

    public String agentId() {
        return agentId;
    }

    public static AgentMode fromAgentId(String agentId) {
        if (CODER.agentId.equals(agentId)) {
            return CODER;
        }
        return WORK;
    }

    public boolean matches(String agentId) {
        return this.agentId.equals(agentId);
    }
}
