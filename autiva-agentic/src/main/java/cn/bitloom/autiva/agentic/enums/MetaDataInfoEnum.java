package cn.bitloom.autiva.agentic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The enum Meta data info enum.
 *
 * @author bitlloom
 */
@Getter
@RequiredArgsConstructor
public enum MetaDataInfoEnum {
    /**
     * Event meta data info enum.
     */
    EVENT,
    /**
     * Manual meta data info enum.
     */
    MANUAL,
    /**
     * Assistant meta data info enum.
     */
    ASSISTANT,
    /**
     * Session id meta data info enum.
     */
    SESSION_ID,
    /**
     * Target meta data info enum.
     */
    TARGET,
    ;
}
