package cn.bitloom.autiva.agentic.core;

import cn.bitloom.autiva.agentic.enums.AgentStateEnum;
import cn.bitloom.autiva.agentic.tool.AbstractTools;
import cn.bitloom.autiva.agentic.tool.CommonTools;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.template.st.StTemplateRenderer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The type Abstract agent.
 *
 * @author bitloom
 */
@Slf4j
public abstract class AbstractAgent {

    /**
     * The Default system message.
     */
    protected final String DEFAULT_SYSTEM_MESSAGE;

    /**
     * The Work dir.
     */
    @Getter
    protected final Path workDir;

    /**
     * The State.
     */
    @Getter
    protected final AtomicReference<AgentStateEnum> state;

    /**
     * The Session map.
     */
    protected final AbstractMessageManager<?, ?, ?> messageManager;

    /**
     * The Available tool list.
     */
    protected final List<AbstractTools<? extends AbstractAgent>> availableToolList;

    /**
     * The Chat client.
     */
    protected final ChatClient chatClient;


    /**
     * Instantiates a new Abstract base agent.
     *
     * @param DEFAULT_SYSTEM_MESSAGE the default system message
     * @param workDir                the work dir
     * @param messageManager         the message manager
     * @param chatModel              the chat model
     */
    public AbstractAgent(String DEFAULT_SYSTEM_MESSAGE, Path workDir, AbstractMessageManager<?, ?, ?> messageManager, ChatModel chatModel) {
        this.DEFAULT_SYSTEM_MESSAGE = DEFAULT_SYSTEM_MESSAGE;
        this.workDir = workDir;
        this.state = new AtomicReference<>(AgentStateEnum.UP);
        this.messageManager = messageManager;
        this.availableToolList = new ArrayList<>();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTemplateRenderer(
                        StTemplateRenderer.builder()
                                .startDelimiterToken('$')
                                .endDelimiterToken('$')
                                .build()
                )
                .defaultSystem(
                        systemPrompt -> systemPrompt
                                .text(this.DEFAULT_SYSTEM_MESSAGE + "初始工作目录为：$workDir$")
                                .param("workDir", this.workDir.toString())
                )
                .defaultAdvisors(messageManager)
                .defaultTools(new CommonTools(this))
                .build();
    }


}
