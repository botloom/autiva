package cn.bitloom.agentic.agent;

import lombok.Getter;

@Getter
public enum AgentIdentityEnum {

    MAIN(AgentCategory.MAIN),
    EVOLVER(AgentCategory.MAIN),
    GENERIC(AgentCategory.SUBAGENT),
    DOCTOR(AgentCategory.SUBAGENT),
    A2A(AgentCategory.SUBAGENT);

    private final AgentCategory category;

    AgentIdentityEnum(AgentCategory category) {
        this.category = category;
    }

    public boolean isMain() {
        return category == AgentCategory.MAIN;
    }

    public boolean isSubagent() {
        return category == AgentCategory.SUBAGENT;
    }

    public enum AgentCategory {
        MAIN, SUBAGENT
    }
}
