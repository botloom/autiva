package cn.bitloom.agentic.evolve.safety;

import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.solidify.CanaryCheck;

import java.util.List;

/**
 * 持续进化的安全边界 — chapter8。
 * <p>
 * 在基因固化前对内容安全性、与现有基因的冲突、综合可固化性进行校验，
 * 并提供自动禁用与自动回滚的判定阈值，防止进化系统产生危险或退化基因。
 */
public class EvolutionSafety {

    /** 基因内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 10000;

    /** 表观遗传增强因子合法范围 */
    private static final double MIN_BOOST = 0.1;
    private static final double MAX_BOOST = 10.0;

    /** 安全固化所需的最小增强因子 */
    private static final double SAFE_BOOST_THRESHOLD = 0.5;

    /** 触发自动回滚的最小失败次数 */
    private static final int ROLLBACK_MIN_FAILURES = 3;

    /** 危险指令模式（小写匹配） */
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "rm -rf",
            "sudo ",
            "delete all",
            "drop table",
            "format ",
            "mkfs",
            ":(){:|:&};:",
            "shutdown",
            "chmod 777"
    );

    /**
     * 校验基因内容安全性。
     * <ul>
     *   <li>content 不为空</li>
     *   <li>content 长度不超过 10000 字符</li>
     *   <li>content 不包含危险指令</li>
     *   <li>epigeneticBoost 在 0.1-10.0 范围内</li>
     * </ul>
     *
     * @param gene 待校验基因
     * @return true 表示通过安全校验
     */
    public boolean validateGene(Gene gene) {
        if (gene == null) {
            return false;
        }
        String content = gene.content();
        if (content == null || content.isBlank()) {
            return false;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return false;
        }
        String lower = content.toLowerCase();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lower.contains(pattern)) {
                return false;
            }
        }
        double boost = gene.epigeneticBoost();
        if (boost < MIN_BOOST || boost > MAX_BOOST) {
            return false;
        }
        return true;
    }

    /**
     * 检查基因与现有基因是否矛盾。
     * <p>
     * 同 category 且同 name 视为冲突（排除自身）。
     *
     * @param gene           待检查基因
     * @param existingGenes  已有基因列表
     * @return true 表示存在冲突
     */
    public boolean checkConflict(Gene gene, List<Gene> existingGenes) {
        if (gene == null || existingGenes == null || existingGenes.isEmpty()) {
            return false;
        }
        String name = gene.name();
        if (name == null || name.isBlank()) {
            return false;
        }
        for (Gene existing : existingGenes) {
            if (existing == null) {
                continue;
            }
            // 排除自身
            if (existing.id() != null && existing.id().equals(gene.id())) {
                continue;
            }
            if (existing.category() == gene.category()
                    && name.equals(existing.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 综合判断基因是否可以安全固化。
     * <ul>
     *   <li>金丝雀检查必须通过</li>
     *   <li>gene.epigeneticBoost() &gt;= 0.5</li>
     *   <li>validateGene 通过</li>
     * </ul>
     *
     * @param gene          待固化基因
     * @param canaryResult  金丝雀检查结果
     * @return true 表示可以安全固化
     */
    public boolean isSafeToSolidify(Gene gene, CanaryCheck.CanaryResult canaryResult) {
        if (canaryResult == null || !canaryResult.passed()) {
            return false;
        }
        if (gene == null || gene.epigeneticBoost() < SAFE_BOOST_THRESHOLD) {
            return false;
        }
        return validateGene(gene);
    }

    /**
     * 判断基因是否应被自动禁用。
     * <p>
     * epigeneticBoost &lt; 0.5 时返回 true。
     *
     * @param gene 待判定基因
     * @return true 表示应自动禁用
     */
    public boolean shouldAutoDisable(Gene gene) {
        return gene != null && gene.epigeneticBoost() < SAFE_BOOST_THRESHOLD;
    }

    /**
     * 判断基因是否应被自动回滚。
     * <p>
     * failureCount &gt; successCount 且 failureCount &gt;= 3 时返回 true。
     *
     * @param gene 待判定基因
     * @return true 表示应自动回滚
     */
    public boolean shouldAutoRollback(Gene gene) {
        if (gene == null) {
            return false;
        }
        return gene.failureCount() > gene.successCount()
                && gene.failureCount() >= ROLLBACK_MIN_FAILURES;
    }
}
