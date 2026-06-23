package cn.bitloom.agentic.evolve.experience;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentKind;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.execution.ExecutionLog;
import cn.bitloom.agentic.evolve.execution.ExecutionRecorder;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExperienceEngine {

    private final ExecutionRecorder executionRecorder;
    private final EvolveConfig config;
    private final ModelFactory modelFactory;
    private final ObjectMapper mapper;
    private volatile Agent evolverAgent;

    public ExperienceEngine(ExecutionRecorder executionRecorder, EvolveConfig config,
                            ModelFactory modelFactory) {
        this.executionRecorder = executionRecorder;
        this.config = config;
        this.modelFactory = modelFactory;
        this.mapper = new ObjectMapper();
    }

    private Agent getEvolverAgent() {
        if (evolverAgent == null) {
            synchronized (this) {
                if (evolverAgent == null) {
                    AgentDefinition definition = new AgentDefinition(
                            "evolver", "进化经验提取器", AgentKind.SUBAGENT,
                            List.of(), List.of(), List.of(), Map.of(),
                            "你是Autiva进化系统的经验提取引擎。分析执行日志，提取结构化的失败模式。只输出JSON，不要解释。"
                    );
                    evolverAgent = Agent.builder()
                            .name("evolver")
                            .definition(definition)
                            .model(modelFactory.model(ModelTypeEnum.DEEPSEEK))
                            .systemPrompt(definition.content())
                            .logging(false)
                            .build();
                }
            }
        }
        return evolverAgent;
    }

    public Experience extract(List<ExecutionLog> logs) {
        List<ExecutionLog> failedLogs = logs.stream()
                .filter(l -> !l.success())
                .toList();

        if (failedLogs.isEmpty()) {
            return null;
        }

        String logsText = failedLogs.stream()
                .map(l -> String.format("[%s] gene=%s intent=%s error=%s",
                        l.id(), l.geneId(), l.intent(), l.error()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                分析以下执行日志，提取结构化的失败模式：

                日志：
                %s

                输出严格JSON格式（不要markdown代码块）：
                {
                  "pattern": "识别的失败模式",
                  "rootCause": "根本原因",
                  "fix": "修复方案",
                  "target": "GENE/ROUTING/MEMORY",
                  "targetId": "目标ID（如geneId）",
                  "confidence": 0.8
                }
                """.formatted(logsText);

        try {
            RuntimeContext ctx = new RuntimeContext("evolve-experience");
            UserMessage userMessage = new UserMessage(prompt);
            AssistantMessage response = getEvolverAgent().runBlock(ctx, userMessage);

            if (response == null || response.getText() == null || response.getText().isEmpty()) {
                log.warn("[Evolve] Agent返回空响应，跳过经验提取");
                return null;
            }

            return parseExperience(response.getText(), failedLogs);
        } catch (Exception e) {
            log.error("[Evolve] Agent提取经验失败", e);
            return null;
        }
    }

    public List<Experience> batchExtract(int recentLogCount) {
        List<ExecutionLog> logs = executionRecorder.readFailedLogs(recentLogCount);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Experience> experiences = new ArrayList<>();
        Map<String, List<ExecutionLog>> byGene = logs.stream()
                .filter(l -> l.geneId() != null)
                .collect(Collectors.groupingBy(ExecutionLog::geneId));

        for (Map.Entry<String, List<ExecutionLog>> entry : byGene.entrySet()) {
            Experience exp = extract(entry.getValue());
            if (exp != null && exp.isActionable()) {
                experiences.add(exp);
            }
        }

        return experiences;
    }

    private Experience parseExperience(String response, List<ExecutionLog> sourceLogs) {
        try {
            String json = response;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                json = json.substring(0, json.indexOf("```"));
            }
            json = json.trim();

            JsonNode node = mapper.readTree(json);

            return new Experience(
                    "exp_" + UUID.randomUUID().toString().substring(0, 8),
                    System.currentTimeMillis(),
                    node.path("pattern").asText(""),
                    node.path("rootCause").asText(""),
                    node.path("fix").asText(""),
                    ExperienceTarget.fromCode(node.path("target").asText("GENE").toLowerCase()),
                    node.path("targetId").asText(""),
                    node.path("confidence").asDouble(0.0),
                    sourceLogs.stream().map(ExecutionLog::id).toList()
            );
        } catch (JsonProcessingException e) {
            log.warn("[Evolve] 解析经验JSON失败: {}", response, e);
            return null;
        }
    }
}
