package cn.bitloom.agentic.evolve.experience;

import cn.bitloom.agentic.evolve.trajectory.Trajectory;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryOutcome;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRepository;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryStep;
import cn.bitloom.agentic.evolve.verify.DimensionResult;
import cn.bitloom.agentic.evolve.verify.TrajectoryVerifier;
import cn.bitloom.agentic.evolve.verify.VerificationContext;
import cn.bitloom.agentic.evolve.verify.VerificationResult;
import cn.bitloom.agentic.memory.AgentMemoryStore;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 经验引擎 — chapter8 五步知识提炼管道。
 * <p>
 * 从轨迹仓库中提取特定任务族的轨迹，经三层验证后，对比成功与失败轨迹，
 * 借助 LLM 归纳出可迁移的经验，并将达标经验持久化到 {@link AgentMemoryStore}。
 * <p>
 * 提炼流程：
 * <ol>
 *   <li>查询 taskFamily 相关的所有轨迹（按 metadata.taskFamily 过滤，回退到 agentName）</li>
 *   <li>对每条轨迹运行 {@link TrajectoryVerifier#verify} 生成 VerificationResult</li>
 *   <li>按 outcome 聚合为成功组 / 失败组 / 部分成功组</li>
 *   <li>调用 ChatClient，注入成功与失败轨迹的对比，要求 LLM 提出候选经验</li>
 *   <li>解析 LLM 返回的 JSON，按 supportCount 过滤升级为 VERIFIED / CANDIDATE</li>
 * </ol>
 * VERIFIED 经验写入 {@code experiences/{taskFamily}.md}，可通过 {@link #loadExperiences} / {@link #loadAllExperiences} 读回。
 */
@Slf4j
public class ExperienceEngine {

    /** 默认最小支持轨迹数，达到此值的经验升级为 VERIFIED */
    public static final int DEFAULT_MIN_SUPPORT = 2;

    /** 经验存储子目录（相对于 memoryStore 根目录） */
    private static final String EXPERIENCES_SUBDIR = "experiences";

    /** 轨迹摘要中文本截断长度 */
    private static final int USER_MSG_LIMIT = 200;
    private static final int STEP_CONTENT_LIMIT = 150;
    private static final int SUMMARY_LIMIT = 200;
    private static final int REASON_LIMIT = 100;

    /** LLM 系统提示词 */
    private static final String SYSTEM_PROMPT = """
            你是经验归纳专家，从成功和失败的轨迹对比中提取可迁移的经验。

            任务：
            1. 分析成功轨迹的共同特征（哪些做法导致了成功）
            2. 分析失败轨迹的共同特征（哪些做法导致了失败）
            3. 对比成功与失败的差异，提取可迁移的经验

            每个经验应包含：
            - appliesWhen: 适用场景描述（什么时候应用这条经验）
            - recommendedStrategy: 推荐策略（应该怎么做）
            - commonPitfall: 常见误区（容易犯的错误）
            - exceptionCondition: 例外条件（什么情况下这条经验不适用）
            - capabilities: 所需能力列表（如 ["search","edit","test"]）

            返回严格的 JSON 数组格式，不要包含 markdown 代码块标记（```json）。
            如果没有可提取的经验，返回空数组 []。

            返回格式：
            [
              {
                "appliesWhen": "重构现有代码时",
                "recommendedStrategy": "先用 Grep 搜索所有引用点，再逐个修改",
                "commonPitfall": "不要直接删除方法，可能存在反射调用",
                "exceptionCondition": "当项目使用 Lombok 时，需额外注意生成的方法",
                "capabilities": ["search", "edit", "test"]
              }
            ]
            """;

    private final TrajectoryRepository trajectoryRepo;
    private final TrajectoryVerifier verifier;
    private final AgentMemoryStore memoryStore;
    private final ChatClient chatClient;

    public ExperienceEngine(TrajectoryRepository trajectoryRepo,
                            TrajectoryVerifier verifier,
                            AgentMemoryStore memoryStore,
                            ChatClient chatClient) {
        this.trajectoryRepo = trajectoryRepo;
        this.verifier = verifier;
        this.memoryStore = memoryStore;
        this.chatClient = chatClient;
    }

    // ===================== 核心管道 =====================

    /**
     * 五步知识提炼管道：从指定任务族的轨迹中提取经验。
     *
     * @param taskFamily    任务族标识
     * @param minSupport    最小支持轨迹数，达到此值的候选经验升级为 VERIFIED
     * @param verifyContext 验证上下文
     * @return 提取的所有经验列表（含 VERIFIED 和 CANDIDATE）
     */
    public List<Experience> extractExperiences(String taskFamily, int minSupport, VerificationContext verifyContext) {
        if (taskFamily == null || taskFamily.isBlank()) {
            log.warn("[ExperienceEngine] taskFamily 为空，跳过经验提取");
            return List.of();
        }

        // 步骤1：查询 taskFamily 相关的所有轨迹
        List<Trajectory> trajectories = findTrajectoriesByTaskFamily(taskFamily);
        if (trajectories.isEmpty()) {
            log.info("[ExperienceEngine] 未找到 taskFamily={} 的轨迹", taskFamily);
            return List.of();
        }
        log.info("[ExperienceEngine] taskFamily={} 共加载 {} 条轨迹", taskFamily, trajectories.size());

        // 步骤2：对每条轨迹运行验证器
        Map<Trajectory, VerificationResult> verified = new LinkedHashMap<>();
        for (Trajectory t : trajectories) {
            try {
                VerificationResult vr = verifier.verify(t, verifyContext);
                verified.put(t, vr);
            } catch (Exception e) {
                log.warn("[ExperienceEngine] 验证轨迹失败: trajectoryId={}", t.id(), e);
            }
        }

        // 步骤3：按 outcome 聚合为成功组 / 失败组 / 部分成功组
        List<Trajectory> successes = new ArrayList<>();
        List<Trajectory> failures = new ArrayList<>();
        List<Trajectory> partials = new ArrayList<>();
        for (Map.Entry<Trajectory, VerificationResult> entry : verified.entrySet()) {
            TrajectoryOutcome outcome = entry.getValue().outcome();
            switch (outcome) {
                case SUCCESS -> successes.add(entry.getKey());
                case FAILURE -> failures.add(entry.getKey());
                case PARTIAL_SUCCESS -> partials.add(entry.getKey());
                default -> { /* UNKNOWN 忽略 */ }
            }
        }
        log.info("[ExperienceEngine] taskFamily={}: 成功={}, 失败={}, 部分成功={}",
                taskFamily, successes.size(), failures.size(), partials.size());

        if (successes.isEmpty() && failures.isEmpty() && partials.isEmpty()) {
            log.info("[ExperienceEngine] taskFamily={} 无可归纳的轨迹（均为 UNKNOWN），跳过", taskFamily);
            return List.of();
        }

        // 步骤4-5：调用 LLM 提取候选经验并解析
        int supportCount = successes.size();
        List<String> sourceIds = successes.stream()
                .map(Trajectory::id)
                .toList();
        List<Experience> candidates = invokeLLM(taskFamily, successes, failures, partials, verified,
                sourceIds, supportCount, minSupport);

        // 步骤6：过滤（已在 invokeLLM 内完成 status 判定）

        // 步骤7：持久化 VERIFIED 经验
        List<Experience> verifiedExperiences = candidates.stream()
                .filter(e -> e.status() == ExperienceStatus.VERIFIED)
                .toList();
        if (!verifiedExperiences.isEmpty()) {
            persistExperiences(taskFamily, verifiedExperiences);
            log.info("[ExperienceEngine] taskFamily={} 持久化 {} 条 VERIFIED 经验",
                    taskFamily, verifiedExperiences.size());
        }

        // 步骤8：返回所有提取的经验
        return candidates;
    }

    // ===================== 加载方法 =====================

    /**
     * 从 AgentMemoryStore 读取指定任务族的经验。
     * <p>
     * 读取 {@code experiences/{taskFamily}.md}，支持单文件存储多条经验（以分隔标记分割）。
     *
     * @param taskFamily 任务族标识
     * @return 经验列表（文件不存在时返回空列表）
     */
    public List<Experience> loadExperiences(String taskFamily) {
        String path = experienceFilePath(taskFamily);
        if (!memoryStore.exists(path)) {
            return List.of();
        }
        try {
            String content = memoryStore.readFile(path);
            String[] parts = content.split(Experience.splitMarker());
            List<Experience> experiences = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    experiences.add(Experience.fromMarkdown(trimmed));
                } catch (Exception e) {
                    log.warn("[ExperienceEngine] 解析经验失败: taskFamily={}, 部分={}",
                            taskFamily, trimmed.substring(0, Math.min(80, trimmed.length())), e);
                }
            }
            return experiences;
        } catch (IOException e) {
            log.error("[ExperienceEngine] 读取经验失败: taskFamily={}", taskFamily, e);
            return List.of();
        }
    }

    /**
     * 列出 experiences/ 目录下所有 .md 文件，逐个反序列化为经验。
     *
     * @return 所有任务族的全部经验列表
     */
    public List<Experience> loadAllExperiences() {
        List<Experience> all = new ArrayList<>();
        if (!memoryStore.exists(EXPERIENCES_SUBDIR) || !memoryStore.isDirectory(EXPERIENCES_SUBDIR)) {
            return all;
        }
        try {
            List<AgentMemoryStore.Entry> entries = memoryStore.list(EXPERIENCES_SUBDIR);
            for (AgentMemoryStore.Entry entry : entries) {
                if (entry.directory() || !entry.name().endsWith(".md")) {
                    continue;
                }
                String taskFamily = entry.name().substring(0, entry.name().length() - 3);
                all.addAll(loadExperiences(taskFamily));
            }
        } catch (IOException e) {
            log.error("[ExperienceEngine] 加载所有经验失败", e);
        }
        return all;
    }

    // ===================== 内部方法 =====================

    /**
     * 查询指定 taskFamily 的所有轨迹。
     * <p>
     * 遍历所有 outcome 类型加载全部轨迹，再在内存中按 metadata.taskFamily 过滤；
     * 若 metadata 中无 taskFamily，则回退到按 agentName 过滤。
     */
    private List<Trajectory> findTrajectoriesByTaskFamily(String taskFamily) {
        List<Trajectory> all = new ArrayList<>();
        for (TrajectoryOutcome outcome : TrajectoryOutcome.values()) {
            all.addAll(trajectoryRepo.findByOutcome(outcome));
        }
        return all.stream()
                .filter(t -> matchesTaskFamily(t, taskFamily))
                .toList();
    }

    /**
     * 判断轨迹是否属于指定任务族。
     * 优先从 metadata.taskField 匹配，回退到 agentName。
     */
    private boolean matchesTaskFamily(Trajectory t, String taskFamily) {
        if (t.metadata() != null) {
            Object tf = t.metadata().get("taskFamily");
            if (tf instanceof String s && !s.isBlank()) {
                return taskFamily.equals(s);
            }
        }
        // metadata 中无 taskFamily，回退到 agentName
        return taskFamily.equals(t.agentName());
    }

    // ===================== LLM 调用 =====================

    /**
     * 调用 LLM 提取候选经验并解析为 Experience 列表。
     */
    private List<Experience> invokeLLM(String taskFamily,
                                       List<Trajectory> successes,
                                       List<Trajectory> failures,
                                       List<Trajectory> partials,
                                       Map<Trajectory, VerificationResult> verified,
                                       List<String> sourceIds,
                                       int supportCount,
                                       int minSupport) {
        String userPrompt = buildUserPrompt(taskFamily, successes, failures, partials, verified);

        String response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[ExperienceEngine] LLM 调用失败: taskFamily={}", taskFamily, e);
            return List.of();
        }

        if (response == null || response.isBlank()) {
            log.warn("[ExperienceEngine] LLM 返回空响应: taskFamily={}", taskFamily);
            return List.of();
        }

        return parseCandidateExperiences(response, taskFamily, sourceIds, supportCount, minSupport);
    }

    /**
     * 构建用户提示词，包含成功/失败/部分成功轨迹的摘要对比。
     */
    private String buildUserPrompt(String taskFamily,
                                   List<Trajectory> successes,
                                   List<Trajectory> failures,
                                   List<Trajectory> partials,
                                   Map<Trajectory, VerificationResult> verified) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务族: ").append(taskFamily).append("\n\n");

        sb.append("=== 成功轨迹（共 ").append(successes.size()).append(" 条）===\n");
        for (Trajectory t : successes) {
            sb.append(formatTrajectorySummary(t, verified.get(t))).append("\n");
        }

        sb.append("\n=== 失败轨迹（共 ").append(failures.size()).append(" 条）===\n");
        for (Trajectory t : failures) {
            sb.append(formatTrajectorySummary(t, verified.get(t))).append("\n");
        }

        sb.append("\n=== 部分成功轨迹（共 ").append(partials.size()).append(" 条）===\n");
        for (Trajectory t : partials) {
            sb.append(formatTrajectorySummary(t, verified.get(t))).append("\n");
        }

        sb.append("\n请对比成功和失败的轨迹，提取可迁移的经验。返回 JSON 数组。");
        return sb.toString();
    }

    /**
     * 格式化单条轨迹的摘要（含步骤和验证维度）。
     */
    private String formatTrajectorySummary(Trajectory t, VerificationResult vr) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 轨迹ID: ").append(t.id()).append("\n");
        sb.append("  用户消息: ").append(truncate(t.userMessage(), USER_MSG_LIMIT)).append("\n");

        if (vr != null) {
            sb.append("  验证结果: ").append(vr.outcome())
                    .append(" (置信度: ").append(String.format("%.2f", vr.confidence())).append(")\n");
            sb.append("  验证总结: ").append(truncate(vr.summary(), SUMMARY_LIMIT)).append("\n");
            sb.append("  验证维度:\n");
            for (DimensionResult d : vr.dimensions()) {
                sb.append("    ").append(d.dimension()).append(": ").append(d.verdict())
                        .append(" (").append(String.format("%.2f", d.score())).append(")")
                        .append(" - ").append(truncate(d.reason(), REASON_LIMIT)).append("\n");
            }
        }

        sb.append("  步骤:\n");
        for (TrajectoryStep step : t.steps()) {
            sb.append("    [").append(step.index()).append("] ").append(step.type());
            if (step.toolName() != null) {
                sb.append(" (").append(step.toolName()).append(")");
            }
            if (step.content() != null && !step.content().isEmpty()) {
                sb.append(": ").append(truncate(step.content(), STEP_CONTENT_LIMIT));
            }
            sb.append(step.success() ? "" : " [失败]");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的 JSON 数组为候选经验列表。
     * <p>
     * 处理可能的 markdown 代码块包裹，按 supportCount 与 minSupport 判定状态。
     */
    private List<Experience> parseCandidateExperiences(String response,
                                                       String taskFamily,
                                                       List<String> sourceIds,
                                                       int supportCount,
                                                       int minSupport) {
        List<Experience> candidates = new ArrayList<>();
        try {
            String json = stripCodeFence(response);
            JsonNode root = JsonUtils.parse(json);
            if (!root.isArray()) {
                log.warn("[ExperienceEngine] LLM 返回的不是 JSON 数组: taskFamily={}", taskFamily);
                return candidates;
            }

            Instant now = Instant.now();
            ExperienceStatus status = supportCount >= minSupport
                    ? ExperienceStatus.VERIFIED
                    : ExperienceStatus.CANDIDATE;

            for (JsonNode node : root) {
                String id = "exp-" + UUID.randomUUID();
                List<String> capabilities = parseStringList(node.get("capabilities"));
                String appliesWhen = getTextOrDefault(node, "appliesWhen", "");
                String recommendedStrategy = getTextOrDefault(node, "recommendedStrategy", "");
                String commonPitfall = getTextOrDefault(node, "commonPitfall", "");
                String exceptionCondition = getTextOrDefault(node, "exceptionCondition", "");

                candidates.add(new Experience(
                        id, taskFamily, capabilities, appliesWhen,
                        recommendedStrategy, commonPitfall, exceptionCondition,
                        sourceIds, now, now, supportCount, status
                ));
            }
        } catch (Exception e) {
            log.error("[ExperienceEngine] 解析候选经验失败: taskFamily={}, response={}",
                    taskFamily, response.substring(0, Math.min(200, response.length())), e);
        }
        return candidates;
    }

    // ===================== 持久化 =====================

    /**
     * 将多条 VERIFIED 经验写入 {@code experiences/{taskFamily}.md}。
     * <p>
     * 单文件存储多条经验时，以分隔标记分割。
     */
    private void persistExperiences(String taskFamily, List<Experience> experiences) {
        if (experiences.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < experiences.size(); i++) {
            if (i > 0) {
                sb.append("\n").append(Experience.splitMarker()).append("\n");
            }
            sb.append(experiences.get(i).toMarkdown());
        }
        String path = experienceFilePath(taskFamily);
        try {
            memoryStore.writeFile(path, sb.toString());
        } catch (IOException e) {
            log.error("[ExperienceEngine] 持久化经验失败: taskFamily={}, path={}", taskFamily, path, e);
        }
    }

    /**
     * 构造经验文件的相对路径：{@code experiences/{taskFamily}.md}
     */
    private String experienceFilePath(String taskFamily) {
        return EXPERIENCES_SUBDIR + "/" + taskFamily + ".md";
    }

    // ===================== 通用辅助 =====================

    /**
     * 去除 LLM 返回内容中可能包裹的 markdown 代码块标记（```json ... ```）。
     */
    private String stripCodeFence(String response) {
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        return json;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultValue;
        }
        return child.asText();
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode element : node) {
            if (!element.isNull()) {
                result.add(element.asText());
            }
        }
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }
}
