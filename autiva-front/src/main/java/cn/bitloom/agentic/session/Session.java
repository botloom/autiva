package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.message.MessageBus;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Setter
@Getter
@Builder
public class Session {

    private String id;
    private String agentId;
    private SessionTypeEnum type;
    private SessionRespTypeEnum respType;
    private ModelTypeEnum model;
    private String source;
    private String parentId;
    @Builder.Default
    private Integer memoryCursor = 0;
    @Builder.Default
    private SessionState state = SessionState.IDLE;
    @Builder.Default
    private String title = "新对话";
    @Builder.Default
    private Long createdAt = System.currentTimeMillis();
    @Builder.Default
    private Long updateAt = System.currentTimeMillis();

    @JSONField(serialize = false)
    private MessageBus messageBus;

    @JSONField(serialize = false)
    private Agent agent;

    @JSONField(serialize = false)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @JSONField(serialize = false)
    private final List<AssistantMessage> roundMessages = Collections.synchronizedList(new ArrayList<>());

    /**
     * 启动消息处理循环：订阅 inBox，收到消息后调用 Agent 执行
     */
    public void start(SessionManager sessionManager) {
        if (this.messageBus == null || this.agent == null) {
            log.warn("无法启动消息循环: sessionId={}, eventBus={}, agent={}", id, this.messageBus != null, this.agent != null);
            return;
        }

        this.messageBus.inBoxSubscribe()
                .concatMap(message -> {
                    this.updateAt = System.currentTimeMillis();
                    this.roundMessages.clear();
                    if (this.respType == SessionRespTypeEnum.STREAM) {
                        return this.agent.runStream(this, message)
                                .doOnNext(assistantMsg -> {
                                    this.messageBus.outBoxPublish(assistantMsg);
                                    this.roundMessages.add(assistantMsg);
                                })
                                .doOnComplete(() -> {
                                    // Hook 逻辑由 Agent 的 HookAdvisor 处理
                                    this.state = SessionState.IDLE;
                                    sessionManager.saveContext(id);
                                });
                    } else {
                        AssistantMessage response = this.agent.runBlock(this, message);
                        this.messageBus.outBoxPublish(response);
                        roundMessages.add(response);
                        // Hook 逻辑由 Agent 的 HookAdvisor 处理
                        this.state = SessionState.IDLE;

                        // 保存上下文快照
                        sessionManager.saveContext(id);
                        return Flux.just(response);
                    }
                })
                .subscribe();
    }

    public void stop() {
        this.messageBus.stop();
        this.state = SessionState.STOPPED;
    }

    public Boolean isStop() {
        return this.state == SessionState.STOPPED;
    }

}
