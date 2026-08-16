package cn.bitloom.agentic.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 目标判断器（对标 learn-claude-code s17 "判断器与执行者分离"）。
 *
 * <p>另一次独立轻量模型调用，<b>零工具</b>：只依据对话中已出现的内容判定目标是否达成，
 * 不得臆测未出现的执行结果。执行者（主模型）负责修改代码、跑命令；
 * 判断器只读对话文本，输出 {@code {ok, reason, impossible}}。
 *
 * <p>判断器调用失败时由调用方（GoalJudgeHook）停止自动续轮、保留目标——绝不宣称成功。
 */
@Slf4j
public class GoalJudge {

    /** 判定结果 */
    public record Verdict(boolean ok, String reason, boolean impossible) {
        public static Verdict blocked(String reason) {
            return new Verdict(false, reason, false);
        }
    }

    private static final String SYSTEM_PROMPT = """
            你是目标达成判断器。用户给智能体设定了一个目标，智能体已经执行了若干轮。
            你的唯一任务：依据下方对话记录，判断目标是否已经真实达成。

            铁律：
            1. 不得臆测未在对话中出现的执行结果。目标要求的验证命令/检查必须已在对话中
               明确出现且结果符合要求，才能判定 ok=true。
            2. 对话中没有证据的，一律 ok=false，并在 reason 中指出还缺什么证据。
            3. 目标在现有条件下根本无法实现（如依赖缺失、目标自相矛盾、被明确拒绝且无替代路径）时
               impossible=true。
            4. 只输出一个 JSON，不要任何其它文本：
               {"ok": true/false, "reason": "简短原因", "impossible": true/false}""";

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoalJudge(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 判定目标是否达成。
     *
     * @param goal      目标描述（结束状态 + 验证方式 + 限制条件）
     * @param roundText 本轮对话文本（用户消息 + 助手回复 + 工具结果）
     * @return 判定结果；调用失败抛出异常（由调用方决定停止续轮）
     */
    public Verdict judge(String goal, String roundText) {
        String prompt = """
                <目标>
                %s
                </目标>

                <对话记录>
                %s
                </对话记录>

                判定目标是否已达成。""".formatted(goal, roundText);

        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .call()
                .content();
        return parseVerdict(content);
    }

    /** JSON 解析容错：提取首个 {...} 片段解析 */
    private Verdict parseVerdict(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("判断器返回空内容");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("判断器输出中无 JSON: " + truncate(content, 200));
        }
        try {
            var node = objectMapper.readTree(content.substring(start, end + 1));
            boolean ok = node.path("ok").asBoolean(false);
            boolean impossible = node.path("impossible").asBoolean(false);
            String reason = node.path("reason").asText("");
            return new Verdict(ok, reason, impossible);
        } catch (Exception e) {
            throw new IllegalStateException("判断器输出解析失败: " + truncate(content, 200), e);
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
