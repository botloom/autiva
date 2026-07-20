package cn.bitloom.agentic.evolve.mutation;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.safety.EvolutionSafety;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 基因突变器。
 * <p>
 * 使用 LLM 根据 L4 爬山分析引擎的优化建议，优化 Gene 的配置内容。
 * 不再是"修改技能代码"，而是"优化配置内容"（Prompt/工具描述/Rubric/技能配置）。
 */
@Slf4j
@Component
public class GeneMutator {

    private final ModelFactory modelFactory;
    private final GeneStore geneStore;
    private final EvolveConfig config;
    private final EvolutionSafety safety;
    private volatile Agent mutatorAgent;

    public GeneMutator(ModelFactory modelFactory, GeneStore geneStore,
                       EvolveConfig config, EvolutionSafety safety) {
        this.modelFactory = modelFactory;
        this.geneStore = geneStore;
        this.config = config;
        this.safety = safety;
    }

    private Agent getMutatorAgent() {
        if (mutatorAgent == null) {
            synchronized (this) {
                if (mutatorAgent == null) {
                    AgentDefinition definition = new AgentDefinition(
                            "mutator", "配置优化引擎", AgentKind.SUBAGENT,
                            List.of(), List.of(), List.of(), Map.of(),
                            "你是Autiva自优化系统的配置优化引擎。根据分析建议优化配置内容。只输出优化后的完整配置内容，不要解释。",
                            AgentDefinition.VerificationConfig.disabled()
                    );
                    mutatorAgent = Agent.builder()
                            .name("mutator")
                            .definition(definition)
                            .model(modelFactory.model(ModelTypeEnum.DEEPSEEK))
                            .systemPrompt(definition.content())
                            .logging(false)
                            .build();
                }
            }
        }
        return mutatorAgent;
    }

    /**
     * 根据优化建议突变基因配置内容。
     *
     * @param original   原始基因
     * @param issue      分析发现的问题
     * @param suggestion 优化建议
     * @return 突变后的基因，安全检查未通过或异常时返回 null
     */
    public Gene mutate(Gene original, String issue, String suggestion) {
        String prompt = """
                你是配置优化引擎。根据分析建议优化以下配置单元的内容。

                ## 当前配置
                - ID: %s
                - 类型: %s
                - 目标: %s
                - 内容:
                %s

                ## 分析发现的问题
                %s

                ## 优化建议
                %s

                要求：
                - 只输出优化后的完整配置内容，不要解释
                - 保持配置的核心功能不变
                - 针对发现的问题进行改进
                """.formatted(
                original.id(), original.type(), original.targetId(),
                original.content(), issue, suggestion
        );

        try {
            RuntimeContext ctx = new RuntimeContext("evolve-mutate");
            UserMessage userMessage = new UserMessage(prompt);
            AssistantMessage response = getMutatorAgent().runBlock(ctx, userMessage);

            if (response == null || response.getText() == null || response.getText().isEmpty()) {
                log.warn("[Evolve] Agent返回空内容，跳过突变");
                return null;
            }

            String newContent = response.getText().trim();

            Gene mutated = new Gene(
                    original.id(),
                    original.type(),
                    original.targetId(),
                    original.name(),
                    newContent,
                    original.description(),
                    original.epigeneticBoost(),
                    original.enabled(),
                    original.version() + 1,
                    original.id(),
                    original.createdAt(),
                    System.currentTimeMillis()
            );

            EvolutionSafety.SafetyCheckResult safetyResult = safety.check(original, mutated);
            if (!safetyResult.passed()) {
                log.warn("[Evolve] 突变安全检查未通过: {}", safetyResult.message());
                return null;
            }

            geneStore.upsertGene(mutated);
            log.info("[Evolve] 基因突变成功: {} v{} -> v{}", original.id(), original.version(), mutated.version());
            return mutated;

        } catch (Exception e) {
            log.error("[Evolve] 基因突变失败: {}", original.id(), e);
            return null;
        }
    }
}
