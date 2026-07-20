package cn.bitloom.exception;

public class EvolveException extends AutivaException {

    public EvolveException(String errorCode, String message) {
        super(errorCode, message);
    }

    public EvolveException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public EvolveException(String errorCode, String message, boolean recoverable) {
        super(errorCode, message, recoverable);
    }

    public EvolveException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(errorCode, message, cause, recoverable);
    }

    public static EvolveException geneNotFound(String geneId) {
        return new EvolveException("EVOLVE_GENE_NOT_FOUND", "基因不存在: " + geneId);
    }

    public static EvolveException cycleFailed(String reason) {
        return new EvolveException("EVOLVE_CYCLE_FAILED", "进化周期失败: " + reason);
    }

    public static EvolveException solidifyFailed(String reason) {
        return new EvolveException("EVOLVE_SOLIDIFY_FAILED", "固化失败: " + reason);
    }

    public static EvolveException storageError(String detail, Throwable cause) {
        return new EvolveException("STORAGE_WRITE_ERROR", "进化存储错误: " + detail, cause);
    }
}
