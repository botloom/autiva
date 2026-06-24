package cn.bitloom.agentic.a2ui;

/**
 * A2UI 验证规则。
 * <p>
 * 交互组件可定义 checks 列表,对 Button 若任何 check 失败则自动禁用。
 *
 * @param condition 验证条件(本地函数调用)
 * @param message   失败提示
 */
public record A2UICheck(
        A2UIAction.FunctionCall condition,
        String message
) {}
