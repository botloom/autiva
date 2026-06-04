package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.signal.Signal;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.agentic.evolve.solidify.Solidifier;
import cn.bitloom.agentic.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class EvolveCycleTool {

    private final EvolutionEngine evolutionEngine;
    private final SessionManager sessionManager;

    private EvolveCycleTool(EvolutionEngine evolutionEngine, SessionManager sessionManager) {
        Assert.notNull(evolutionEngine, "evolutionEngine不能为null");
        Assert.notNull(sessionManager, "sessionManager不能为null");
        this.evolutionEngine = evolutionEngine;
        this.sessionManager = sessionManager;
    }

    @Tool(name = "evolve_run_cycle", description = "执行一次完整的进化周期：收集信号→分析历史→选择基因→组装提示词→返回进化上下文")
    public ToolResult runCycle(
            @ToolParam(description = "当前会话ID，用于获取对话历史") String sessionId,
            @ToolParam(description = "进化意图描述（如：修复错误、优化性能、探索新能力）") String intent
    ) {
        log.info("[ToolCall] evolve_run_cycle - sessionId={}, intent={}", sessionId, intent);

        List<String> conversationTexts = extractConversationTexts(sessionId);

        EvolutionEngine.EvolutionCycleResult result = evolutionEngine.runCycle(conversationTexts);

        if (!result.success()) {
            return ToolResult.warning("进化周期未产生结果: " + result.reason());
        }

        Gene gene = result.gene();
        List<Signal> signals = result.signals();

        EvolutionEvent.Outcome outcome = new EvolutionEvent.Outcome("pending", result.score(), 0);
        EvolutionEvent event = evolutionEngine.createEvent(signals, gene, intent, result.prompt(), outcome);

        Solidifier.SolidifyResult solidifyResult = evolutionEngine.solidify(event);

        String rawOutput = String.format("进化周期完成\n\n选中基因: %s (%s)\n策略: %s\n理由: %s\n置信度: %.2f\n固化结果: %s\n\n%s",
                gene.id(), gene.summary(),
                result.preset(),
                result.reason(),
                result.score(),
                solidifyResult.message(),
                result.prompt());
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("进化周期完成，选中基因: " + gene.id())
                .data(java.util.Map.of("geneId", gene.id(), "score", result.score()))
                .rawOutput(rawOutput)
                .build();
    }

    private List<String> extractConversationTexts(String sessionId) {
        try {
            var session = sessionManager.getById(sessionId);
            if (session == null) {
                return List.of();
            }
            List<Message> messages = session.getMessages();
            if (messages == null) {
                return List.of();
            }
            return messages.stream()
                    .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage
                            || m instanceof org.springframework.ai.chat.messages.AssistantMessage)
                    .map(m -> {
                        if (m instanceof org.springframework.ai.chat.messages.UserMessage um) {
                            return um.getText();
                        } else if (m instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                            return am.getText();
                        }
                        return "";
                    })
                    .filter(t -> !t.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("[Evolve] 提取对话文本失败: {}", e.getMessage());
            return List.of();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvolutionEngine evolutionEngine;
        private SessionManager sessionManager;

        public Builder evolutionEngine(EvolutionEngine evolutionEngine) {
            this.evolutionEngine = evolutionEngine;
            return this;
        }

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public EvolveCycleTool build() {
            return new EvolveCycleTool(evolutionEngine, sessionManager);
        }
    }
}
