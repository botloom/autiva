package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.EvolutionEngine;
import cn.bitloom.agentic.evolve.climb.ClimbingResult;
import cn.bitloom.agentic.evolve.climb.OptimizationSuggestion;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主动触发 L4 爬山分析。
 * <p>
 * 智能体通过此工具发起一次 L4 自优化：加载最近 Trace → LLM 分析高频缺陷 →
 * 高置信度建议触发 Gene 突变 → 安全检查 → 落盘。
 */
@Slf4j
public class ClimbAnalyzeTool extends AbstractTool<ClimbAnalyzeTool.Input> {

    private static final String DESCRIPTION = """
            主动触发 L4 爬山自优化分析。
            基于最近对话 Trace 发现高频缺陷，自动优化 Agent Prompt / 工具描述 / Grader 校验规则 / 技能配置。
            高置信度建议会自动应用并写入版本库；低置信度建议仅记录不应用。
            参数 agent_id 可选，缺省时使用当前会话所属智能体。""";

    private final EvolutionEngine evolutionEngine;

    public ClimbAnalyzeTool(EvolutionEngine evolutionEngine) {
        super("climb_analyze", DESCRIPTION, Input.class);
        this.evolutionEngine = evolutionEngine;
    }

    public record Input(
            @ToolParam(description = "目标智能体ID，可选。缺省时使用当前会话的智能体",
                    required = false) String agent_id
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String agentId = resolveAgentId(input, context);
        log.info("[ToolCall] climb_analyze - agentId={}", agentId);
        try {
            ClimbingResult result = evolutionEngine.climb(agentId);
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .message(buildSummary(result))
                    .data(buildData(result))
                    .rawOutput(buildRawOutput(result))
                    .build();
        } catch (Exception e) {
            log.warn("[ToolCall] climb_analyze 失败 agentId={}", agentId, e);
            return ToolResult.error("L4 分析失败: " + e.getMessage());
        }
    }

    private String resolveAgentId(Input input, ToolContext context) {
        if (input.agent_id() != null && !input.agent_id().isBlank()) {
            return input.agent_id();
        }
        if (context != null) {
            Object sessionId = context.getContext().get("sessionId");
            if (sessionId instanceof String s && !s.isEmpty()) {
                return s.split("-")[0];
            }
        }
        return "default";
    }

    private String buildSummary(ClimbingResult r) {
        return String.format("分析 %d 条 Trace，发现 %d 条建议，应用 %d 条，跳过 %d 条",
                r.traceCount(), r.suggestions() != null ? r.suggestions().size() : 0,
                r.appliedCount(), r.skippedCount());
    }

    private Map<String, Object> buildData(ClimbingResult r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent_id", r.agentId());
        data.put("trace_count", r.traceCount());
        data.put("pass_rate_before", r.passRateBefore());
        data.put("suggestion_count", r.suggestions() != null ? r.suggestions().size() : 0);
        data.put("applied_count", r.appliedCount());
        data.put("skipped_count", r.skippedCount());
        return data;
    }

    private String buildRawOutput(ClimbingResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# L4 爬山分析报告\n\n");
        sb.append("- Agent: ").append(r.agentId()).append("\n");
        sb.append("- 分析 Trace 数: ").append(r.traceCount()).append("\n");
        sb.append("- 优化前通过率: ").append(String.format("%.1f%%", r.passRateBefore() * 100)).append("\n");
        sb.append("- 应用: ").append(r.appliedCount()).append(" 条\n");
        sb.append("- 跳过: ").append(r.skippedCount()).append(" 条\n\n");

        List<OptimizationSuggestion> suggestions = r.suggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            sb.append("暂无优化建议。\n");
        } else {
            sb.append("## 优化建议\n\n");
            for (OptimizationSuggestion s : suggestions) {
                sb.append("- [").append(String.format("%.2f", s.confidence())).append("] ");
                sb.append(s.geneId()).append(" (").append(s.geneType()).append(" / ").append(s.targetId()).append(")\n");
                sb.append("  问题: ").append(s.issue()).append("\n");
                sb.append("  建议: ").append(s.suggestion()).append("\n");
            }
        }

        if (r.analysisText() != null && !r.analysisText().isBlank()) {
            sb.append("\n## LLM 原始分析\n\n").append(r.analysisText()).append("\n");
        }
        return sb.toString();
    }
}
