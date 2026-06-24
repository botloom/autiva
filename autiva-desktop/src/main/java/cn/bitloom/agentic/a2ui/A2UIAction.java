package cn.bitloom.agentic.a2ui;

import java.util.Map;

/**
 * A2UI 动作模型(双轨制)。
 * <p>
 * 组件通过 action 属性触发行为,分为两类:
 * <ul>
 *   <li>{@link Event} - 智能体事件,派发到 Agent 处理</li>
 *   <li>{@link FunctionCall} - 本地函数,渲染器执行(无需网络)</li>
 * </ul>
 */
public sealed interface A2UIAction permits A2UIAction.Event, A2UIAction.FunctionCall {

    /** 智能体事件:发送数据到 Agent 处理 */
    record Event(
            String name,
            Map<String, Object> context
    ) implements A2UIAction {}

    /** 本地函数:渲染器沙盒中执行,无需网络往返 */
    record FunctionCall(
            String call,
            Map<String, Object> args
    ) implements A2UIAction {}
}
