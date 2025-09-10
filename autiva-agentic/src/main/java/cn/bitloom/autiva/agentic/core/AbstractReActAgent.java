package cn.bitloom.autiva.agentic.core;

import cn.bitloom.autiva.agentic.exception.AgentException;
import cn.bitloom.autiva.agentic.tool.AbstractTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;

/**
 * The type Abstract multi turn agent.
 *
 * @author bitloom
 */
@Slf4j
public abstract class AbstractReActAgent extends AbstractAgent {

    /**
     * Instantiates a new Abstract base agent.
     *
     * @param DEFAULT_SYSTEM_MESSAGE the default system message
     * @param workDir                the work dir
     * @param messageManager         the message manager
     * @param availableToolClassList the available tool class list
     * @param chatModel              the chat model
     */
    public AbstractReActAgent(String DEFAULT_SYSTEM_MESSAGE, String workDir, AbstractMessageManager<?, ?, ?> messageManager, List<Class<? extends AbstractTools<? extends AbstractAgent>>> availableToolClassList, ChatModel chatModel) {
        super(DEFAULT_SYSTEM_MESSAGE, Path.of(workDir).toAbsolutePath(), messageManager, chatModel);
        if (CollectionUtils.isNotEmpty(availableToolClassList)) {
            availableToolClassList.forEach(availableToolClass -> {
                try {
                    Constructor<? extends AbstractTools<? extends AbstractAgent>> constructor = ReflectionUtils.accessibleConstructor(availableToolClass, this.getClass());
                    constructor.setAccessible(true);
                    AbstractTools<? extends AbstractAgent> tool = constructor.newInstance(this);
                    this.availableToolList.add(tool);
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                         InvocationTargetException e) {
                    throw new AgentException("Tool实例化失败", e);
                }
            });
        }
    }

    /**
     * 多轮异步循环执行对话，直到状态为 FINISHED
     *
     * @param sessionId the session id
     * @return the mono
     */
    public Flux<String> run(String sessionId) {
        return this.messageManager
                .subscribe(sessionId)
                .concatMap(message ->
                        this.chatClient.prompt()
                                .messages(message)
                                .tools(this.availableToolList.toArray())
                                .stream()
                                .content()
                );
    }

}
