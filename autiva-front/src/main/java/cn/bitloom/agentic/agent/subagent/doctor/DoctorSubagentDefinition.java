package cn.bitloom.agentic.agent.subagent.doctor;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;

public record DoctorSubagentDefinition(SubagentReference reference, String name, String description, String content) implements SubagentDefinition {

    public static final AgentIdentityEnum IDENTITY = AgentIdentityEnum.DOCTOR;

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getKind() {
        return IDENTITY.name();
    }

    @Override
    public String toSubagentRegistrations() {
        return "- **%s**: %s (系统配置与维护)".formatted(getName(), getDescription());
    }
}
