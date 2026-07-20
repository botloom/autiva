package cn.bitloom.exception;

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
}
