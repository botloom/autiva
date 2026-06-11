package cn.bitloom.agentic.tool.manage.evolve;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * 查看进化引擎当前配置和状态。
 */
@Slf4j
public class EvolveConfigStatusTool extends AbstractTool<EvolveConfigStatusTool.Input> {

    private static final String DESCRIPTION = "查看进化引擎当前配置和状态";

    private final EvolveConfig evolveConfig;

    private EvolveConfigStatusTool(EvolveConfig evolveConfig) {
        super("evolve_config_status", DESCRIPTION, Input.class);
        Assert.notNull(evolveConfig, "evolveConfig不能为null");
        this.evolveConfig = evolveConfig;
    }

    /**
     * 无参数输入
     */
    public record Input(
            @ToolParam(description = "无参数，传空字符串即可") String _none
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext context) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvolveConfig evolveConfig;

        public Builder evolveConfig(EvolveConfig evolveConfig) {
            this.evolveConfig = evolveConfig;
            return this;
        }

        public EvolveConfigStatusTool build() {
            return new EvolveConfigStatusTool(evolveConfig);
        }
    }
}
