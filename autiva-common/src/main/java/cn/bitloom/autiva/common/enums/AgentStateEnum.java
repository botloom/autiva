package cn.bitloom.autiva.common.enums;

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
     * Idle agent state enum.
     */
    IDLE("IDLE", 0),
    /**
     * Running agent state enum.
     */
    RUNNING("RUNNING", 1),
    /**
     * Finished agent state enum.
     */
    FINISHED("FINISHED", 2),
    /**
     * Error agent state enum.
     */
    ERROR("ERROR", 3),
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
