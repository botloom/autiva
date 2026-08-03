package cn.bitloom.config;

import cn.bitloom.agentic.evolve.EvolverAgent;
import cn.bitloom.agentic.evolve.experience.ExperienceEngine;
import cn.bitloom.agentic.evolve.gene.GeneInjector;
import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.agentic.evolve.gene.GeneSelector;
import cn.bitloom.agentic.evolve.routing.RoutingEngine;
import cn.bitloom.agentic.evolve.safety.EvolutionSafety;
import cn.bitloom.agentic.evolve.solidify.CanaryCheck;
import cn.bitloom.agentic.evolve.solidify.Solidifier;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRecorder;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRepository;
import cn.bitloom.agentic.evolve.verify.ProcessVerifier;
import cn.bitloom.agentic.evolve.verify.QualityVerifier;
import cn.bitloom.agentic.evolve.verify.ResultVerifier;
import cn.bitloom.agentic.evolve.verify.TrajectoryVerifier;
import cn.bitloom.agentic.evolve.verify.VerificationContext;
import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.agentic.memory.FileSystemAgentMemoryStore;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.constant.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 进化系统 Spring Bean 配置。
 * <p>
 * 仅在 {@code app.evolve.enabled=true} 时创建进化相关 Bean。
 * GeneRepository 和 GeneSelector 已通过 {@code @Component} 无条件注册，
 * 因为它们开销极小且被 GeneInjector（Advisor）依赖，无论进化是否启用都可能需要加载基因。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.evolve", name = "enabled", havingValue = "true")
public class EvolveConfig {

    private final ModelFactory modelFactory;
    private final GeneRepository geneRepository;
    private final GeneSelector geneSelector;

    /**
     * 进化系统专用记忆存储，指向 {@link AppConstants.Evolve#MEMORY_DIR}。
     */
    @Bean
    public AgentMemoryStore evolveMemoryStore() {
        log.info("[EvolveConfig] 创建进化记忆存储: {}", AppConstants.Evolve.MEMORY_DIR);
        return new FileSystemAgentMemoryStore(AppConstants.Evolve.MEMORY_DIR);
    }

    @Bean
    public TrajectoryRepository trajectoryRepository() {
        return new TrajectoryRepository();
    }

    @Bean
    public ResultVerifier resultVerifier() {
        return new ResultVerifier();
    }

    @Bean
    public ProcessVerifier processVerifier() {
        return new ProcessVerifier();
    }

    /**
     * 质量层验证器，使用 DeepSeek 模型作为 LLM Judge。
     */
    @Bean
    public QualityVerifier qualityVerifier() {
        ChatClient chatClient = ChatClient.builder(modelFactory.model(ModelTypeEnum.DEEPSEEK)).build();
        return new QualityVerifier(chatClient);
    }

    @Bean
    public TrajectoryVerifier trajectoryVerifier(ResultVerifier resultVerifier,
                                                  ProcessVerifier processVerifier,
                                                  QualityVerifier qualityVerifier) {
        return new TrajectoryVerifier(resultVerifier, processVerifier, qualityVerifier);
    }

    /**
     * 默认验证上下文（不执行编译/测试检查，不限制工具）。
     * <p>
     * 后续可根据 AgentDefinition.VerificationConfig 动态构建。
     */
    @Bean
    public VerificationContext verificationContext() {
        return VerificationContext.defaults();
    }

    /**
     * 经验引擎，使用 DeepSeek 模型进行跨轨迹归纳。
     */
    @Bean
    public ExperienceEngine experienceEngine(TrajectoryRepository trajectoryRepository,
                                              TrajectoryVerifier verifier,
                                              AgentMemoryStore evolveMemoryStore) {
        ChatClient chatClient = ChatClient.builder(modelFactory.model(ModelTypeEnum.DEEPSEEK)).build();
        return new ExperienceEngine(trajectoryRepository, verifier, evolveMemoryStore, chatClient);
    }

    @Bean
    public CanaryCheck canaryCheck(TrajectoryVerifier verifier,
                                    VerificationContext verificationContext) {
        return new CanaryCheck(verifier, verificationContext);
    }

    @Bean
    public Solidifier solidifier(GeneRepository geneRepository,
                                 CanaryCheck canaryCheck,
                                 AgentMemoryStore evolveMemoryStore) {
        return new Solidifier(geneRepository, canaryCheck, evolveMemoryStore);
    }

    @Bean
    public EvolutionSafety evolutionSafety() {
        return new EvolutionSafety();
    }

    @Bean
    public RoutingEngine routingEngine(GeneRepository geneRepository) {
        return new RoutingEngine(geneRepository);
    }

    @Bean
    public EvolverAgent evolverAgent(TrajectoryRepository trajectoryRepository,
                                      TrajectoryVerifier verifier,
                                      ExperienceEngine experienceEngine,
                                      Solidifier solidifier,
                                      EvolutionSafety safety,
                                      RoutingEngine routingEngine,
                                      VerificationContext verificationContext) {
        log.info("[EvolveConfig] 创建 EvolverAgent");
        return new EvolverAgent(
                trajectoryRepository,
                verifier,
                experienceEngine,
                geneRepository,
                geneSelector,
                solidifier,
                safety,
                routingEngine,
                verificationContext
        );
    }

    /**
     * 轨迹记录器 Hook，在 Agent 构建时通过 hooks 列表注入。
     */
    @Bean
    public TrajectoryRecorder trajectoryRecorder(TrajectoryRepository trajectoryRepository) {
        log.info("[EvolveConfig] 创建 TrajectoryRecorder");
        return new TrajectoryRecorder(trajectoryRepository);
    }

    /**
     * 基因注入 Advisor，在 Agent 构建时通过 advisors 列表注入。
     * <p>
     * order=220，在 SkillContextAdvisor(210) 之后执行。
     */
    @Bean
    public GeneInjector geneInjector() {
        log.info("[EvolveConfig] 创建 GeneInjector");
        return GeneInjector.builder().geneSelector(geneSelector).build();
    }
}
