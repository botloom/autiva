package cn.bitloom.exception;

public class StorageException extends AutivaException {

    public StorageException(String errorCode, String message) {
        super(errorCode, message);
    }

    public StorageException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public StorageException(String errorCode, String message, boolean recoverable) {
        super(errorCode, message, recoverable);
    }

    public StorageException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(errorCode, message, cause, recoverable);
    }

    public static StorageException readError(String path, Throwable cause) {
        return new StorageException("STORAGE_READ_ERROR", "读取失败: " + path, cause);
    }

    public static StorageException writeError(String path, Throwable cause) {
        return new StorageException("STORAGE_WRITE_ERROR", "写入失败: " + path, cause);
    }

    public static StorageException readError(String path) {
        return new StorageException("STORAGE_READ_ERROR", "读取失败: " + path);
    }

    public static StorageException writeError(String path) {
        return new StorageException("STORAGE_WRITE_ERROR", "写入失败: " + path);
    }

    public static StorageException dirError(String path, Throwable cause) {
        return new StorageException("STORAGE_WRITE_ERROR", "目录操作失败: " + path, cause);
    }

    public static StorageException dirNotFound(String path) {
        return new StorageException("STORAGE_READ_ERROR", "目录不存在: " + path);
    }

    public static StorageException notADir(String path) {
        return new StorageException("STORAGE_READ_ERROR", "路径不是目录: " + path);
    }
}
