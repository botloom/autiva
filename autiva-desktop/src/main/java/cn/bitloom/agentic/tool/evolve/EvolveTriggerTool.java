package cn.bitloom.agentic.tool.evolve;

import cn.bitloom.agentic.evolve.EvolverAgent;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手动触发进化周期的工具。
 * <p>
 * 调用 {@link EvolverAgent#runEvolutionCycle(String)} 执行完整进化闭环：
 * Cycle → Route → Execute → Solidify → Record。
 * <p>
 * 进化周期可能耗时较长（涉及轨迹验证、LLM 归纳、金丝雀检查），
 * 建议在非关键路径或低峰期触发。
 */
@Slf4j
public class EvolveTriggerTool extends AbstractTool<EvolveTriggerTool.Input> {

    private static final String DESCRIPTION = """
            手动触发一次完整进化周期。
            需要提供 taskFamily（任务族标识，如 "coding-refactor"）。
            进化周期会执行：轨迹采集 → 验证 → 经验提取 → 候选基因生成 → 安全检查 → 固化 → 记录。
            返回进化结果摘要（分析轨迹数、提取经验数、固化基因数、回滚基因数）。
            """;

    private final EvolverAgent evolverAgent;

    private EvolveTriggerTool(EvolverAgent evolverAgent) {
        super("EvolveTrigger", DESCRIPTION, Input.class);
        Assert.notNull(evolverAgent, "evolverAgent 不能为 null");
        this.evolverAgent = evolverAgent;
    }

    public record Input(
            @ToolParam(description = "任务族标识，如 \"coding-refactor\"、\"coding-test\" 等") String taskFamily
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] EvolveTrigger - 触发进化周期: taskFamily={}", input.taskFamily());

        if (input.taskFamily() == null || input.taskFamily().isBlank()) {
            return ToolResult.error("taskFamily 不能为空");
        }

        String taskFamily = input.taskFamily().trim();
        long startTime = System.currentTimeMillis();

        EvolverAgent.EvolutionResult result;
        try {
            result = evolverAgent.runEvolutionCycle(taskFamily);
        } catch (Exception e) {
            log.error("[ToolCall] EvolveTrigger - 进化周期执行失败: taskFamily={}", taskFamily, e);
            return ToolResult.error("进化周期执行失败: " + e.getMessage(),
                    "进化周期执行失败\ntaskFamily: " + taskFamily + "\n错误: " + e.getMessage() + "\n");
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[ToolCall] EvolveTrigger - 进化周期完成: taskFamily={}, 耗时={}ms, 固化={}, 回滚={}",
                taskFamily, durationMs, result.genesSolidified(), result.genesRolledBack());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskFamily", result.taskFamily());
        data.put("trajectoriesAnalyzed", result.trajectoriesAnalyzed());
        data.put("experiencesExtracted", result.experiencesExtracted());
        data.put("genesSolidified", result.genesSolidified());
        data.put("genesRolledBack", result.genesRolledBack());
        data.put("durationMs", durationMs);

        StringBuilder sb = new StringBuilder();
        sb.append("进化周期完成\n\n");
        sb.append("- 任务族: ").append(result.taskFamily()).append("\n");
        sb.append("- 分析轨迹数: ").append(result.trajectoriesAnalyzed()).append("\n");
        sb.append("- 提取经验数: ").append(result.experiencesExtracted()).append("\n");
        sb.append("- 固化基因数: ").append(result.genesSolidified()).append("\n");
        sb.append("- 回滚基因数: ").append(result.genesRolledBack()).append("\n");
        sb.append("- 耗时: ").append(durationMs).append(" ms\n\n");

        sb.append("## 过程消息\n");
        for (String message : result.messages()) {
            sb.append("- ").append(message).append("\n");
        }

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("进化周期完成: 固化=" + result.genesSolidified()
                        + ", 回滚=" + result.genesRolledBack()
                        + ", 耗时=" + durationMs + "ms")
                .data(data)
                .rawOutput(sb.toString())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private EvolverAgent evolverAgent;

        private Builder() {}

        public Builder evolverAgent(EvolverAgent evolverAgent) {
            this.evolverAgent = evolverAgent;
            return this;
        }

        public EvolveTriggerTool build() {
            return new EvolveTriggerTool(this.evolverAgent);
        }
    }
}
