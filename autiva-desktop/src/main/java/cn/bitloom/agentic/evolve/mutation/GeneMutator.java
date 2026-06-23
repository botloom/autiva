package cn.bitloom.agentic.evolve.mutation;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.experience.Experience;
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
                            "mutator", "基因突变器", AgentKind.SUBAGENT,
                            List.of(), List.of(), List.of(), Map.of(),
                            "你是Autiva进化系统的基因突变引擎。根据失败原因修复基因代码。只输出修复后的代码，不要解释。"
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

    public Gene mutate(Gene original, Experience experience) {
        String currentCode = original.code();
        if (currentCode == null || currentCode.isEmpty()) {
            currentCode = original.strategy() != null ? String.join("\n", original.strategy()) : "";
        }

        String prompt = """
                修复以下基因：

                当前代码：
                %s

                失败原因：
                %s

                修复方案：
                %s

                要求：
                - 保持IO接口稳定
                - 修复失败问题
                - 提高鲁棒性
                - 只输出修复后的代码，不要解释
                """.formatted(currentCode, experience.rootCause(), experience.fix());

        try {
            RuntimeContext ctx = new RuntimeContext("evolve-mutate");
            UserMessage userMessage = new UserMessage(prompt);
            AssistantMessage response = getMutatorAgent().runBlock(ctx, userMessage);

            if (response == null || response.getText() == null || response.getText().isEmpty()) {
                log.warn("[Evolve] Agent返回空代码，跳过突变");
                return null;
            }

            String newCode = cleanCode(response.getText());

            Gene mutated = new Gene(
                    original.id(),
                    original.category(),
                    original.signalsMatch(),
                    original.preconditions(),
                    original.strategy(),
                    original.constraints(),
                    original.validation(),
                    1.0,
                    original.summary(),
                    original.antiPatterns(),
                    true,
                    original.runtimeType(),
                    newCode,
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

    private String cleanCode(String code) {
        if (code.contains("```java")) {
            code = code.substring(code.indexOf("```java") + 7);
            code = code.substring(0, code.indexOf("```"));
        } else if (code.contains("```")) {
            code = code.substring(code.indexOf("```") + 3);
            code = code.substring(0, code.lastIndexOf("```"));
        }
        return code.trim();
    }
}
