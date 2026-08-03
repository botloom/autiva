package cn.bitloom.agentic.evolve.trajectory;

import cn.bitloom.agentic.agent.RuntimeContext;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轨迹记录器 — 通过 Hook 机制采集 Agent 执行轨迹。
 * <p>
 * 在每轮对话中累积模型调用、工具调用的步骤，对话结束后构建 {@link Trajectory} 并持久化。
 * <p>
 * 使用 sessionId 索引的 ConcurrentHashMap 存储轨迹构建器，以兼容流式模式下
 * 回调可能在不同线程执行的情况。sessionId 直接从 RuntimeContext 获取，
 * 不依赖 ThreadLocal（流式模式下 doOnComplete 线程可能与 doOnNext 不同）。
 */
@Slf4j
public class TrajectoryRecorder implements IAgentHook {

    private final TrajectoryRepository repository;

    /** sessionId -> 轨迹构建器（线程安全，兼容流式模式跨线程回调） */
    private final ConcurrentHashMap<String, TrajectoryBuilder> builders = new ConcurrentHashMap<>();

    /** 模型回复文本截断长度 */
    private static final int MODEL_TEXT_LIMIT = 500;

    /** 工具响应文本截断长度 */
    private static final int TOOL_RESPONSE_LIMIT = 1000;

    public TrajectoryRecorder(TrajectoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "TrajectoryRecorder";
    }

    @Override
    public int order() {
        return 1000; // 在 PermissionHook(10) 之后执行
    }

    @Override
    public void beforeConversationRound(RuntimeContext ctx) {
        TrajectoryBuilder builder = new TrajectoryBuilder();
        builder.startTime = Instant.now();
        builder.sessionId = ctx.getSessionId();
        builder.branch = ctx.getBranch();

        // 尝试从上下文获取 agentName
        Object agentName = ctx.getParam("agentName");
        builder.agentName = agentName instanceof String s ? s : "unknown";

        // 尝试从上下文获取 userMessage
        Object userMsg = ctx.getParam("userMessage");
        if (userMsg instanceof String s) {
            builder.userMessage = s;
        }

        String sessionId = ctx.getSessionId();
        if (sessionId != null) {
            builders.put(sessionId, builder);
        }
    }

