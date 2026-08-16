package cn.bitloom.node;

import java.util.List;

/**
 * Slash 命令定义与解析（编码模式命令系统）。
 *
 * <p>命令在 Controller 层拦截（handleSendMessage），由 ViewModel 分发处理，
 * 不经过 LLM。首期命令：/goal（目标闭环）、/plan（计划模式）。
 */
public final class SlashCommands {

    private SlashCommands() {
    }

    /**
     * @param name        命令名（不含 /）
     * @param description 一句话描述（浮层展示）
     * @param usage       用法提示
     * @param hasArg      是否带参数（浮层选中后补全命令名，否则直接执行）
     */
    public record Command(String name, String description, String usage, boolean hasArg) {
        public String fullName() {
            return "/" + name;
        }
    }

    public static final Command GOAL = new Command("goal",
            "设置目标闭环：独立判断器每轮复核，未达成自动续轮推进",
            "/goal <目标描述：结束状态 + 验证方式 + 限制条件>", true);

    public static final Command PLAN = new Command("plan",
            "切换计划模式：只读探索并制定计划，批准后自动执行",
            "/plan", false);

    public static List<Command> all() {
        return List.of(GOAL, PLAN);
    }

    /** 解析结果：命令名（小写）+ 参数（可为空串） */
    public record Parsed(String name, String args) {
    }

    /**
     * 解析输入文本。非命令（不以 / 开头或只有 /）返回 null。
     */
    public static Parsed parse(String text) {
        if (text == null || !text.startsWith("/") || text.startsWith("//")) {
            return null;
        }
        String body = text.substring(1).trim();
        if (body.isEmpty()) {
            return null;
        }
        String name = body.split("\\s+", 2)[0].toLowerCase();
        String args = body.contains(" ") ? body.substring(body.indexOf(' ')).trim() : "";
        return new Parsed(name, args);
    }
}
