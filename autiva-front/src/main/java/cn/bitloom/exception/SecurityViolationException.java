package cn.bitloom.exception;

public class SecurityViolationException extends AutivaException {

    public SecurityViolationException(String message) {
        super("SECURITY_VIOLATION", message);
    }

    public SecurityViolationException(String message, Throwable cause) {
        super("SECURITY_VIOLATION", message, cause);
    }

    public static SecurityViolationException absolutePath(String path) {
        return new SecurityViolationException("不允许使用绝对路径: '" + path + "'");
    }

    public static SecurityViolationException pathTraversal(String path) {
        return new SecurityViolationException("路径遍历攻击: '" + path + "'");
    }
}
