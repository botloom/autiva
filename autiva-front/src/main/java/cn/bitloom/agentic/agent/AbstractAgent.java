package cn.bitloom.agentic.agent;

import cn.bitloom.agentic.advisor.LoggingAdvisor;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.agentic.tool.ToolManager;
import cn.bitloom.agentic.workspace.WorkspaceManager;
import cn.bitloom.config.ConfigManager;
import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.template.st.StTemplateRenderer;

import java.time.LocalDateTime;
import java.util.EnumMap;

/**
 * The type Abstract agent.
 */
public abstract class AbstractAgent {

    private final EnumMap<ModelEnum, ChatClient> chatClientMap = new EnumMap<>(ModelEnum.class);

    /**
     * The Workspace manager.
     */
    @Resource
    protected WorkspaceManager workspaceManager;
    /**
     * The Tool manager.
     */
    @Resource
    protected ToolManager toolManager;
    /**
     * The Skill manager.
     */
    @Resource
    protected SkillManager skillManager;
    @Resource
    private ChatModel deepSeekChatModel;
    @Resource
    private ChatModel zhiPuAiChatModel;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private LoggingAdvisor loggingAdvisor;
    @Resource
    private ToolCallingManager toolCallingManager;
    @Resource
    protected ConfigManager configManager;
    @Resource
    protected SessionManager sessionManager;
    @Resource
    protected AgentManager agentManager;

    /**
     * The Status.
     */
    @Getter
    protected AgentStatusEnum status = AgentStatusEnum.IDLE;

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        ChatClient dsChatClient = ChatClient.builder(this.deepSeekChatModel)
                .defaultTemplateRenderer(
                        StTemplateRenderer.builder()
                                .startDelimiterToken('$')
                                .endDelimiterToken('$')
                                .build()
                )
                .defaultSystem(this.getSystemPrompt())
                .defaultOptions(this.toolManager.getToolCallOption(this.configManager.getAgentToolList(this.getIdentity().name())))
                .defaultAdvisors(a -> a.advisors(
                                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                                this.loggingAdvisor,
                                ToolCallAdvisor.builder()
                                        .toolCallingManager(toolCallingManager)
                                        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                                        .conversationHistoryEnabled(true)
                                        .disableMemory()
                                        .build()
                        )
                )
                .build();
        this.chatClientMap.put(ModelEnum.DEEPSEEK, dsChatClient);
        ChatClient zChatClient = ChatClient.builder(this.zhiPuAiChatModel)
                .defaultTemplateRenderer(
                        StTemplateRenderer.builder()
                                .startDelimiterToken('$')
                                .endDelimiterToken('$')
                                .build()
                )
                .defaultSystem(this.getSystemPrompt())
                .defaultOptions(this.toolManager.getToolCallOption(this.configManager.getAgentToolList(this.getIdentity().name())))
                .defaultAdvisors(a -> a.advisors(
                                MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
                                this.loggingAdvisor,
                                ToolCallAdvisor.builder()
                                        .toolCallingManager(toolCallingManager)
                                        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                                        .conversationHistoryEnabled(true)
                                        .disableMemory()
                                        .build()
                        )
                )
                .build();
        this.chatClientMap.put(ModelEnum.GLM, zChatClient);
        this.run();
    }

    private String getSystemPrompt() {
        return "## 技能\n\n" +
                this.skillManager.getDescription() +
                "\n---\n\n## 工作目录\n\n" +
                AppConstants.Base.WORKSPACE_DIR.resolve(this.getIdentity().name()) +
                "\n---\n\n## 项目上下文\n\n" +
                this.agentManager.getDescription(this.getIdentity().name()) +
                "\n---\n\n## 时间\n\n" +
                LocalDateTime.now() +
                "\n---\n\n## 运行时\n\n" +
                "agent:" +
                this.getIdentity();
    }

    /**
     * Model chat client.
     *
     * @param model the model
     * @return the chat client
     */
    protected ChatClient model(ModelEnum model) {
        return this.chatClientMap.getOrDefault(model, this.chatClientMap.get(ModelEnum.DEEPSEEK));
    }

    /**
     * Run.
     */
    protected abstract void run();

    /**
     * Gets identity.
     *
     * @return the identity
     */
    protected abstract AgentIdentityEnum getIdentity();


}
