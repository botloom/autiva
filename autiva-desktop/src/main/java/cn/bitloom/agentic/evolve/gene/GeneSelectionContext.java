package cn.bitloom.agentic.evolve.gene;

import java.util.List;

/**
 * 基因选择上下文，描述当前任务的特征，用于基因匹配打分。
 *
 * @param taskType      任务类型（如 coding/refactoring/chat）
 * @param toolsUsed     本次任务可能使用的工具列表
 * @param errorPatterns 错误模式列表（如 file-already-exists）
 * @param agentName     执行任务的 Agent 名称
 */
public record GeneSelectionContext(String taskType, List<String> toolsUsed, List<String> errorPatterns, String agentName) {

    /**
     * 默认选择上下文，用于缺少运行时信息时的回退。
     */
    public static GeneSelectionContext defaults() {
        return new GeneSelectionContext("coding", List.of(), List.of(), "default");
    }
}
