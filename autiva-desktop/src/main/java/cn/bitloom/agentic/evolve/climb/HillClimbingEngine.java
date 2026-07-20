package cn.bitloom.agentic.evolve.climb;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.evolve.mutation.GeneMutator;
import cn.bitloom.agentic.evolve.safety.EvolutionSafety;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.trace.Trace;
import cn.bitloom.agentic.trace.TraceRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * L4 爬山分析引擎。
 *
 * <p>分析批量 Trace，发现高频缺陷，输出优化建议并触发 {@link GeneMutator} 突变 Gene。</p>
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>加载最近 N 条 Trace（按 agentId 过滤）</li>
 *   <li>加载该 Agent 的所有 Gene 配置</li>
 *   <li>LLM 分析：发现高频缺陷 + 定位需优化的 Gene</li>
 *   <li>对每个高置信度建议执行 GeneMutator.mutate</li>
 *   <li>安全检查通过则 upsert Gene，记录 EvolutionEvent</li>
 * </ol>
 *
 * <p>触发方式：</p>
 * <ul>
 *   <li>手动触发：UI 上"分析优化"按钮</li>
 *   <li>定时触发：每天首次启动时分析昨天的 Trace</li>
 *   <li>阈值触发：累积 N 次失败校验后自动触发</li>
 * </ul>
 */
@Slf4j
@Component
public class HillClimbingEngine {

    private static final String ANALYZER_SYSTEM_PROMPT = """
            你是 Autiva 的 L4 自优化分析引擎。你的职责是分析 Agent 的执行 Trace，
            发现高频缺陷模式，并定位需要优化的配置单元（Gene）。

            # 分析原则
            1. 基于数据：每个问题都要引用具体的 Trace 证据（频次/模式）
            2. 可执行：建议必须具体到 Gene 层面，指出优化方向
            3. 优先级：优先处理高频问题（出现 ≥3 次的模式）
            4. 置信度校准：高频且明确的 =0.9+，间接推断 =0.6-0.8，主观判断 =0.3-0.5

            # 输出格式
            严格输出 JSON 数组，每个元素是一个优化建议：
            ```json
            [
              {
                "geneId": "prompt_xxx_xxx",
                "geneType": "PROMPT",
                "targetId": "heimy",
                "issue": "Trace 中 heimy 遇到错误时 7/10 次盲目重试而非分析根因",
                "suggestion": "在 prompt 中增加'先分析根因再行动'的强约束",
                "confidence": 0.85
              }
            ]
            ```

            如果没有可优化项，输出空数组 `[]`。不要输出 JSON 之外的任何内容。
            """;

    private final ModelFactory modelFactory;
    private final GeneStore geneStore;
    private final GeneMutator geneMutator;
    private final EvolutionSafety safety;
    private final TraceRecorder traceRecorder;
    private final EvolveConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<Agent> analyzerAgentRef = new AtomicReference<>();

    public HillClimbingEngine(ModelFactory modelFactory,
                              GeneStore geneStore,
                              GeneMutator geneMutator,
                              EvolutionSafety safety,
                              TraceRecorder traceRecorder,
                              EvolveConfig config) {
        this.modelFactory = modelFactory;
        this.geneStore = geneStore;
        this.geneMutator = geneMutator;
        this.safety = safety;
        this.traceRecorder = traceRecorder;
        this.config = config;
    }

