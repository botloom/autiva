package cn.bitloom.agentic.workflow;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Workflow 运行日志 — journal 续跑核心（对标 learn-claude-code s16）。
 *
 * <p>运行数据落 {@code {sessionDir}/workflows/{runId}/}：
 * {@code journal.jsonl}（事件流：agent 结果 / phase / log）+ {@code outputs/}（agent 输出文件）。
 *
 * <p>每个 agent() 调用计算<strong>稳定语义 key</strong> = hash(type + label + prompt + schema)，
 * <strong>绝不包含并发顺序</strong>（parallel/pipeline 完成序不确定）。
 * resume 时 key 命中直接回放缓存结果，只有变更过的调用及其下游真正执行。
 */
@Slf4j
public class WorkflowJournal {

    /** agent 调用的稳定语义 key：sha256(type|label|prompt|schema) 前 32 位 hex */
    public static String semanticKey(String type, String label, String prompt, String schema) {
        String input = type + "|" + (label == null ? "" : label) + "|" + (prompt == null ? "" : prompt)
                + "|" + (schema == null ? "" : schema);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)), 0, 16);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private record JournalEntry(String type, String key, String file, String message, Instant timestamp) {
    }

    private final Path runDir;
    private final Path journalFile;
    private final Path outputsDir;
    /** key → output（加载已有 journal 时填充；resume 回放缓存） */
    private final Map<String, String> cache = new HashMap<>();

    private WorkflowJournal(Path runDir) {
        this.runDir = runDir;
        this.journalFile = runDir.resolve("journal.jsonl");
        this.outputsDir = runDir.resolve("outputs");
    }

    /** 新建 run 目录 */
    public static WorkflowJournal create(String sessionId, String runId) throws IOException {
        Path runDir = AppConstants.Session.sessionDir(sessionId).resolve("workflows").resolve(runId);
        Files.createDirectories(runDir.resolve("outputs"));
        return new WorkflowJournal(runDir);
    }

    /** 打开已有 run（resume）：journal 加载到缓存 */
    public static WorkflowJournal open(String sessionId, String runId) throws IOException {
        Path runDir = AppConstants.Session.sessionDir(sessionId).resolve("workflows").resolve(runId);
        if (!Files.isDirectory(runDir)) {
            throw new IllegalArgumentException("workflow run 不存在: " + runId);
        }
        WorkflowJournal journal = new WorkflowJournal(runDir);
        journal.loadCache();
        return journal;
    }

    private void loadCache() {
        if (!Files.exists(journalFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(journalFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JournalEntry entry = JsonUtils.fromJson(line, new TypeReference<JournalEntry>() {});
                if (entry != null && "agent".equals(entry.type()) && entry.key() != null
                        && entry.file() != null) {
                    Path output = outputsDir.resolve(entry.file());
                    if (Files.exists(output)) {
                        cache.put(entry.key(), Files.readString(output, StandardCharsets.UTF_8));
                    }
                }
            }
        }
        catch (Exception e) {
            log.warn("[Workflow] journal 缓存加载失败: {}: {}", runDir, e.getMessage());
        }
    }

    /** key 命中（resume 回放判定） */
    public boolean has(String key) {
        return cache.containsKey(key);
    }

    /** 回放缓存结果（不执行） */
    public String replay(String key) {
        return cache.get(key);
    }

    /** 记录 agent 结果：output 落文件 + journal 追加行（原子追加语义由 JSONL 单行保证） */
    public synchronized void recordAgent(String key, String output) {
        try {
            Files.createDirectories(outputsDir);
            String file = key + ".txt";
            Path outputFile = outputsDir.resolve(file);
            Files.writeString(outputFile, output == null ? "" : output);
            appendEntry(new JournalEntry("agent", key, file, null, Instant.now()));
            cache.put(key, output);
        }
        catch (IOException e) {
            log.warn("[Workflow] agent 结果记录失败: key={}: {}", key, e.getMessage());
        }
    }

    /** 进度事件（phase / log） */
    public synchronized void log(String type, String message) {
        try {
            appendEntry(new JournalEntry(type, null, null, message, Instant.now()));
        }
        catch (IOException e) {
            log.warn("[Workflow] journal 写入失败: {}", e.getMessage());
        }
    }

    /** 最终快照（runId, status, finishedAt） */
    public void snapshot(String workflowName, String status) {
        try {
            Path tmp = runDir.resolve("snapshot.tmp");
            Map<String, String> snapshot = Map.of(
                    "workflow", workflowName,
                    "status", status,
                    "finishedAt", Instant.now().toString());
            Files.writeString(tmp, JsonUtils.toJson(snapshot));
            Files.move(tmp, runDir.resolve("snapshot.json"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            log.warn("[Workflow] 快照写入失败: {}", e.getMessage());
        }
    }

    private void appendEntry(JournalEntry entry) throws IOException {
        Files.writeString(journalFile, JsonUtils.toJson(entry) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
