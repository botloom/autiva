package cn.bitloom.agentic.evolve.routing;

/**
 * 路由表条目 — 描述某个基因在何种条件下被激活及其优先级。
 *
 * @param geneId    关联的基因 ID
 * @param priority  优先级（数值越大越优先）
 * @param condition 激活条件描述（自然语言或表达式，当前由调用方解析）
 * @param enabled   是否启用
 */
public record RoutingEntry(String geneId, double priority, String condition, boolean enabled) {
}
