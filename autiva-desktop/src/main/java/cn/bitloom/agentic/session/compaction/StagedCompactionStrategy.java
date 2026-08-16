package cn.bitloom.agentic.session.compaction;

import cn.bitloom.agentic.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 四步分层压缩管线（对标 learn-claude-code s08 Context Compact）。
 * <p>
 * 按"信息损失和调用成本从低到高"编排，每步后检查是否已降到目标水位，
 * 能停就停——低成本操作永远优先于 LLM 摘要：
 * <ol>
 *   <li>第 1 步（已有，管线外）：ToolResultOffloadHook 在工具调用后立即落盘超长结果</li>
 *   <li>第 2 步 snip_compact：事件数超限时滑动窗口裁剪（归档完整事件，零 LLM 成本）</li>
 *   <li>第 3 步 micro_compact：旧 tool 结果占位符化——最近 N 条保持完整，
 *       更早且超阈值字符的缩短为占位符（零 LLM 成本）</li>
 *   <li>水位检查：token 估算已降到触发阈值的 {@code targetRatio} 以下则结束，省掉 LLM 调用</li>
 *   <li>第 4 步 compact_history：委托给 LLM 递归摘要策略（有成本，最后手段）</li>
 * </ol>
 * 归档事件走现有 archived 机制（持久化保留，UI/搜索仍可见），等价于 transcript 落盘。
 */
@Slf4j
public final class StagedCompactionStrategy implements CompactionStrategy {

    public static final int DEFAULT_MAX_EVENTS = 50;
    public static final int DEFAULT_HEAD_KEEP = 3;
    public static final int DEFAULT_RECENT_TOOL_KEEP = 3;
    public static final int DEFAULT_TOOL_PLACEHOLDER_THRESHOLD = 120;
    public static final double DEFAULT_TARGET_RATIO = 0.6;

    /** 第 2 步：滑动窗口裁剪（复用现有策略，零 LLM 成本） */
    private final SlidingWindowCompactionStrategy slidingWindow;

    /** 第 4 步：LLM 递归摘要（有成本，最后手段） */
    private final CompactionStrategy summarizer;

    private final TokenCountEstimator tokenCountEstimator;

    /** 水位检查目标：触发阈值的比例（降到此比例以下则跳过 LLM 摘要） */
    private final double targetRatio;

    /** token 触发阈值（与 TokenCountTrigger 的 threshold 一致） */
    private final int tokenThreshold;

    private final int recentToolKeep;

    private final int toolPlaceholderThreshold;

    private StagedCompactionStrategy(SlidingWindowCompactionStrategy slidingWindow, CompactionStrategy summarizer,
                                     TokenCountEstimator tokenCountEstimator, double targetRatio, int tokenThreshold,
                                     int recentToolKeep, int toolPlaceholderThreshold) {
        Assert.notNull(slidingWindow, "slidingWindow must not be null");
        Assert.notNull(summarizer, "summarizer must not be null");
        Assert.notNull(tokenCountEstimator, "tokenCountEstimator must not be null");
        this.slidingWindow = slidingWindow;
        this.summarizer = summarizer;
        this.tokenCountEstimator = tokenCountEstimator;
        this.targetRatio = targetRatio;
        this.tokenThreshold = tokenThreshold;
        this.recentToolKeep = recentToolKeep;
        this.toolPlaceholderThreshold = toolPlaceholderThreshold;
    }

    @Override
    public CompactionResult compact(CompactionRequest request) {
        Assert.notNull(request, "request must not be null");

        // ===== 第 2 步 snip_compact：滑动窗口裁剪 =====
        CompactionResult snipResult = slidingWindow.compact(request);
        List<MessageEvent> current = new ArrayList<>(snipResult.compactedEvents());
        List<MessageEvent> archived = new ArrayList<>(snipResult.archivedEvents());
        int tokensSaved = snipResult.tokensEstimatedSaved();
        log.debug("[StagedCompaction] 第2步裁剪: 归档 {} 条事件", archived.size());

        // ===== 第 3 步 micro_compact：旧 tool 结果占位符化 =====
        PlaceholderResult phResult = placeholderizeToolResponses(request, current);
        current = phResult.compacted;
        archived.addAll(phResult.archived);
        tokensSaved += phResult.tokensSaved;
        log.debug("[StagedCompaction] 第3步占位符化: 归档 {} 条完整工具结果", phResult.archived.size());

        // ===== 水位检查：能否省掉 LLM 摘要 =====
        if (isBelowTargetLevel(current)) {
            log.info("[StagedCompaction] 零成本步骤已降到水位以下（估算 {} tokens），跳过 LLM 摘要",
                    estimateTokens(current));
            return new CompactionResult(current, archived, tokensSaved);
        }

        // ===== 第 4 步 compact_history：LLM 递归摘要 =====
        CompactionRequest stage4Request = new CompactionRequest(request.session(), current,
                current.size(), request.currentTurnCount());
        CompactionResult summaryResult = summarizer.compact(stage4Request);
        archived.addAll(summaryResult.archivedEvents());
        log.info("[StagedCompaction] 前置步骤不足，已执行 LLM 摘要: 累计归档 {} 条事件", archived.size());
        return new CompactionResult(summaryResult.compactedEvents(), archived,
                tokensSaved + summaryResult.tokensEstimatedSaved());
    }

