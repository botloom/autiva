package cn.bitloom.agentic.session;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class Session {

    private String id;
    private AgentIdentityEnum agentId;
    private SessionTypeEnum type;
    private SessionRespTypeEnum respType;
    private ModelTypeEnum model;
    private String source;
    private String target;
    private String parentId;
    @JSONField(serialize = false)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

}

