package cn.bitloom.autiva.agentic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The enum Agent state enum.
 *
 * @author bitloom
 */
@Getter
@RequiredArgsConstructor
public enum AgentStateEnum {

    /**
     * Up agent state enum.
     */
    UP("UP", 0),

    /**
     * Down agent state enum.
     */
    DOWN("DOWN", 1),

    ;
    private final String name;
    private final Integer code;

    /**
     * Gets by name.
     *
     * @param name the name
     * @return the by name
     */
    public static AgentStateEnum getByName(String name) {
        for (AgentStateEnum agentStateEnum : AgentStateEnum.values()) {
            if (agentStateEnum.name.equals(name)) {
                return agentStateEnum;
            }
        }
        return null;
    }
}
