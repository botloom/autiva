package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.agent.AgentDefinition;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.skill.SkillManager;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 上下文注入 Advisor —— 在对话首次交互时将动态上下文注入到第一个 UserMessage。
 * <p>
 * 核心设计原则（对标 Claude Code 架构）：
 * 1. SystemMessage 保持静态（agent.md 内容），可被 LLM 完全缓存
 * 2. 动态上下文（memory / environment / skills / subagents / summary）只在首次交互时
 *    注入到第一个 UserMessage，后续轮次不再重复注入
 * 3. memory.md 变化时，在下一个 UserMessage 中注入增量更新
 * 4. Skills 使用紧凑格式（一行一个），减少 token 占用
 * </p>
 */
@Slf4j
@Builder
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    private final SkillManager skillManager;
    private final AgentDefinitionManager definitionManager;
    private final AgentDefinition definition;
    private final Path memoryFilePath;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     * 核心逻辑：将动态上下文注入到第一个 UserMessage（而非 SystemMessage）。
     *
     * <p>注入策略：
     * <ul>
     *   <li>首次请求：注入完整动态上下文到第一个 UserMessage 前面，标记 contextInjected=true</li>
     *   <li>后续请求：不注入（上下文已在对话中）。如果 memory.md 变化，注入增量更新</li>
     *   <li>无 Session（子智能体场景）：跳过注入</li>
     * </ul>
     */
    private ChatClientRequest injectContext(ChatClientRequest request) {
        try {
            RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
            if (ctx == null || ctx.getSession() == null) {
                // 子智能体场景：无 Session，不需要上下文注入
                return request;
            }

            Session session = ctx.getSession();

            // 检查 memory.md 是否有变化
            String memoryContent = readMemoryFile();
            String memoryHash = memoryContent != null ? String.valueOf(memoryContent.hashCode()) : null;
            boolean memoryChanged = !Objects.equals(memoryHash, session.getLastMemoryHash());

            // 如果已注入且 memory 没变，完全跳过
            if (session.isContextInjected() && !memoryChanged) {
                return request;
            }

            String contextText;
            if (!session.isContextInjected()) {
                // 首次注入：完整上下文
                contextText = buildFullContextText(request, memoryContent);
                session.setContextInjected(true);
            } else {
                // memory 变化：只注入 memory 增量
                contextText = buildMemoryUpdateText(memoryContent);
            }

            // 更新 memory hash
            if (memoryHash != null) {
                session.setLastMemoryHash(memoryHash);
            }

            if (contextText == null || contextText.isBlank()) {
                return request;
            }

            // 注入到第一个 UserMessage
            Prompt prompt = request.prompt();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());

            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof UserMessage userMsg) {
                    String augmentedText = contextText + "\n\n" + userMsg.getText();
                    messages.set(i, new UserMessage(augmentedText));
                    return request.mutate()
                            .prompt(prompt.mutate().messages(messages).build())
                            .build();
                }
            }

            // 没有 UserMessage（极端情况）：新建一个
            messages.add(new UserMessage(contextText));
            return request.mutate()
                    .prompt(prompt.mutate().messages(messages).build())
                    .build();
        } catch (Exception e) {
            log.warn("[ProactiveContextAdvisor] 注入上下文失败", e);
            return request;
        }
    }

    /**
     * 构建首次注入的完整动态上下文
     */
    private String buildFullContextText(ChatClientRequest request, String memoryContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<context>");

        // 1. 环境元数据（OS + 时间 + 时区）
        sb.append("\n<environment>\n").append(buildEnvironmentText()).append("\n</environment>");

        // 2. 热记忆（memory.md 内容）
        if (memoryContent != null && !memoryContent.isBlank()) {
            sb.append("\n<memory>\n").append(memoryContent).append("\n</memory>");
        }

        // 3. 可用技能（紧凑格式：每 skill 一行）
        String skillListing = buildCompactSkillListing();
        if (skillListing != null && !skillListing.isBlank()) {
            sb.append("\n<skills>\n").append(skillListing).append("\n</skills>");
        }

        // 4. 可用子智能体（紧凑格式）
        String subagentListing = buildCompactSubagentListing();
        if (subagentListing != null && !subagentListing.isBlank()) {
            sb.append("\n<subagents>\n").append(subagentListing).append("\n</subagents>");
        }

        // 5. 上下文桥接（Session.summary —— 压缩后的历史对话摘要）
        RuntimeContext ctx = (RuntimeContext) request.context().get("runtimeContext");
        if (ctx != null && ctx.getSession() != null) {
            Session session = ctx.getSession();
            String summary = session.getSummary();
            if (summary != null && !summary.isBlank()) {
                sb.append("\n<summary>\n").append(summary).append("\n</summary>");
            }
        }

        sb.append("\n</context>");

        String result = sb.toString();
        if (result.length() < 50) {
            return null; // 空上下文
        }
        return result;
    }

    /**
     * 构建 memory 增量更新文本
     */
    private String buildMemoryUpdateText(String memoryContent) {
        if (memoryContent == null || memoryContent.isBlank()) {
            return null;
        }
        return "<memory-update>\n" + memoryContent + "\n</memory-update>";
    }

    /**
     * 构建环境元数据（极简版）
     */
    private String buildEnvironmentText() {
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return String.format("操作系统: %s (%s) | 时间: %s (UTC%s) | 工作目录: %s",
                osName, osArch,
                now.format(TIME_FORMATTER),
                now.getOffset().getId(),
                System.getProperty("user.dir", ""));
    }

    /**
     * 构建紧凑技能列表（每 skill 一行）
     */
    private String buildCompactSkillListing() {
        if (skillManager == null) {
            return null;
        }
        try {
            // 优先使用紧凑格式
            String compact = skillManager.getCompactListing();
            if (compact != null && !compact.isBlank()) {
                return compact;
            }
            // 回退：使用完整描述但截断
            String full = skillManager.getDescription();
            if (full != null && !full.isBlank()) {
                return full;
            }
        } catch (Exception e) {
            log.debug("[ProactiveContextAdvisor] 获取技能列表失败", e);
        }
        return null;
    }

    /**
     * 构建紧凑子智能体列表（每 agent 一行）
     */
    private String buildCompactSubagentListing() {
        if (definition == null || definitionManager == null) {
            return null;
        }
        try {
            return definition.subagents().stream()
                    .map(name -> {
                        AgentDefinition def = definitionManager.getDefinition(name);
                        if (def != null) {
                            return String.format("- %s: %s", name, def.description());
                        }
                        return "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.debug("[ProactiveContextAdvisor] 获取子智能体列表失败", e);
            return null;
        }
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
