package cn.bitloom.agentic.verify.grader;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.verify.Feedback;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

/**
 * 产出级校验器：在对话轮次结束时（afterConversationRound）校验最终产出。
 * <p>
 * 用于 VerificationHook 的对话级校验。确定性校验优先执行，
 * 全部通过后再由 LlmGrader 做 LLM-as-judge 校验。
 */
public interface OutputGrader {

    /**
     * 校验最终产出。
     *
     * @param output  最终产出消息
     * @param ctx     运行时上下文（可获取 userMessage/sessionId 等）
     * @param rubrics 关联的 RUBRIC Gene 列表（type=RUBRIC, targetId=agentId）
     * @return 校验反馈
     */
    Feedback verify(AssistantMessage output, RuntimeContext ctx, List<Gene> rubrics);

    /**
     * 是否启用。默认启用。
     */
    default boolean enabled() {
        return true;
    }
}
