package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

@Slf4j
public class EvolveConfigTool {

    private final EvolveConfig evolveConfig;

    private EvolveConfigTool(EvolveConfig evolveConfig) {
        Assert.notNull(evolveConfig, "evolveConfig不能为null");
        this.evolveConfig = evolveConfig;
    }

    @Tool(name = "evolve_config_status", description = "查看进化引擎当前配置和状态")
    public ToolResult status() {
        log.info("[ToolCall] evolve_config_status");
        String rawOutput = String.format("""
                进化引擎状态:
                - 策略预设: %s
                - 进化目录: %s
                - 信号去重窗口: %d
                - 修复循环阈值: %d
                - 饱和阈值: %d
                - 最大提示词长度: %d
                """,
                evolveConfig.getStrategyPreset(),
                evolveConfig.getEvolveDir(),
                evolveConfig.getSignalDedupWindow(),
                evolveConfig.getRepairLoopThreshold(),
                evolveConfig.getSaturationThreshold(),
                evolveConfig.getMaxPromptLength());
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("进化引擎状态")
                .rawOutput(rawOutput)
                .build();
    }

    @Tool(name = "evolve_config_set_strategy", description = "设置进化引擎的策略预设")
    public ToolResult setStrategy(
            @ToolParam(description = "策略名称: BALANCED/INNOVATE/HARDEN/REPAIR_ONLY/EARLY_STABILIZE/STEADY_STATE/AUTO") String strategy
    ) {
        log.info("[ToolCall] evolve_config_set_strategy - strategy={}", strategy);
        try {
            StrategyPreset preset = StrategyPreset.valueOf(strategy.toUpperCase());
            evolveConfig.setStrategyPreset(preset);
            return ToolResult.success("策略已设置为: " + preset.name());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("无效的策略名称: " + strategy + "。可用: BALANCED, INNOVATE, HARDEN, REPAIR_ONLY, EARLY_STABILIZE, STEADY_STATE, AUTO");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvolveConfig evolveConfig;

        public Builder evolveConfig(EvolveConfig evolveConfig) {
            this.evolveConfig = evolveConfig;
            return this;
        }

        public EvolveConfigTool build() {
            return new EvolveConfigTool(evolveConfig);
        }
    }
}
