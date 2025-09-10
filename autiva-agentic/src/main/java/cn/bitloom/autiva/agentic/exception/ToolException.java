package cn.bitloom.autiva.agentic.exception;

/**
 * The type Tool exception.
 *
 * @author bitloom
 */
public class ToolException extends RuntimeException{
    /**
     * Instantiates a new Tool exception.
     */
    public ToolException() {
    }

    /**
     * Instantiates a new Tool exception.
     *
     * @param message the message
     */
    public ToolException(String message) {
        super(message);
    }

    /**
     * Instantiates a new Tool exception.
     *
     * @param message the message
     * @param cause   the cause
     */
    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
