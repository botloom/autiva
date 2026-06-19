package cn.bitloom.exception;

import cn.bitloom.agentic.evolve.signal.SignalType;

public abstract class AutivaException extends RuntimeException {

    private final String errorCode;
    private final boolean recoverable;

    protected AutivaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.recoverable = false;
    }

    protected AutivaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.recoverable = false;
    }

    protected AutivaException(String errorCode, String message, boolean recoverable) {
        super(message);
        this.errorCode = errorCode;
        this.recoverable = recoverable;
    }

    protected AutivaException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.recoverable = recoverable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public SignalType toSignalType() {
        return switch (errorCode) {
            case "AGENT_NOT_FOUND", "AGENT_CONFIG_ERROR", "SUBAGENT_NOT_FOUND", "SUBAGENT_EXECUTION_FAILED"
                    -> SignalType.LOG_ERROR;
            case "TOOL_VALIDATION_ERROR" -> SignalType.LOG_ERROR;
            case "TOOL_EXECUTION_ERROR", "TOOL_NOT_FOUND" -> SignalType.ERRSIG;
            case "TOOL_BYPASS" -> SignalType.TOOL_BYPASS;
            case "STORAGE_READ_ERROR", "STORAGE_WRITE_ERROR" -> SignalType.MEMORY_MISSING;
            case "EVOLVE_GENE_NOT_FOUND", "EVOLVE_CYCLE_FAILED" -> SignalType.CAPABILITY_GAP;
            case "EVOLVE_SOLIDIFY_FAILED" -> SignalType.REPAIR_LOOP_DETECTED;
            case "SECURITY_VIOLATION" -> SignalType.LOG_ERROR;
            case "WORKFLOW_CONFIG_ERROR", "WORKFLOW_EXECUTION_ERROR" -> SignalType.LOG_ERROR;
            default -> SignalType.LOG_ERROR;
        };
    }
}
