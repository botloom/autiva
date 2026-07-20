package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import cn.bitloom.agentic.evolve.gene.GeneType;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.agentic.verify.Feedback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 裁判（Maker/Verifier 分离）。
 * <p>
 * 用独立的 ChatClient 评判 Maker Agent 的产出，不复用主 Agent 上下文。
 * 从 GeneStore 加载 RUBRIC Gene 作为评判标准。
 * <p>
 * 设计参考 Loop Engineering 最佳实践：规避模型自审盲区。
 */
@Slf4j
@Component
public class LlmGrader {

    private static final String VERIFIER_SYSTEM_PROMPT = """
            你是 Autiva 的质量裁判 Agent。你的职责是按 Rubric 标准评判另一个 Agent 的产出质量。

            # 评判原则
            1. 客观：只根据 Rubric 标准判断，不偏袒也不挑剔
            2. 具体指出问题：每条 issue 都要包含具体位置和修复方向
            3. 评分校准：完美=100，可接受=70-89，需改进=50-69，不可接受=<50
            4. 输出严格 JSON 格式，不要任何额外说明

            # 输出格式
            ```json
            {
              "passed": true 或 false,
              "score": 0-100,
              "issues": ["具体问题描述1", "具体问题描述2"]
            }
            ```

            passed 的判定标准：score ≥ 70 且 issues 中没有 Blocker 级别问题。
            """;

    private final ModelFactory modelFactory;
    private final GeneStore geneStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile ChatClient verifierClient;

    public LlmGrader(ModelFactory modelFactory, GeneStore geneStore) {
        this.modelFactory = modelFactory;
        this.geneStore = geneStore;
    }

    /**
     * 评判产出。
     *
     * @param output      Maker Agent 的最终产出
     * @param ctx         运行时上下文（可获取 userMessage/agentId）
     * @return 校验反馈列表（可能包含多个 issue，全部通过时返回单条 pass）
     */
    public List<Feedback> grade(AssistantMessage output, RuntimeContext ctx) {
        if (output == null || output.getText() == null || output.getText().isEmpty()) {
            return List.of(Feedback.fail("产出为空，无法评判"));
        }

        try {
            String agentId = ctx.getSession() != null ? ctx.getSession().getAgentId() : "unknown";
            List<Gene> rubrics = geneStore.findByTypeAndTarget(GeneType.RUBRIC, agentId);

            String rubricText = rubrics.stream()
                    .filter(Gene::enabled)
                    .map(g -> "- " + g.name() + ": " + g.content())
                    .collect(Collectors.joining("\n"));

            if (rubricText.isEmpty()) {
                // 没有 RUBRIC Gene，跳过 LLM 评判
                log.debug("[LlmGrader] 未找到 RUBRIC Gene (agentId={})，跳过 LLM 评判", agentId);
                return List.of(Feedback.pass());
            }

            String userMessage = ctx.getSession() != null && ctx.getSession().getMessages() != null
                    && !ctx.getSession().getMessages().isEmpty()
                    ? getLastUserMessage(ctx)
                    : "(用户消息不可用)";

            String prompt = buildPrompt(rubricText, output.getText(), userMessage);

            ChatClient client = getVerifierClient();
            String response = client.prompt()
                    .system(VERIFIER_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            return parseFeedback(response);
        } catch (Exception e) {
            log.error("[LlmGrader] 评判失败: {}", e.getMessage(), e);
            return List.of(Feedback.fail("LLM 评判异常: " + e.getMessage(), Feedback.Severity.WARN));
        }
    }

    private String buildPrompt(String rubricText, String output, String userMessage) {
        return """
                请按以下 Rubric 标准评判 Agent 的产出。

                ## 评判标准（Rubric）
                %s

                ## 用户原始请求
                %s

                ## Agent 产出
                %s

                请输出 JSON：
                """.formatted(rubricText, truncate(userMessage, 2000), truncate(output, 6000));
    }

    private String getLastUserMessage(RuntimeContext ctx) {
        if (ctx.getSession() == null || ctx.getSession().getMessages() == null) {
            return "(无会话消息)";
        }
        // 反向遍历找到最后一条 UserMessage
        var messages = ctx.getSession().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            var msg = messages.get(i);
            if (msg instanceof org.springframework.ai.chat.messages.UserMessage um) {
                return um.getText() != null ? um.getText() : "(空用户消息)";
            }
        }
        return "(未找到用户消息)";
    }

    private List<Feedback> parseFeedback(String response) {
        if (response == null || response.isBlank()) {
            return List.of(Feedback.fail("LLM 裁判返回空内容", Feedback.Severity.WARN));
        }

        try {
            // 提取 JSON 部分（兼容 markdown code block 包裹）
            String json = extractJson(response);
            JsonNode root = mapper.readTree(json);

            boolean passed = root.has("passed") && root.get("passed").asBoolean();
            int score = root.has("score") ? root.get("score").asInt() : (passed ? 80 : 40);
            List<String> issues = new ArrayList<>();
            if (root.has("issues") && root.get("issues").isArray()) {
                for (JsonNode issue : root.get("issues")) {
                    issues.add(issue.asText());
                }
            }

            if (passed) {
                return List.of(Feedback.pass("LLM 评判通过，score=" + score));
            } else {
                Feedback.Severity severity = score < 50 ? Feedback.Severity.ERROR : Feedback.Severity.WARN;
                String message = "LLM 评判未通过 (score=" + score + "): "
                        + (issues.isEmpty() ? "未给出具体原因" : String.join("; ", issues));
                return List.of(Feedback.fail(message, score / 100.0, severity));
            }
        } catch (Exception e) {
            log.warn("[LlmGrader] 解析 LLM 输出失败: {}", e.getMessage());
            return List.of(Feedback.fail("LLM 输出解析失败: " + e.getMessage(), Feedback.Severity.WARN));
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...(truncated)";
    }

    private ChatClient getVerifierClient() {
        if (verifierClient == null) {
            synchronized (this) {
                if (verifierClient == null) {
                    verifierClient = ChatClient.builder(modelFactory.model(ModelTypeEnum.DEEPSEEK))
                            .build();
                }
            }
        }
        return verifierClient;
    }
}