    /**
     * 执行爬山分析。
     *
     * @param agentId 目标 Agent ID
     * @return 爬山结果
     */
    public ClimbingResult climb(String agentId) {
        log.info("[HillClimbing] 开始分析 agentId={}", agentId);

        // 1. 加载最近 N 条 Trace
        List<Trace> traces = traceRecorder.loadRecent(agentId, 50);
        if (traces.isEmpty()) {
            log.info("[HillClimbing] 无可用 Trace，跳过 agentId={}", agentId);
            return new ClimbingResult(agentId, 0, "无可用 Trace",
                    List.of(), List.of(), 0, 0.0);
        }

        // 2. 加载该 Agent 的所有 Gene 配置
        List<Gene> genes = geneStore.findByTarget(agentId);
        if (genes.isEmpty()) {
            // 也尝试加载该 Agent 关联的所有 Gene（按 type=PROMPT/TOOL_DESC/RUBRIC/SKILL_CONFIG）
            genes = geneStore.findByType(GeneType.PROMPT).stream()
                    .filter(g -> agentId.equals(g.targetId()))
                    .collect(Collectors.toList());
        }

        // 计算优化前的通过率
        TraceRecorder.VerificationStats stats = traceRecorder.stats(agentId, 50);

        // 3. LLM 分析
        AnalysisResult analysis = analyzeTraces(traces, genes, agentId);
        log.info("[HillClimbing] 分析完成 agentId={} suggestions={}",
                agentId, analysis.suggestions().size());

        // 4. 对每个高置信度建议执行突变
        List<EvolutionEvent> applied = new ArrayList<>();
        int skipped = 0;
        for (OptimizationSuggestion suggestion : analysis.suggestions()) {
            if (!suggestion.isHighConfidence(config.getExperienceConfidenceThreshold())) {
                log.debug("[HillClimbing] 跳过低置信度建议: {} (conf={})",
                        suggestion.geneId(), suggestion.confidence());
                skipped++;
                continue;
            }

            Gene targetGene = geneStore.findById(suggestion.geneId());
            if (targetGene == null) {
                log.warn("[HillClimbing] Gene 不存在: {}", suggestion.geneId());
                skipped++;
                continue;
            }

            // geneMutator.mutate() 内部已包含 safety.check()，无需重复检查
            Gene mutated = geneMutator.mutate(targetGene, suggestion.issue(), suggestion.suggestion());
            if (mutated == null) {
                log.warn("[HillClimbing] 突变失败: {}", suggestion.geneId());
                skipped++;
                continue;
            }

            EvolutionEvent event = createMutationEvent(targetGene, mutated, suggestion);
            geneStore.appendEvent(event);
            applied.add(event);
            log.info("[HillClimbing] 已应用优化: {} v{} -> v{}",
                    mutated.id(), targetGene.version(), mutated.version());
        }

        return new ClimbingResult(
                agentId,
                traces.size(),
                analysis.rawText(),
                analysis.suggestions(),
                applied,
                skipped,
                stats.passRate()
        );
    }

    private AnalysisResult analyzeTraces(List<Trace> traces, List<Gene> genes, String agentId) {
        String prompt = buildAnalysisPrompt(traces, genes, agentId);
        try {
            Agent analyzer = getAnalyzerAgent();
            RuntimeContext ctx = new RuntimeContext("hill-climbing-" + agentId);
            UserMessage userMessage = new UserMessage(prompt);
            AssistantMessage response = analyzer.runBlock(ctx, userMessage);

            if (response == null || response.getText() == null || response.getText().isEmpty()) {
                log.warn("[HillClimbing] 分析 Agent 返回空内容");
                return new AnalysisResult("分析返回空", List.of());
            }

            String rawText = response.getText().trim();
            List<OptimizationSuggestion> suggestions = parseSuggestions(rawText);
            return new AnalysisResult(rawText, suggestions);
        } catch (Exception e) {
            log.error("[HillClimbing] 分析失败: {}", e.getMessage(), e);
            return new AnalysisResult("分析异常: " + e.getMessage(), List.of());
        }
    }

    private String buildAnalysisPrompt(List<Trace> traces, List<Gene> genes, String agentId) {
        String tracesText = formatTraces(traces);
        String genesText = formatGenes(genes);

        long verifiedCount = traces.stream().filter(Trace::verified).count();
        long failedCount = traces.size() - verifiedCount;

        return """
                请分析以下 Agent 的执行 Trace，发现高频缺陷模式，并定位需要优化的 Gene。

                ## Agent 信息
                - Agent ID: %s
                - Trace 数量: %d
                - 校验通过: %d 次
                - 校验失败: %d 次
                - 通过率: %.1f%%

                ## 执行 Trace（按时间倒序，最近 %d 条）
                %s

                ## 当前配置单元（Gene）
                %s

                请输出 JSON 数组格式的优化建议。每个建议必须指向已存在的 Gene ID。
                """.formatted(
                agentId,
                traces.size(),
                verifiedCount,
                failedCount,
                100.0 * verifiedCount / Math.max(traces.size(), 1),
                Math.min(traces.size(), 50),
                tracesText,
                genesText.isEmpty() ? "（无已注册的 Gene）" : genesText
        );
    }