    @Override
    public ChatClientRequest beforeModelCall(ChatClientRequest request) {
        String sessionId = extractSessionId(request);
        TrajectoryBuilder builder = sessionId != null ? builders.get(sessionId) : null;
        // 首次模型调用时，从请求中提取用户消息（如果尚未设置）
        if (builder != null && builder.userMessage == null) {
            try {
                for (var message : request.prompt().getInstructions()) {
                    if (message instanceof UserMessage um) {
                        String text = um.getText();
                        if (text != null && !text.isEmpty()) {
                            builder.userMessage = text;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[TrajectoryRecorder] 提取用户消息失败", e);
            }
        }
        return request;
    }

    @Override
    public void afterModelCall(ChatClientRequest request, ChatClientResponse response) {
        String sessionId = extractSessionId(request);
        TrajectoryBuilder builder = sessionId != null ? builders.get(sessionId) : null;
        if (builder == null || response == null || response.chatResponse() == null) {
            return;
        }

        try {
            var chatResponse = response.chatResponse();
            var result = chatResponse.getResult();
            if (result == null || result.getOutput() == null) {
                return;
            }

            var output = result.getOutput();

            // 累积模型回复文本（流式模式下每个 chunk 的文本片段）
            String text = output.getText();
            if (text != null && !text.isEmpty()) {
                builder.modelTextAccumulator.append(text);
            }

            // 检测边界信号：工具调用请求或完成信号
            boolean hasToolCalls = output.getToolCalls() != null && !output.getToolCalls().isEmpty();
            String finishReason = extractFinishReason(result);

            boolean isBoundary = hasToolCalls
                    || "STOP".equals(finishReason)
                    || "TOOL_CALLS".equals(finishReason);

            if (isBoundary) {
                // 边界到达，生成一个 MODEL_CALL 步骤
                String accumulated = builder.modelTextAccumulator.toString();
                if (!accumulated.isEmpty() || hasToolCalls) {
                    builder.steps.add(new TrajectoryStep(
                            builder.nextIndex++,
                            TrajectoryStep.StepType.MODEL_CALL,
                            truncate(accumulated, MODEL_TEXT_LIMIT),
                            null,
                            true,
                            Instant.now()
                    ));
                }
                builder.modelTextAccumulator.setLength(0);
            }
        } catch (Exception e) {
            log.debug("[TrajectoryRecorder] 记录模型调用步骤失败", e);
        }
    }

    @Override
    public String afterToolCall(String toolName, String result, ToolContext context) {
        String sessionId = extractSessionId(context);
        TrajectoryBuilder builder = sessionId != null ? builders.get(sessionId) : null;
        if (builder == null) {
            return result;
        }

        // 解析工具结果判断成功/失败
        boolean success = parseToolSuccess(result);
        Instant now = Instant.now();

        // 记录 TOOL_CALL 步骤
        builder.steps.add(new TrajectoryStep(
                builder.nextIndex++,
                TrajectoryStep.StepType.TOOL_CALL,
                "",
                toolName,
                success,
                now
        ));

        // 记录 TOOL_RESPONSE 步骤
        builder.steps.add(new TrajectoryStep(
                builder.nextIndex++,
                TrajectoryStep.StepType.TOOL_RESPONSE,
                truncate(result, TOOL_RESPONSE_LIMIT),
                toolName,
                success,
                now
        ));

        return result;
    }

    @Override
    public void afterConversationRound(RuntimeContext ctx) {
        String sessionId = ctx.getSessionId();
        if (sessionId == null) {
            return;
        }
        TrajectoryBuilder builder = builders.remove(sessionId);
        if (builder == null) {
            return;
        }

        try {
            // 如果还有未提交的模型文本，生成最后一个 MODEL_CALL 步骤
            String remaining = builder.modelTextAccumulator.toString();
            if (!remaining.isEmpty()) {
                builder.steps.add(new TrajectoryStep(
                        builder.nextIndex++,
                        TrajectoryStep.StepType.MODEL_CALL,
                        truncate(remaining, MODEL_TEXT_LIMIT),
                        null,
                        true,
                        Instant.now()
                ));
                builder.modelTextAccumulator.setLength(0);
            }

            builder.endTime = Instant.now();
            Trajectory trajectory = builder.build();
            repository.save(trajectory);
            log.info("[TrajectoryRecorder] 轨迹已持久化: id={}, sessionId={}, steps={}, outcome={}",
                    trajectory.id(), trajectory.sessionId(), trajectory.steps().size(), trajectory.outcome());
        } catch (Exception e) {
            log.warn("[TrajectoryRecorder] 持久化轨迹失败", e);
        }
    }

    // ===================== 辅助方法 =====================

    private String extractSessionId(ChatClientRequest request) {
        try {
            Object sid = request.context().get(ChatMemory.CONVERSATION_ID);
            if (sid instanceof String s) {
                return s;
            }
            Object ctx = request.context().get("runtimeContext");
            if (ctx instanceof RuntimeContext rc) {
                return rc.getSessionId();
            }
        } catch (Exception ignored) {
            // 忽略上下文访问异常
        }
        return null;
    }

    private String extractSessionId(ToolContext context) {
        if (context == null) {
            return null;
        }
        try {
            Object sid = context.getContext().get("sessionId");
            if (sid instanceof String s) {
                return s;
            }
        } catch (Exception ignored) {
            // 忽略上下文访问异常
        }
        return null;
    }

    private String extractFinishReason(org.springframework.ai.chat.model.Generation result) {
        try {
            if (result.getMetadata() != null) {
                return result.getMetadata().getFinishReason();
            }
        } catch (Exception ignored) {
            // 忽略元数据访问异常
        }
        return null;
    }

    /**
     * 从工具结果 JSON 中解析成功/失败状态。
     * 工具结果格式为 ToolResult.toJson()，包含 status 字段。
     */
    private boolean parseToolSuccess(String result) {
        if (result == null || result.isBlank()) {
            return true;
        }
        try {
            var node = JsonUtils.parse(result);
            var status = node.get("status");
            if (status != null) {
                String statusText = status.asText();
                return !"ERROR".equals(statusText);
            }
        } catch (Exception ignored) {
            // 非 JSON 格式，默认成功
        }
        return true;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...";
    }

    // ===================== 轨迹构建器 =====================

    /**
     * 轨迹构建器，在单轮对话中累积轨迹数据。
     */
    private static class TrajectoryBuilder {
        Instant startTime;
        Instant endTime;
        String sessionId;
        String branch;
        String agentName;
        String userMessage;
        final List<TrajectoryStep> steps = new ArrayList<>();
        final StringBuilder modelTextAccumulator = new StringBuilder();
        int nextIndex = 0;
        final Map<String, Object> metadata = new HashMap<>();

        Trajectory build() {
            TrajectoryOutcome outcome = determineOutcome();
            String id = UUID.randomUUID().toString();
            return new Trajectory(
                    id,
                    sessionId,
                    branch,
                    agentName,
                    startTime,
                    endTime,
                    userMessage,
                    new ArrayList<>(steps),
                    outcome,
                    new HashMap<>(metadata)
            );
        }

        /**
         * 根据步骤记录推断轨迹结果。
         */
        private TrajectoryOutcome determineOutcome() {
            if (steps.isEmpty()) {
                return TrajectoryOutcome.UNKNOWN;
            }

            boolean hasToolFailure = false;
            boolean hasToolSuccess = false;

            for (TrajectoryStep step : steps) {
                if (step.type() == TrajectoryStep.StepType.TOOL_CALL
                        || step.type() == TrajectoryStep.StepType.TOOL_RESPONSE) {
                    if (step.success()) {
                        hasToolSuccess = true;
                    } else {
                        hasToolFailure = true;
                    }
                }
            }

            if (hasToolFailure && hasToolSuccess) {
                return TrajectoryOutcome.PARTIAL_SUCCESS;
            }
            if (hasToolFailure) {
                return TrajectoryOutcome.FAILURE;
            }
            return TrajectoryOutcome.SUCCESS;
        }
    }
}