    /**
     * micro_compact：遍历事件中的 toolResponse，最近 {@code recentToolKeep} 条保持完整，
     * 更早且超 {@code toolPlaceholderThreshold} 字符的替换为占位符事件（原完整版归档）。
     */
    private PlaceholderResult placeholderizeToolResponses(CompactionRequest request, List<MessageEvent> events) {
        // 收集 toolResponse 事件的下标（从旧到新）
        List<Integer> toolResponseIdx = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).isToolResponse()) {
                toolResponseIdx.add(i);
            }
        }
        if (toolResponseIdx.size() <= recentToolKeep) {
            return new PlaceholderResult(events, List.of(), 0);
        }

        // 最近 N 条保持完整的起始位置
        int keepFromIdx = toolResponseIdx.size() - recentToolKeep;
        List<MessageEvent> archived = new ArrayList<>();
        List<MessageEvent> result = new ArrayList<>(events);
        int tokensSaved = 0;

        for (int rank = 0; rank < keepFromIdx; rank++) {
            int eventIdx = toolResponseIdx.get(rank);
            MessageEvent original = events.get(eventIdx);
            List<MessageEvent.ToolResponseInfo> responses = original.getResponses();
            if (responses == null) {
                continue;
            }

            // 拼接全部 responseData 估算长度；已落盘的结果保留路径提示
            String joined = responses.stream()
                    .map(r -> r.responseData() != null ? r.responseData() : "")
                    .reduce("", (a, b) -> a + b);
            if (joined.length() <= toolPlaceholderThreshold) {
                continue;
            }

            // 替换为占位符版本（保留 id/name，responseData 缩短）
            List<ToolResponseMessage.ToolResponse> placeholders = responses.stream()
                    .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(),
                            shorten(r.responseData())))
                    .toList();
            Map<String, Object> metadata = new HashMap<>(original.getMetadata() != null
                    ? original.getMetadata() : Map.of());
            metadata.put("compactedPlaceholder", Boolean.TRUE);
            MessageEvent placeholder = MessageEvent.builder()
                    .sessionId(original.getSessionId())
                    .id(original.getId())
                    .timestamp(original.getTimestamp())
                    .branch(original.getBranch())
                    .message(ToolResponseMessage.builder().responses(placeholders).build())
                    .metadata(metadata)
                    .build();

            archived.add(original);
            result.set(eventIdx, placeholder);
            tokensSaved += tokenCountEstimator.estimate(joined);
        }
        return new PlaceholderResult(result, archived, tokensSaved);
    }

    private String shorten(String responseData) {
        if (responseData == null || responseData.length() <= toolPlaceholderThreshold) {
            return responseData;
        }
        if (responseData.contains("完整内容见文件")) {
            // 已是落盘摘要，保留（内含文件路径）
            return responseData;
        }
        return "[较早的工具结果已省略，原文已归档] "
                + responseData.substring(0, Math.min(100, responseData.length())) + "...";
    }

    private boolean isBelowTargetLevel(List<MessageEvent> events) {
        return estimateTokens(events) <= (long) (tokenThreshold * targetRatio);
    }

    private int estimateTokens(List<MessageEvent> events) {
        return events.stream()
                .mapToInt(e -> tokenCountEstimator.estimate(CompactionUtils.formatEvent(e)))
                .sum();
    }

    private record PlaceholderResult(List<MessageEvent> compacted, List<MessageEvent> archived, int tokensSaved) {
    }

    public static Builder builder(CompactionStrategy summarizer) {
        return new Builder(summarizer);
    }

    public static class Builder {

        private final CompactionStrategy summarizer;
        private int maxEvents = DEFAULT_MAX_EVENTS;
        private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();
        private double targetRatio = DEFAULT_TARGET_RATIO;
        private int tokenThreshold = 100000;
        private int recentToolKeep = DEFAULT_RECENT_TOOL_KEEP;
        private int toolPlaceholderThreshold = DEFAULT_TOOL_PLACEHOLDER_THRESHOLD;

        private Builder(CompactionStrategy summarizer) {
            this.summarizer = summarizer;
        }

        public Builder maxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
            return this;
        }

        public Builder tokenCountEstimator(TokenCountEstimator estimator) {
            this.tokenCountEstimator = estimator;
            return this;
        }

        public Builder targetRatio(double targetRatio) {
            this.targetRatio = targetRatio;
            return this;
        }

        /** 与 TokenCountTrigger 的 threshold 保持一致，用于水位检查 */
        public Builder tokenThreshold(int tokenThreshold) {
            this.tokenThreshold = tokenThreshold;
            return this;
        }

        public Builder recentToolKeep(int recentToolKeep) {
            this.recentToolKeep = recentToolKeep;
            return this;
        }

        public Builder toolPlaceholderThreshold(int threshold) {
            this.toolPlaceholderThreshold = threshold;
            return this;
        }

        public StagedCompactionStrategy build() {
            SlidingWindowCompactionStrategy window = SlidingWindowCompactionStrategy.builder()
                    .maxEvents(this.maxEvents)
                    .tokenCountEstimator(this.tokenCountEstimator)
                    .build();
            return new StagedCompactionStrategy(window, this.summarizer, this.tokenCountEstimator,
                    this.targetRatio, this.tokenThreshold, this.recentToolKeep, this.toolPlaceholderThreshold);
        }
    }
}
