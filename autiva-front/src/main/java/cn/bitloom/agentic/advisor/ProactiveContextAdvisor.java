package cn.bitloom.agentic.advisor;

import cn.bitloom.agentic.memory.JournalManager;
import cn.bitloom.agentic.memory.MemorySearchService;
import cn.bitloom.constant.AppConstants;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@Slf4j
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    private final JournalManager journalManager;
    private final MemorySearchService memorySearchService;
    private final EvolutionHintProvider evolutionHintProvider;

    public ProactiveContextAdvisor(JournalManager journalManager,
                                   MemorySearchService memorySearchService,
                                   EvolutionHintProvider evolutionHintProvider) {
        this.journalManager = journalManager;
        this.memorySearchService = memorySearchService;
        this.evolutionHintProvider = evolutionHintProvider;
    }

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
        String userMessage = extractUserMessage(request);
        boolean isHeartbeat = isHeartbeatRequest(request);

        if (isHeartbeat) {
            String heartbeatContent = loadHeartbeatMd(request);
            if (heartbeatContent != null && !heartbeatContent.isBlank()) {
                dynamicContext.append("\n\n# HEARTBEAT.md（心跳检查清单）\n\n").append(heartbeatContent);
            }
        }

        String recentJournals = journalManager.getRecentJournalsSummary(2);
        if (recentJournals != null && !recentJournals.isBlank()) {
            dynamicContext.append("\n\n# 近期日志（自动注入）\n\n").append(recentJournals);
        }

        if (userMessage != null && !userMessage.isBlank()) {
            String relevantMemories = memorySearchService.searchAndFormat(userMessage, 5);
            if (relevantMemories != null && !relevantMemories.isBlank()) {
                dynamicContext.append("\n\n# 相关记忆（自动召回）\n\n").append(relevantMemories);
            }
        }

        if (!isHeartbeat && userMessage != null && !userMessage.isBlank()) {
            String evolutionHint = evolutionHintProvider.getHint(userMessage);
            if (evolutionHint != null) {
                dynamicContext.append("\n\n").append(evolutionHint);
            }
        }

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

    private boolean isHeartbeatRequest(ChatClientRequest request) {
        for (Message message : request.prompt().getInstructions()) {
            if (message instanceof UserMessage userMsg) {
                Object heartbeat = userMsg.getMetadata().get("heartbeat");
                return Boolean.TRUE.equals(heartbeat);
            }
        }
        return false;
    }

    private String loadHeartbeatMd(ChatClientRequest request) {
        String conversationId = extractConversationId(request);
        if (conversationId == null) {
            return null;
        }

        String agentName = conversationId.split("-")[0];
        Path heartbeatFile = AppConstants.Base.WORKSPACE_DIR.resolve(agentName).resolve("HEARTBEAT.md");

        if (!Files.exists(heartbeatFile)) {
            heartbeatFile = loadFromBootstrap(agentName);
            if (heartbeatFile == null || !Files.exists(heartbeatFile)) {
                return null;
            }
        }

        try {
            String content = Files.readString(heartbeatFile);
            if (content.isBlank() || content.strip().matches("^#.*\\s*$")) {
                return null;
            }
            return content;
        } catch (IOException e) {
            log.warn("[ProactiveContextAdvisor] 读取 HEARTBEAT.md 失败: {}", heartbeatFile, e);
            return null;
        }
    }

    private Path loadFromBootstrap(String agentName) {
        String resourcePath = "/bootstrap/" + agentName + "/HEARTBEAT.md";
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            Path targetDir = AppConstants.Base.WORKSPACE_DIR.resolve(agentName);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve("HEARTBEAT.md");
            if (!Files.exists(targetFile)) {
                Files.writeString(targetFile, new String(is.readAllBytes()));
            }
            return targetFile;
        } catch (IOException e) {
            log.warn("[ProactiveContextAdvisor] 复制 HEARTBEAT.md 到工作区失败: {}", resourcePath, e);
            return null;
        }
    }

    private String extractConversationId(ChatClientRequest request) {
        var params = request.context().get("chat_memory_conversation_id");
        return params != null ? params.toString() : null;
    }

    private String extractUserMessage(ChatClientRequest request) {
        for (Message message : request.prompt().getInstructions()) {
            if (message instanceof UserMessage userMsg) {
                return userMsg.getText();
            }
        }
        return null;
    }
}
