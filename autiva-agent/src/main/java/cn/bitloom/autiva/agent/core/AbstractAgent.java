package cn.bitloom.autiva.agent.core;

import cn.bitloom.autiva.agent.tool.ToolSet;
import cn.bitloom.autiva.common.enums.AgentStateEnum;
import cn.bitloom.autiva.common.exception.AgentException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.template.st.StTemplateRenderer;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The type Abstract agent.
 *
 * @author bitloom
 */
@Slf4j
@Getter
public abstract class AbstractAgent {

    private final String CONVERSATION_ID;
    private final String WORK_DIR;
    private final String DEFAULT_SYSTEM_MESSAGE;
    private final AtomicReference<AgentStateEnum> STATE;
    private final Integer DUPLICATE_THRESHOLD;
    private final ChatMemory CHAT_MEMORY;
    /**
     * The Chat client.
     */
    protected final ChatClient CHAT_CLIENT;
    /**
     * The Available tool list.
     */
    protected final List<String> AVAILABLE_TOOL_LIST;

    /**
     * Instantiates a new Abstract base agent.
     *
     * @param AVAILABLE_TOOL_LIST  the available tool list
     * @param chatModel            the chat model
     * @param chatMemoryRepository the chat memory repository
     */
    public AbstractAgent(String CONVERSATION_ID, String WORK_DIR, String DEFAULT_SYSTEM_MESSAGE, List<String> AVAILABLE_TOOL_LIST, ChatModel chatModel, ChatMemoryRepository chatMemoryRepository) {
        this.CONVERSATION_ID = CONVERSATION_ID;
        this.WORK_DIR = WORK_DIR;
        this.DEFAULT_SYSTEM_MESSAGE = DEFAULT_SYSTEM_MESSAGE;
        this.STATE = new AtomicReference<>(AgentStateEnum.IDLE);
        this.DUPLICATE_THRESHOLD = 2;
        this.AVAILABLE_TOOL_LIST = AVAILABLE_TOOL_LIST;
        this.CHAT_MEMORY = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        this.CHAT_CLIENT = ChatClient.builder(chatModel)
                .defaultTemplateRenderer(
                        StTemplateRenderer.builder()
                                .startDelimiterToken('$')
                                .endDelimiterToken('$')
                                .build()
                )
                .defaultSystem(
                        systemPrompt -> systemPrompt
                                .text(this.DEFAULT_SYSTEM_MESSAGE)
                                .text("初始工作目录为：$workDir$")
                                .text("如果你想在任何时候停止交互，请使用$terminate$工具/函数调用。")
                                .param("workDir", this.WORK_DIR)
                                .param("terminate", ToolSet.TERMINATE_TOOL)
                )
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(this.CHAT_MEMORY).build()
                )
                .defaultToolNames(ToolSet.ASK_HUMAN_TOOL)
                .build();
    }


    /**
     * Run.
     */
    public void run() {
        if (!this.STATE.get().equals(AgentStateEnum.IDLE)) {
            throw new AgentException("Agent is not idle");
        }
        this.STATE.set(AgentStateEnum.RUNNING);
        while (!this.STATE.get().equals(AgentStateEnum.FINISHED)) {
            this.step();
            if (this.isStuck()) {
                this.handleStuck();
            }
        }
    }

    /**
     * Is stuck boolean.
     *
     * @return the boolean
     */
    protected Boolean isStuck() {
        List<Message> messageList = this.CHAT_MEMORY.get(this.CONVERSATION_ID);
        if (messageList.size() < 2) {
            return false;
        }
        Message lastMessage = messageList.get(messageList.size() - 1);
        if (StringUtils.isBlank(lastMessage.getText())) {
            return true;
        }
        long duplicateCount = messageList.stream()
                .filter(message -> message.getMessageType().equals(MessageType.ASSISTANT))
                .filter(message -> message.getText().equals(lastMessage.getText()))
                .count();
        return duplicateCount > DUPLICATE_THRESHOLD;
    }

    /**
     * Handle stuck.
     */
    protected void handleStuck() {
        this.CHAT_MEMORY.add(
                this.CONVERSATION_ID,
                SystemMessage.builder()
                        .text("发现重复的回应。考虑新的策略，避免重蹈已经尝试过的无效路径。")
                        .build()
        );
    }

    private void step() {
        UserMessage input = this.input();
        Flux<ChatClientResponse> flux = this.CHAT_CLIENT.prompt()
                .user(
                        userMessage -> userMessage
                                .text(input.getText())
                                .media(input.getMedia().toArray(new Media[0]))
                )
                .advisors(
                        advisor -> advisor
                                .param(ChatMemory.CONVERSATION_ID, this.CONVERSATION_ID)
                )
                .toolNames(this.AVAILABLE_TOOL_LIST.toArray(new String[0]))
                .stream()
                .chatClientResponse();
        this.output(flux);
    }

    /**
     * Input message.
     *
     * @return the message
     */
    abstract protected UserMessage input();

    /**
     * Output.
     *
     * @param result the result
     */
    abstract protected void output(Flux<ChatClientResponse> result);
}
