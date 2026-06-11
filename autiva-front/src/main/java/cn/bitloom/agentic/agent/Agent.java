package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.agent.advisor.HookAdvisor;
import cn.bitloom.agentic.hook.AgentHook;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.session.Session;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一智能体类，合并了原 AbstractAgent + MainAgent + SubagentExecutor 的功能。
 * 主智能体和子智能体都是 Agent 类的实例，区别仅在 AgentDefinition 的配置。
 * <p>
 * 只能通过 Builder 创建。Hook 机制基于 Spring AI Advisor 实现，通过 HookAdvisor 桥接。
 */
@Slf4j
public class Agent {

    @Getter
    private final String agentId;
    @Getter
    private final AgentKind kind;
    @Getter
    private final AgentDefinition definition;
    @Getter
    private final ModelFactory modelFactory;
    @Getter
    private final List<ToolCallback> tools;
    @Getter
    private final List<AgentHook> hooks;

    /**
     * 缓存的 ChatClient，按模型类型按需创建
     */
    private final Map<ModelTypeEnum, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    private Agent(String agentId, AgentKind kind, AgentDefinition definition,
                  ModelFactory modelFactory, List<ToolCallback> tools,
                  List<AgentHook> hooks) {
        this.agentId = agentId;
        this.kind = kind;
        this.definition = definition;
        this.modelFactory = modelFactory;
        this.tools = tools;
        this.hooks = hooks;
    }

    // ===== 主智能体执行（Session 驱动） =====

    /**
     * 流式调用 LLM（主智能体模式）
     */
    public Flux<AssistantMessage> runStream(Session session, Message message) {
        return getOrCreateChatClient(session.getModel())
                .prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getId())
                        .param("model", session.getModel()))
                .toolContext(Map.of("sessionId", session.getId(), "model", session.getModel()))
                .toolCallbacks(this.tools)
                .messages(message)
                .stream()
                .chatResponse()
                .map(chatResponse -> chatResponse.getResult().getOutput());
    }

    /**
     * 阻塞调用 LLM（主智能体模式）
     */
    public AssistantMessage runBlock(Session session, Message message) {
        ChatResponse response = getOrCreateChatClient(session.getModel())
                .prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getId())
                        .param("model", session.getModel()))
                .toolContext(Map.of("sessionId", session.getId(), "model", session.getModel()))
                .toolCallbacks(this.tools)
                .messages(message)
                .call()
                .chatResponse();
        return response.getResult().getOutput();
    }

    // ===== 内部方法 =====

    /**
     * 获取或创建指定模型的 ChatClient（按需缓存），通过 HookAdvisor 注册 Hooks
     */
    private ChatClient getOrCreateChatClient(ModelTypeEnum model) {
        return chatClientCache.computeIfAbsent(model, m -> {
            ChatClient.Builder builder = ChatClient.builder(modelFactory.model(m));
            if (!hooks.isEmpty()) {
                builder.defaultAdvisors(new HookAdvisor(hooks));
            }
            return builder.build();
        });
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId = "default";
        private AgentKind kind;
        private AgentDefinition definition;
        private ModelFactory modelFactory;
        private List<ToolCallback> tools = new ArrayList<>();
        private List<AgentHook> hooks = new ArrayList<>();

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder kind(AgentKind kind) { this.kind = kind; return this; }
        public Builder definition(AgentDefinition definition) { this.definition = definition; return this; }
        public Builder modelFactory(ModelFactory modelFactory) { this.modelFactory = modelFactory; return this; }
        public Builder tools(List<ToolCallback> tools) { this.tools = tools; return this; }
        public Builder hooks(List<AgentHook> hooks) { this.hooks = hooks; return this; }

        public Agent build() {
            if (kind == null && definition != null) {
                kind = definition.kind();
            }
            return new Agent(agentId, kind, definition, modelFactory, tools, hooks);
        }
    }
}
