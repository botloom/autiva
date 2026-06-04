package cn.bitloom.agentic.evolve.prompt;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.signal.Signal;
import cn.bitloom.agentic.evolve.strategy.StrategyPreset;

import java.util.List;
import java.util.stream.Collectors;

public class EvolvePromptAssembler {

    private final EvolveConfig config;

    public EvolvePromptAssembler(EvolveConfig config) {
        this.config = config;
    }

    public String assemble(List<Signal> signals, Gene gene, StrategyPreset preset, String reason) {
        StringBuilder sb = new StringBuilder();

        sb.append("# GEP 进化上下文\n\n");

        sb.append("## 检测到的信号\n");
        for (Signal signal : signals) {
            sb.append("- [").append(signal.type().code()).append("] ").append(signal.content()).append("\n");
        }
        sb.append("\n");

        sb.append("## 推荐的基因\n");
        sb.append("- ID: ").append(gene.id()).append("\n");
        sb.append("- 类别: ").append(gene.category().code()).append("\n");
        sb.append("- 摘要: ").append(gene.summary()).append("\n");
        sb.append("- 选择理由: ").append(reason).append("\n");
        sb.append("\n");

        sb.append("## 策略步骤\n");
        for (int i = 0; i < gene.strategy().size(); i++) {
            sb.append(i + 1).append(". ").append(gene.strategy().get(i)).append("\n");
        }
        sb.append("\n");

        if (!gene.constraints().isEmpty()) {
            sb.append("## 约束条件\n");
            gene.constraints().forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(v).append("\n")
            );
            sb.append("\n");
        }

        if (!gene.antiPatterns().isEmpty()) {
            sb.append("## 反模式警告\n");
            for (String ap : gene.antiPatterns()) {
                sb.append("- ").append(ap).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 策略约束\n");
        sb.append("- 当前策略: ").append(preset.name()).append("\n");
        sb.append("- 基因类别: ").append(gene.category().code()).append("\n");
        sb.append("\n");

        sb.append("## 进化指引\n");
        sb.append("基于以上信号和基因，请遵循策略步骤进行演化。");
        sb.append("如果遇到约束条件中列出的情况，请停止并报告。");
        sb.append("完成演化后，请描述你做了什么以及结果。\n");

        String result = sb.toString();
        if (result.length() > config.getMaxPromptLength()) {
            result = result.substring(0, config.getMaxPromptLength()) + "\n...[已截断]";
        }

        return result;
    }

    public String assembleGeneDetail(Gene gene) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 基因: ").append(gene.id()).append("\n\n");
        sb.append("- 类别: ").append(gene.category().code()).append("\n");
        sb.append("- 摘要: ").append(gene.summary()).append("\n");
        sb.append("- 表观遗传值: ").append(String.format("%.2f", gene.epigeneticBoost())).append("\n");
        sb.append("- 状态: ").append(gene.enabled() ? "启用" : "禁用").append("\n");
        sb.append("- 信号匹配: ").append(String.join(", ", gene.signalsMatch())).append("\n");

        sb.append("\n### 策略步骤\n");
        for (int i = 0; i < gene.strategy().size(); i++) {
            sb.append(i + 1).append(". ").append(gene.strategy().get(i)).append("\n");
        }

        if (!gene.constraints().isEmpty()) {
            sb.append("\n### 约束条件\n");
            gene.constraints().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        if (!gene.antiPatterns().isEmpty()) {
            sb.append("\n### 反模式\n");
            for (String ap : gene.antiPatterns()) {
                sb.append("- ").append(ap).append("\n");
            }
        }

        if (!gene.validation().isEmpty()) {
            sb.append("\n### 验证检查\n");
            for (String v : gene.validation()) {
                sb.append("- ").append(v).append("\n");
            }
        }

        return sb.toString();
    }

    public String assembleGeneSummary(List<Gene> genes) {
        return genes.stream()
                .map(g -> String.format("- [%s] %s (%s, boost=%.2f, %s) - %s",
                        g.category().code(), g.id(),
                        g.enabled() ? "启用" : "禁用",
                        g.epigeneticBoost(),
                        String.join(",", g.signalsMatch()),
                        g.summary()))
                .collect(Collectors.joining("\n"));
    }
}
