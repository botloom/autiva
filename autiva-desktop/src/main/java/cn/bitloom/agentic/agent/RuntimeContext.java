package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.event.EventPublisher;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 运行时上下文，携带 sessionId、userId、branch、eventSink 等运行时信息。
 * <p>
 * Agent 实例无状态可复用，per-session 的运行时数据通过 RuntimeContext 传入。
 * 参考 AgentScope 2.0 的 RuntimeContext.builder().sessionId(...).userId(...).put(...).build() 模式。
 * <p>
 * Agent.runStream 内部通过 Flux.create 创建 sink，包装为 EventPublisher 后
 * 调用 {@link #put} 注入到 params，再通过 {@link #toToolContextMap()} 传递给 ToolContext。
 * <p>
 * branch 用于子智能体隔离：主智能体 branch 为 null（root），子智能体 branch 形如
 * "subagent.{name}"。事件持久化和历史检索都基于 branch 进行过滤。
 */
@Getter
public class RuntimeContext {

    private final String sessionId;
    private final String userId;
    private final String branch;
    private final String projectPath;
    private final Map<String, Object> params;

    private RuntimeContext(String sessionId, String userId, String branch, String projectPath, Map<String, Object> params) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.branch = branch;
        this.projectPath = projectPath;
        this.params = params != null ? params : new HashMap<>();
    }

    /**
     * 运行时注入参数（如 Agent.runStream 内部注入 eventSink）。
     */
    public void put(String key, Object value) {
        this.params.put(key, value);
    }

    public Object getParam(String key) {
        return this.params.get(key);
    }

    /**
     * 转为 ToolContext 用的 Map（含 sessionId、userId、branch、projectPath 及 params 中所有项）。
     */
    public Map<String, Object> toToolContextMap() {
        Map<String, Object> map = new HashMap<>(params);
        if (sessionId != null) {
            map.put("sessionId", sessionId);
        }
        if (userId != null) {
            map.put("userId", userId);
        }
        if (branch != null) {
            map.put("branch", branch);
        }
        if (projectPath != null) {
            map.put("projectPath", projectPath);
        }
        return map;
    }

    public static RuntimeContextBuilder builder() {
        return new RuntimeContextBuilder();
    }

    public static class RuntimeContextBuilder {
        private String sessionId;
        private String userId;
        private String branch;
        private String projectPath;
        private final Map<String, Object> params = new HashMap<>();

        public RuntimeContextBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public RuntimeContextBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public RuntimeContextBuilder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public RuntimeContextBuilder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public RuntimeContextBuilder put(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        public RuntimeContextBuilder eventSink(EventPublisher eventSink) {
            this.params.put("eventSink", eventSink);
            return this;
        }

        public RuntimeContext build() {
            return new RuntimeContext(sessionId, userId, branch, projectPath, params);
        }
    }
}
