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
import org.springframework.ai.chat.messages.SystemMessage;
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
import java.util.stream.Collectors;

/**
 * 主动上下文注入 Advisor，基于 Session 和配置动态注入上下文到系统提示词。
 * <p>
 * 注入内容（每次请求动态计算）：
 * 1. 热记忆（memory.md 内容，每次请求从文件读取最新版本）
 * 2. 环境元数据（操作系统信息、当前时间与时区）
 * 3. 上下文桥接（Session.summary，压缩后的早期对话摘要）
 * 4. 技能描述（请求时通过 SkillManager 动态计算）
 * 5. 子智能体描述（请求时通过 AgentDefinitionManager 动态计算）
 * <p>
 * Session 通过 RuntimeContext 传递，无需在构建时持有 SessionManager。
 * 技能和子智能体描述改为请求时动态计算，确保运行时变更能被感知。
 */
@Slf4j
@Builder
public class ProactiveContextAdvisor implements StreamAdvisor, CallAdvisor {

    /** 技能管理器（请求时动态计算技能描述） */
    private final SkillManager skillManager;

    /** 智能体定义管理器（请求时动态获取子智能体定义） */
    private final AgentDefinitionManager definitionManager;

    /** 当前智能体定义（提供 subagents 列表） */
    private final AgentDefinition definition;

    /** memory.md 文件路径，请求时读取最新内容 */
    private final Path memoryFilePath;

    /** 时间格式化器：yyyy-MM-dd HH:mm:ss (UTC+HH:mm) */
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

        // 2. 环境元数据（OS + 时间）
        String envText = buildEnvironmentText();
        if (!envText.isBlank()) {
            sb.append("\n<environment>\n").append(envText).append("\n</environment>");
        }

        // 3. 技能描述（动态计算）
        String skillDesc = buildSkillDescriptions();
        if (skillDesc != null && !skillDesc.isBlank()) {
            sb.append("\n<skills>\n").append(skillDesc).append("\n</skills>");
        }

        // 4. 子智能体描述（动态计算）
        String subagentDesc = buildSubagentDescriptions();
        if (subagentDesc != null && !subagentDesc.isBlank()) {
            sb.append("\n<subagents>\n").append(subagentDesc).append("\n</subagents>");
        }

        // 5. 上下文桥接（Session.summary）
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
     * 构建环境元数据文本（OS 信息 + 当前时间与时区）
     */
    private String buildEnvironmentText() {
        StringBuilder sb = new StringBuilder();
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "unknown");

        sb.append("- OS: ").append(osName);
        if (!osVersion.isEmpty()) {
            sb.append(" ").append(osVersion);
        }
        sb.append(" (").append(osArch).append(")");

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        sb.append("\n- Time: ").append(now.format(TIME_FORMATTER));

        // 时区偏移：UTC+08:00
        sb.append(" (UTC").append(now.getOffset().getId()).append(")");

        return sb.toString();
    }

    /**
     * 动态构建技能描述（每次请求从 SkillManager 获取最新）
     */
    private String buildSkillDescriptions() {
        if (skillManager == null) {
            return null;
        }
        try {
            return skillManager.getDescription();
        } catch (Exception e) {
            log.debug("[ProactiveContextAdvisor] 获取技能描述失败", e);
            return null;
        }
    }

    /**
     * 动态构建子智能体描述（每次请求从 AgentDefinitionManager 获取最新）
     */
    private String buildSubagentDescriptions() {
        if (definition == null || definitionManager == null) {
            return null;
        }
        try {
            return definition.subagents().stream()
                    .map(name -> {
                        AgentDefinition def = definitionManager.getDefinition(name);
                        return def != null ? "- " + name + ": " + def.description() : "";
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.debug("[ProactiveContextAdvisor] 获取子智能体描述失败", e);
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
