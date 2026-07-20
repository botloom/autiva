package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产出完整性校验器（确定性）。
 * <p>
 * 不调用 LLM，仅做基础完整性检查：
 * - 产出非空
 * - 产出长度合理（不为过短）
 * - 产出不是错误兜底文本
 * <p>
 * 深度完整性校验由 LlmGrader 完成。
 */
@Component
public class CompletenessGrader implements OutputGrader {

    private static final int MIN_OUTPUT_LENGTH = 10;
    private static final List<String> FALLBACK_MARKERS = List.of(
            "我无法处理", "发生错误", "出错了", "无法完成", "I cannot", "error occurred"
    );

    @Override
    public Feedback verify(AssistantMessage output, RuntimeContext ctx, List<Gene> rubrics) {
        if (output == null || output.getText() == null || output.getText().isEmpty()) {
            return Feedback.fail("产出为空", Feedback.Severity.ERROR);
        }

        String text = output.getText().trim();
        if (text.length() < MIN_OUTPUT_LENGTH) {
            return Feedback.fail("产出过短（" + text.length() + " 字符），可能未完整回答用户请求",
                    0.2, Feedback.Severity.WARN);
        }

        for (String marker : FALLBACK_MARKERS) {
            if (text.contains(marker) && text.length() < 100) {
                return Feedback.fail("产出疑似错误兜底文本: " + text.substring(0, Math.min(text.length(), 50)),
                        0.1, Feedback.Severity.ERROR);
            }
        }

        return Feedback.pass();
    }
}
