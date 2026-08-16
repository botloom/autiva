package cn.bitloom.agentic.workflow;

import cn.bitloom.agentic.agent.Agent;
import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.agent.SubAgentFactory;
import cn.bitloom.agentic.event.MessageEvent;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Workflow 编排原语 DSL（对标 learn-claude-code s16："编排形状固定时写进代码，不靠模型逐轮决策"）。
 *
 * <p>原语：{@link #agent}（派子智能体 + schema 校验 + 失败重试一次）、{@link #parallel}（等齐屏障）、
 * {@link #pipeline}（流水线不等齐）、{@link #phase}/{@link #log}（进度标记）。
 * 所有 agent 调用经 {@link WorkflowJournal} 语义 key 缓存，resume 时命中回放。
 */
@Slf4j
public class WorkflowContext {

    private static final Pattern LABEL_SLUG = Pattern.compile("[^a-zA-Z0-9_-]");

    private final Session session;
    private final String runId;
    private final String projectPath;
    private final SubAgentFactory factory;
    private final WorkflowJournal journal;
    private final Consumer<String> progress;

    public WorkflowContext(Session session, String runId, String projectPath,
            SubAgentFactory factory, WorkflowJournal journal, Consumer<String> progress) {
        this.session = session;
        this.runId = runId;
        this.projectPath = projectPath;
        this.factory = factory;
        this.journal = journal;
        this.progress = progress;
    }

    /**
     * 派子智能体执行任务。
     *
     * @param definition 底层智能体类型（Explore 只读审计 / General 全能执行）
     * @param prompt     任务指令（自包含）
     * @param schemaJson 输出 JSON 约束（null = 自由文本）；输出必须可解析为 JSON，失败重试 1 次
     * @param label      稳定语义标识（journal key 组成部分；同 workflow 内不同调用勿重名）
     * @return 子智能体输出（schema 模式下为 JSON 文本）
     */
    public String agent(String definition, String prompt, String schemaJson, String label) {
        String key = WorkflowJournal.semanticKey("agent", label, prompt, schemaJson);
        if (journal.has(key)) {
            log("[journal] 回放缓存: {}", label);
            return journal.replay(key);
        }
        String branch = "workflow." + runId + "." + slug(label);
        String fullPrompt = schemaJson != null
                ? prompt + "\n\n输出要求：只输出一个符合以下结构约束的 JSON（不要输出其它文本）：\n" + schemaJson
                : prompt;

        String output = runAgent(definition, fullPrompt, branch);
        if (schemaJson != null && !isJson(output)) {
            log("[workflow] {} 输出非 JSON，重试一次", label);
            output = runAgent(definition,
                    fullPrompt + "\n\n注意：上一次输出不是合法 JSON，这次必须只输出 JSON。", branch);
        }
        journal.recordAgent(key, output);
        return output;
    }

    /** 等齐屏障：全部完成才返回（完成序不确定 — 语义 key 绝不包含顺序） */
    public <T> List<T> parallel(List<Supplier<T>> thunks) {
        List<CompletableFuture<T>> futures = thunks.stream()
                .map(CompletableFuture::supplyAsync)
                .toList();
        List<T> result = new ArrayList<>();
        for (CompletableFuture<T> f : futures) {
            result.add(f.join());
        }
        return result;
    }

    /** 流水线：单项完成即流向下一 stage，不等齐 */
    public <T> List<T> pipeline(List<T> items, List<Function<T, T>> stages) {
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (T item : items) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> item);
            for (Function<T, T> stage : stages) {
                future = future.thenApply(stage);
            }
            futures.add(future);
        }
        List<T> result = new ArrayList<>();
        for (CompletableFuture<T> f : futures) {
            result.add(f.join());
        }
        return result;
    }

    /** 阶段标记：journal + 进度回调 */
    public void phase(String title) {
        journal.log("phase", title);
        if (progress != null) {
            progress.accept("[phase] " + title);
        }
    }

    /** 日志：journal + 进度回调（支持 {} 占位） */
    public void log(String message, Object... args) {
        String text = message.formatted(args);
        journal.log("log", text);
        if (progress != null) {
            progress.accept(text);
        }
    }

    private String runAgent(String definition, String prompt, String branch) {
        Agent agent = factory.build(session, definition, branch, projectPath, null, null);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(session.id())
                .userId(session.userId())
                .branch(branch)
                .projectPath(projectPath)
                .build();
        StringBuilder result = new StringBuilder();
        MessageEvent inputEvent = MessageEvent.userMessage(session.id(), prompt);
        agent.runStream(inputEvent, ctx)
                .doOnNext(event -> {
                    if (event instanceof MessageEvent me && me.isAssistantMessage() && me.getText() != null) {
                        result.append(me.getText());
                    }
                })
                .blockLast();
        return result.toString();
    }

    private boolean isJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            String trimmed = text.trim();
            // 剥离可能的 markdown 代码块包裹
            if (trimmed.startsWith("```")) {
                int start = trimmed.indexOf('\n');
                int end = trimmed.lastIndexOf("```");
                if (start > 0 && end > start) {
                    trimmed = trimmed.substring(start + 1, end).trim();
                }
            }
            JsonUtils.parse(trimmed);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    private String slug(String label) {
        return LABEL_SLUG.matcher(label.toLowerCase(Locale.ROOT)).replaceAll("-").substring(0,
                Math.min(label.length(), 24));
    }
}
