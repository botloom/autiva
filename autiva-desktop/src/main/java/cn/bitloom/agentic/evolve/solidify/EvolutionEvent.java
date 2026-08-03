package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.util.JsonUtils;

import java.time.Instant;

/**
 * 进化事件 — 记录一次固化/回滚/增强调整的完整信息，追加写入进化事件日志。
 *
 * @param id          事件唯一标识（UUID）
 * @param geneId      关联的基因 ID
 * @param action      动作类型：SOLIDIFY / ROLLBACK / BOOST_UPDATE
 * @param fromVersion 变更前版本号
 * @param toVersion   变更后版本号
 * @param boostBefore 变更前表观遗传增强因子
 * @param boostAfter  变更后表观遗传增强因子
 * @param reason      变更原因
 * @param timestamp   事件发生时间
 */
public record EvolutionEvent(
        String id,
        String geneId,
        String action,
        int fromVersion,
        int toVersion,
        double boostBefore,
        double boostAfter,
        String reason,
        Instant timestamp
) {

    /** 动作类型常量 */
    public static final String ACTION_SOLIDIFY = "SOLIDIFY";
    public static final String ACTION_ROLLBACK = "ROLLBACK";
    public static final String ACTION_BOOST_UPDATE = "BOOST_UPDATE";

    /**
     * 将事件序列化为 JSON 字符串（单行，用于 NDJSON 日志）。
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }
}
