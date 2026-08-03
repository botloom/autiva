package cn.bitloom.vm;

import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Work 模式首页 ViewModel。
 * <p>
 * 当前为空壳实现，预留扩展点。
 * buildMessageWithContext 直接返回原文，onSwitchAgent 空实现。
 */
@Slf4j
@Component
public class WorkHomePageViewModel extends AbstractHomePageViewModel {

    public WorkHomePageViewModel(FileSystemSessionManager fileSystemSessionManager,
                                 AgentDefinitionManager definitionManager,
                                 ModelFactory modelFactory,
                                 Toolkit toolkit,
                                 cn.bitloom.agentic.skill.SkillManager skillManager,
                                 cn.bitloom.agentic.evolve.EvolveAgentEnricher evolveEnricher) {
        super(fileSystemSessionManager, definitionManager, modelFactory, toolkit, skillManager, evolveEnricher);
    }

    @Override
    protected String buildMessageWithContext(String text) {
        return text;
    }

    @Override
    protected void onSwitchAgent(String agentId) {
        // work 模式无模式切换专有逻辑
    }
}
