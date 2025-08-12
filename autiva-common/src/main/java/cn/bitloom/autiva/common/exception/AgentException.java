package cn.bitloom.autiva.common.exception;

/**
 * The type Agent exception.
 *
 * @author bitloom
 */
public class AgentException extends RuntimeException{
    /**
     * Instantiates a new Agent exception.
     */
    public AgentException() {
    }

    /**
     * Instantiates a new Agent exception.
     *
     * @param message the message
     */
    public AgentException(String message) {
        super(message);
    }

    /**
     * Instantiates a new Agent exception.
     *
     * @param message the message
     * @param cause   the cause
     */
    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
