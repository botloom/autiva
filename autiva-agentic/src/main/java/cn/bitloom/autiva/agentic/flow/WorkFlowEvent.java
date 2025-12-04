package cn.bitloom.autiva.agentic.flow;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The type Work flow event.
 */
@Data
@Builder
public class WorkFlowEvent {
    /**
     * 事件类型：START, NODE_START, NODE_COMPLETE, COMPLETE, ERROR, CANCELLED
     */
    private EventType type;

    /**
     * 事件发生时间
     */
    private LocalDateTime timestamp;

    /**
     * 节点ID，如果事件与节点相关
     */
    private String nodeId;

    /**
     * 节点名称，如果事件与节点相关
     */
    private String nodeName;

    /**
     * 工作流名称，如果事件与工作流有关
     */
    private String workflowName;

    /**
     * 事件数据，包含事件相关的数据
     */
    private Map<String, Object> data;

    /**
     * 错误信息，如果事件类型为ERROR
     */
    private Throwable error;

    private Integer retryCount;

    /**
     * 消息
     */
    private String message;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * Start event type.
         */
        START,
        /**
         * Stop event type.
         */
        STOP,
        /**
         * Node start event type.
         */
        NODE_START,
        /**
         * Node complete event type.
         */
        NODE_COMPLETE,
        /**
         * Complete event type.
         */
        COMPLETE,
        /**
         * Error event type.
         */
        ERROR,
        /**
         * Cancelled event type.
         */
        CANCELLED
    }

    /**
     * 创建工作流开始事件
     *
     * @param workflowName the workflow name
     * @return 工作流开始事件
     */
    public static WorkFlowEvent createStartEvent(String workflowName) {
        return WorkFlowEvent.builder()
                .workflowName(workflowName)
                .type(EventType.START)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建工作流停止事件
     *
     * @param workflowName the workflow name
     * @param message      the message
     * @return the work flow event
     */
    public static WorkFlowEvent createStopEvent(String workflowName, String message) {
        return WorkFlowEvent.builder()
                .workflowName(workflowName)
                .type(EventType.STOP)
                .timestamp(LocalDateTime.now())
                .message(message)
                .build();
    }

    /**
     * 创建节点开始事件
     *
     * @param nodeId   节点ID
     * @param nodeName 节点名称
     * @return 节点开始事件
     */
    public static WorkFlowEvent createNodeStartEvent(String nodeId, String nodeName) {
        return WorkFlowEvent.builder()
                .type(EventType.NODE_START)
                .timestamp(LocalDateTime.now())
                .nodeId(nodeId)
                .nodeName(nodeName)
                .build();
    }

    /**
     * 创建节点完成事件
     *
     * @param data 节点执行结果数据
     * @return 节点完成事件
     */
    public static WorkFlowEvent createNodeCompleteEvent(Map<String, Object> data) {
        return WorkFlowEvent.builder()
                .type(EventType.NODE_COMPLETE)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    /**
     * 创建工作流完成事件
     *
     * @return 工作流完成事件
     */
    public static WorkFlowEvent createCompleteEvent() {
        return WorkFlowEvent.builder()
                .type(EventType.COMPLETE)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建错误事件
     *
     * @param error    错误异常
     * @param nodeId   节点ID，如果错误与节点相关
     * @param nodeName 节点名称，如果错误与节点相关
     * @return 错误事件
     */
    public static WorkFlowEvent createErrorEvent(Throwable error, String nodeId, String nodeName) {
        return WorkFlowEvent.builder()
                .type(EventType.ERROR)
                .timestamp(LocalDateTime.now())
                .nodeId(nodeId)
                .nodeName(nodeName)
                .error(error)
                .build();
    }

    /**
     * 创建取消事件
     *
     * @return 取消事件
     */
    public static WorkFlowEvent createCancelledEvent() {
        return WorkFlowEvent.builder()
                .type(EventType.CANCELLED)
                .timestamp(LocalDateTime.now())
                .build();
    }

}