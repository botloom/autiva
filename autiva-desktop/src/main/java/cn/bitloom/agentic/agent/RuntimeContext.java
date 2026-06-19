package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.session.Session;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 运行时上下文，通过 ChatClientRequest.context() 传递给 Advisor。
 * <p>
 * 主智能体场景：包含 Session，Advisor 从中获取 Session 状态
 * 子智能体场景：无 Session，只包含必要的参数（conversationId 等）
 */
@Getter
public class RuntimeContext {
    private final Session session;
    private final String conversationId;
    private final Map<String, Object> params;

    public RuntimeContext(Session session) {
        this.session = session;
        this.conversationId = session != null ? session.getId() : null;
        this.params = new HashMap<>();
    }

    /** 创建无 Session 的 RuntimeContext（子智能体场景） */
    public RuntimeContext(String conversationId) {
        this.session = null;
        this.conversationId = conversationId;
        this.params = new HashMap<>();
    }

    public RuntimeContext param(String key, Object value) {
        this.params.put(key, value);
        return this;
    }

    public Object getParam(String key) {
        return this.params.get(key);
    }
}
