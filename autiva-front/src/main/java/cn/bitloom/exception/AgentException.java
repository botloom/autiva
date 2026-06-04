package cn.bitloom.exception;

public class AgentException extends AutivaException {

    public AgentException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AgentException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public AgentException(String errorCode, String message, boolean recoverable) {
        super(errorCode, message, recoverable);
    }

    public AgentException(String errorCode, String message, Throwable cause, boolean recoverable) {
        super(errorCode, message, cause, recoverable);
    }

    public static AgentException notFound(String name) {
        return new AgentException("AGENT_NOT_FOUND", "智能体不存在: " + name);
    }

    public static AgentException configError(String detail, Throwable cause) {
        return new AgentException("AGENT_CONFIG_ERROR", "智能体配置错误: " + detail, cause);
    }

    public static AgentException subagentNotFound(String name) {
        return new AgentException("SUBAGENT_NOT_FOUND", "子智能体不存在: " + name);
    }

    public static AgentException subagentAlreadyExists(String name) {
        return new AgentException("SUBAGENT_ALREADY_EXISTS", "子智能体已存在: " + name);
    }

    public static AgentException subagentExecutionFailed(String name, Throwable cause) {
        return new AgentException("SUBAGENT_EXECUTION_FAILED", "子智能体执行失败: " + name, cause);
    }

    public static AgentException subagentResolverNotFound(String reference) {
        return new AgentException("SUBAGENT_RESOLVER_NOT_FOUND", "未找到能够解析子代理引用的SubagentResolver: " + reference);
    }

    public static AgentException subagentExecutorNotFound(String kind) {
        return new AgentException("SUBAGENT_EXECUTOR_NOT_FOUND", "未找到子代理类型 " + kind + " 的执行器");
    }
}