    private String formatTraces(List<Trace> traces) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(traces.size(), 50);
        for (int i = 0; i < limit; i++) {
            Trace t = traces.get(i);
            sb.append(String.format("- Trace#%d [%s] agent=%s verified=%s attempts=%d toolCalls=%d",
                    i + 1,
                    t.traceId(),
                    t.agentId(),
                    t.verified(),
                    t.attemptCount(),
                    t.toolCalls() != null ? t.toolCalls().size() : 0));
            if (t.feedbacks() != null && !t.feedbacks().isEmpty()) {
                sb.append(" feedbacks=").append(t.feedbacks().size());
            }
            if (t.finalOutput() != null && !t.finalOutput().isEmpty()) {
                sb.append("\n  output: ").append(truncate(t.finalOutput(), 300));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatGenes(List<Gene> genes) {
        if (genes == null || genes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Gene g : genes) {
            sb.append(String.format("- geneId=%s type=%s target=%s v%d boost=%.2f enabled=%s%n",
                    g.id(), g.type(), g.targetId(),
                    g.version(), g.epigeneticBoost(), g.enabled()));
            if (g.content() != null) {
                sb.append("  content: ").append(truncate(g.content(), 200)).append("\n");
            }
        }
        return sb.toString();
    }

    private List<OptimizationSuggestion> parseSuggestions(String response) {
        String json = extractJsonArray(response);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<OptimizationSuggestion> result = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    String geneId = textOr(node, "geneId", null);
                    if (geneId == null) continue;
                    GeneType geneType = parseGeneType(textOr(node, "geneType", "PROMPT"));
                    String targetId = textOr(node, "targetId", "");
                    String issue = textOr(node, "issue", "");
                    String suggestion = textOr(node, "suggestion", "");
                    double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.5;
                    result.add(new OptimizationSuggestion(geneId, geneType, targetId, issue, suggestion, confidence));
                } catch (Exception e) {
                    log.warn("[HillClimbing] 解析单条建议失败: {}", e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[HillClimbing] 解析建议数组失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) return "";
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "";
    }

    private String textOr(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private GeneType parseGeneType(String text) {
        try {
            return GeneType.valueOf(text.toUpperCase());
        } catch (Exception e) {
            return GeneType.PROMPT;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...";
    }

    private EvolutionEvent createMutationEvent(Gene original, Gene mutated, OptimizationSuggestion suggestion) {
        return new EvolutionEvent(
                "evt_" + UUID.randomUUID().toString().substring(0, 8),
                System.currentTimeMillis(),
                List.of(),
                mutated.id(),
                "hill-climbing:" + suggestion.issue(),
                suggestion.suggestion(),
                new EvolutionEvent.Outcome("success", suggestion.confidence(), 1),
                Map.of(
                        "fromVersion", original.version(),
                        "toVersion", mutated.version(),
                        "confidence", suggestion.confidence()
                )
        );
    }

    private Agent getAnalyzerAgent() {
        Agent existing = analyzerAgentRef.get();
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (analyzerAgentRef.get() == null) {
                AgentDefinition definition = new AgentDefinition(
                        "hill-climber", "L4 爬山分析引擎", AgentKind.SUBAGENT,
                        List.of(), List.of(), List.of(), Map.of(),
                        ANALYZER_SYSTEM_PROMPT,
                        AgentDefinition.VerificationConfig.disabled()
                );
                Agent agent = Agent.builder()
                        .name("hill-climber")
                        .definition(definition)
                        .model(modelFactory.model(ModelTypeEnum.DEEPSEEK))
                        .systemPrompt(ANALYZER_SYSTEM_PROMPT)
                        .logging(false)
                        .build();
                analyzerAgentRef.set(agent);
            }
            return analyzerAgentRef.get();
        }
    }

    private record AnalysisResult(String rawText, List<OptimizationSuggestion> suggestions) {}
}
