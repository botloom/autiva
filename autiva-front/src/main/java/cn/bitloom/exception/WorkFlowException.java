package cn.bitloom.exception;

public class WorkFlowException extends AutivaException {

    public WorkFlowException(String errorCode, String message) {
        super(errorCode, message);
    }

    public WorkFlowException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public WorkFlowException(String errorCode, String message, boolean recoverable) {
        super(errorCode, message, recoverable);
    }

    public WorkFlowException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(errorCode, message, cause, recoverable);
    }

    public static WorkFlowException configError(String detail, Throwable cause) {
        return new WorkFlowException("WORKFLOW_CONFIG_ERROR", "工作流配置错误: " + detail, cause);
    }

    public static WorkFlowException configError(String detail) {
        return new WorkFlowException("WORKFLOW_CONFIG_ERROR", "工作流配置错误: " + detail);
    }

    public static WorkFlowException executionError(String node, Throwable cause) {
        return new WorkFlowException("WORKFLOW_EXECUTION_ERROR", "工作流执行失败: 节点 " + node, cause);
    }

    public static WorkFlowException nodeNotFound(String nodeId) {
        return new WorkFlowException("WORKFLOW_CONFIG_ERROR", "节点不存在: " + nodeId);
    }
}
