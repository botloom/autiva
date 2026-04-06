package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Slf4j
@Component
public class MainAgent extends AbstractAgent {

    @Override
    public void run() {
        EventBus.inBoxSubscribe()
                .concatMap(event -> {
                    this.status = AgentStatusEnum.WORKING;
                    Session session = sessionManager.getById(event.getSessionId());
                    if (session.getRespType().equals(SessionRespTypeEnum.STREAM)) {
                        return this.model(ModelEnum.GLM)
                                .prompt()
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, event.getSessionId()))
                                .toolContext(Map.of("sessionId", event.getSessionId()))
                                .messages(event.getMessage())
                                .stream()
                                .chatResponse()
                                .publishOn(Schedulers.boundedElastic())
                                .doOnNext(message -> EventBus.outBoxPublish(event.getSessionId(), message.getResult().getOutput()))
                                .doOnComplete(() -> this.status = AgentStatusEnum.IDLE);
                    } else {
                        ChatResponse chatResponse = this.model(ModelEnum.GLM)
                                .prompt()
                                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, event.getSessionId()))
                                .toolContext(Map.of("sessionId", event.getSessionId()))
                                .messages(event.getMessage())
                                .call()
                                .chatResponse();
                        if (chatResponse != null) {
                            EventBus.outBoxPublish(event.getSessionId(), chatResponse.getResult().getOutput());
                        }
                        this.status = AgentStatusEnum.IDLE;
                        return Flux.empty();
                    }
                })
                .subscribe();
    }

    @Override
    protected AgentIdentityEnum getIdentity() {
        return AgentIdentityEnum.MAIN;
    }

}
