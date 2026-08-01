package cn.bitloom.agentic.event;

/**
 * 事件发布器，供工具层（AutivaToolCallingManager、WriteTool、EditTool）
 * 把事件推送到 Agent.run 返回的事件流。
 * <p>
 * Agent.runStream 内部通过 Flux.create 创建 sink，包装为 EventPublisher
 * 注入 RuntimeContext，再通过 ToolContext 传递给工具层。
 */
@FunctionalInterface
public interface EventPublisher {
    void publish(AbstractEvent event);
}
