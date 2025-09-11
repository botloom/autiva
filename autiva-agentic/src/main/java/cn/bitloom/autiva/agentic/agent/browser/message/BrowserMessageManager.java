package cn.bitloom.autiva.agentic.agent.browser.message;

import cn.bitloom.autiva.agentic.agent.browser.InteractiveBrowser;
import cn.bitloom.autiva.agentic.core.AbstractMessageManager;
import cn.bitloom.autiva.agentic.enums.MetaDataInfoEnum;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * The type Browser message manager.
 *
 * @author bitloom
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserMessageManager extends AbstractMessageManager<BrowserAgentInput, BrowserAgentOutput, BrowserAgentMemory> {

    private final InteractiveBrowser interactiveBrowser;

    @Override
    protected void init() {
        this.input = new BrowserAgentInput();
        this.output = new BrowserAgentOutput();
        this.memoryList = new ArrayList<>();
    }

    @Override
    protected ChatClientRequest preHandle(ChatClientRequest request) {
        Prompt prompt = request.prompt();
        SystemMessage systemMessage = prompt.getSystemMessage();
        UserMessage userMessage = prompt.getUserMessage();
        Map<String, Object> metadata = userMessage.getMetadata();
        if (metadata.containsKey(MetaDataInfoEnum.MANUAL.name())) {
            this.input.setTarget(userMessage.getText());
            this.input.setSessionId((String) metadata.get(MetaDataInfoEnum.SESSION_ID.name()));
        } else if (metadata.containsKey(MetaDataInfoEnum.EVENT.name())) {
//            this.input.setEvent(userMessage.getText());
        } else {
            this.memoryList.add(JSON.parseObject(userMessage.getText(), BrowserAgentMemory.class));
        }
        this.input.setHistory(this.memoryList);
        this.input.setBrowser(interactiveBrowser.getBrowserStatus((String) metadata.get(MetaDataInfoEnum.SESSION_ID.name())));
        return request.mutate()
                .prompt(
                        Prompt.builder()
                                .chatOptions(prompt.getOptions())
                                .messages(systemMessage, UserMessage.builder().text(JSON.toJSONString(this.input)).build())
                                .build()
                ).build();
    }

    @Override
    protected void postHandle(ChatClientResponse response) {
        if (Objects.nonNull(response.chatResponse())) {
            AssistantMessage assistantMessage = response.chatResponse().getResult().getOutput();
            BrowserAgentOutput browserAgentOutput = JSON.parseObject(assistantMessage.getText(), BrowserAgentOutput.class);
            if (browserAgentOutput != null) {
                BrowserAgentMemory browserAgentMemory = new BrowserAgentMemory();
                browserAgentMemory.setId(String.valueOf(this.memoryList.size()));
                browserAgentMemory.setPreStepAssessment(browserAgentOutput.getPreStepAssessment());
                browserAgentMemory.setMemory(browserAgentOutput.getMemory());
                browserAgentMemory.setNextStep(browserAgentOutput.getNextStep());
                browserAgentMemory.setResult(browserAgentOutput.getResult());
                this.emit(UserMessage.builder().metadata(Map.of(MetaDataInfoEnum.SESSION_ID.name(), this.input.getSessionId(), MetaDataInfoEnum.ASSISTANT.name(), "")).text(JSON.toJSONString(browserAgentMemory)).build());
            }
        }
    }

}
