package cn.bitloom.autiva.agentic.exception;

/**
 * The type Work flow exception.
 *
 * @author ningyu
 */
public class WorkFlowException extends RuntimeException{
    /**
     * Instantiates a new Work flow exception.
     */
    public WorkFlowException() {
    }

    /**
     * Instantiates a new Work flow exception.
     *
     * @param message the message
     */
    public WorkFlowException(String message) {
        super(message);
    }

    /**
     * Instantiates a new Work flow exception.
     *
     * @param message the message
     * @param cause   the cause
     */
    public WorkFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
