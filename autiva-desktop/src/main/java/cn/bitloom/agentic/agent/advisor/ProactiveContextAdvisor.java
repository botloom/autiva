package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.session.Session;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;

/**
 * 主动上下文注入 Advisor，基于 Session 结构化注入上下文。
 * <p>
 * 注入内容：
 * 1. 基于 Session 的结构化上下文（任务清单、计划模式、对话摘要）
 * <p>
 * 注：相关记忆召回和进化提示已移除，改为智能体通过 memory_search 工具主动搜索。
 * Session 通过 RuntimeContext 传递，无需在构建时持有 SessionManager。
 */
@Slf4j
@Builder
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    @Override
    public @NonNull String getName() {
        return "ProactiveContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request,
                                                           @NonNull StreamAdvisorChain chain) {
        ChatClientRequest augmented = augmentRequest(request);
        return chain.nextStream(augmented);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest augmented = augmentRequest(request);
        return chain.nextCall(augmented);
    }

    private ChatClientRequest augmentRequest(ChatClientRequest request) {
        StringBuilder dynamicContext = new StringBuilder();
        RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
        if (ctx == null || ctx.getSession() == null) return request;
        Session session = ctx.getSession();

        injectStateBasedContext(dynamicContext, session);

        if (dynamicContext.isEmpty()) {
            return request;
        }

        var systemMessage = request.prompt().getSystemMessage();
        String augmentedSystemText = (systemMessage != null ? systemMessage.getText() : "") + dynamicContext;
        Prompt augPrompt = request.prompt().augmentSystemMessage(augmentedSystemText);
        return ChatClientRequest.builder()
                .prompt(augPrompt)
                .context(new HashMap<>(request.context()))
                .build();
    }

    /**
     * 基于 Session 注入结构化上下文
     */
    private void injectStateBasedContext(StringBuilder dynamicContext, Session session) {
        // 注入任务清单
        List<Session.TaskItem> tasks = session.getTasks();
        if (tasks != null && !tasks.isEmpty()) {
            dynamicContext.append("\n\n# 当前任务清单\n\n");
            for (Session.TaskItem task : tasks) {
                String statusIcon = switch (task.getStatus()) {
                    case "completed" -> "[x]";
                    case "in_progress" -> "[>]";
                    default -> "[ ]";
                };
                dynamicContext.append("- ").append(statusIcon).append(" ")
                        .append(task.getContent())
                        .append(" (").append(task.getPriority()).append(")")
                        .append("\n");
            }
        }

        // 注入计划模式上下文
        if (session.isPlanModeActive()) {
            dynamicContext.append("\n\n# Plan Mode 已激活\n\n");
            if (session.getPlanFilePath() != null) {
                dynamicContext.append("计划文件路径: ").append(session.getPlanFilePath()).append("\n");
            }
            dynamicContext.append("当前处于 Plan Mode，请按照计划模式规范执行任务。\n");
        }

        // 注入对话摘要（游标前自动压缩）
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            dynamicContext.append("\n\n# 早期对话摘要（游标前自动压缩）\n\n");
            dynamicContext.append("以下是本次对话早期内容的压缩摘要，供你参考上下文：\n\n");
            dynamicContext.append(session.getSummary()).append("\n");
        }
    }

    @SuppressWarnings("unused")
    private String extractUserMessage(ChatClientRequest request) {
        for (Message message : request.prompt().getInstructions()) {
            if (message instanceof UserMessage userMsg) {
                return userMsg.getText();
            }
        }
        return null;
    }
}
