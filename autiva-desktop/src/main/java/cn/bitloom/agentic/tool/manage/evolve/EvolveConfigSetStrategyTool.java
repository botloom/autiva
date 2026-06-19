package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 设置进化引擎的策略预设。
 */
@Slf4j
public class EvolveConfigSetStrategyTool extends AbstractTool<EvolveConfigSetStrategyTool.Input> {

    private static final String DESCRIPTION = "设置进化引擎的策略预设";

    private final EvolveConfig evolveConfig;

    private EvolveConfigSetStrategyTool(EvolveConfig evolveConfig) {
        super("evolve_config_set_strategy", DESCRIPTION, Input.class);
        Assert.notNull(evolveConfig, "evolveConfig不能为null");
        this.evolveConfig = evolveConfig;
    }

    public record Input(
            @ToolParam(description = "策略名称: BALANCED/INNOVATE/HARDEN/REPAIR_ONLY/EARLY_STABILIZE/STEADY_STATE/AUTO") String strategy
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String strategy = input.strategy();
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

        public EvolveConfigSetStrategyTool build() {
            return new EvolveConfigSetStrategyTool(evolveConfig);
        }
    }
}
