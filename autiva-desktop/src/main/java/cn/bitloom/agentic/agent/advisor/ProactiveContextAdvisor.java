package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.session.Session;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 主动上下文注入 Advisor，基于 Session 和配置动态注入上下文到系统提示词。
 * <p>
 * 注入内容：
 * 1. 热记忆（memory.md 内容，每次请求从文件读取最新版本）
 * 2. 上下文桥接（Session.summary，压缩后的早期对话摘要）
 * 3. 技能描述（Agent 构建时计算）
 * 4. 子智能体描述（Agent 构建时计算）
 * <p>
 * Session 通过 RuntimeContext 传递，无需在构建时持有 SessionManager。
 */
@Slf4j
@Builder
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    /** 技能描述文本（Agent 构建时计算，相对稳定） */
    private final String skillDescriptions;

    /** 子智能体描述文本（Agent 构建时计算，相对稳定） */
    private final String subagentDescriptions;

    /** memory.md 文件路径，请求时读取最新内容 */
    private final Path memoryFilePath;

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
        ChatClientRequest modifiedRequest = injectContext(request);
        return chain.nextStream(modifiedRequest);
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request,
                                                   @NonNull CallAdvisorChain chain) {
        ChatClientRequest modifiedRequest = injectContext(request);
        return chain.nextCall(modifiedRequest);
    }

    /**
     * 构建动态上下文并注入到系统提示词中
     */
    private ChatClientRequest injectContext(ChatClientRequest request) {
        try {
            String contextText = buildContextText(request);
            if (contextText == null || contextText.isBlank()) {
                return request;
            }

            // 追加动态上下文到系统提示词
            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            boolean systemMessageFound = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage sysMsg) {
                    String augmentedText = sysMsg.getText() + "\n\n" + contextText;
                    messages.set(i, SystemMessage.builder()
                            .text(augmentedText)
                            .metadata(sysMsg.getMetadata())
                            .build());
                    systemMessageFound = true;
                    break;
                }
            }

            if (!systemMessageFound) {
                messages.addFirst(new SystemMessage(contextText));
            }

            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[ProactiveContextAdvisor] 注入上下文失败", e);
            return request;
        }
    }

    /**
     * 构建动态上下文文本
     */
    private String buildContextText(ChatClientRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("<context>");

        // 1. 热记忆（memory.md）
        String memoryContent = readMemoryFile();
        if (memoryContent != null && !memoryContent.isBlank()) {
            sb.append("\n<memory>\n").append(memoryContent).append("\n</memory>");
        }

        // 2. 技能描述
        if (skillDescriptions != null && !skillDescriptions.isBlank()) {
            sb.append("\n<skills>\n").append(skillDescriptions).append("\n</skills>");
        }

        // 3. 子智能体描述
        if (subagentDescriptions != null && !subagentDescriptions.isBlank()) {
            sb.append("\n<subagents>\n").append(subagentDescriptions).append("\n</subagents>");
        }

        // 4. 上下文桥接（Session.summary）
        RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
        if (ctx != null && ctx.getSession() != null) {
            Session session = ctx.getSession();
            String summary = session.getSummary();
            if (summary != null && !summary.isBlank()) {
                sb.append("\n<summary>\n").append(summary).append("\n</summary>");
            }
        }

        sb.append("\n</context>");

        // 如果只有 <context></context> 标签，说明没有实际内容
        String result = sb.toString();
        if (result.equals("<context>\n</context>")) {
            return null;
        }
        return result;
    }

    /**
     * 读取 memory.md 文件内容
     */
    private String readMemoryFile() {
        if (memoryFilePath == null || !Files.exists(memoryFilePath)) {
            return null;
        }
        try {
            return Files.readString(memoryFilePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[ProactiveContextAdvisor] 读取 memory.md 失败: {}", memoryFilePath, e);
            return null;
        }
    }
}
