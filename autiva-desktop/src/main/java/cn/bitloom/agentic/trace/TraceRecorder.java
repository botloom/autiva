package cn.bitloom.agentic.trace;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Trace 持久化管理。
 *
 * <p>存储格式：JSONL，按日期分目录，每个会话一个文件。</p>
 *
 * <pre>
 * ~/.autiva/evolve/traces/
 *   2026-07-20/
 *     session_abc123.jsonl
 *     session_def456.jsonl
 * </pre>
 *
 * <p>每行一条 Trace JSON。L4 爬山引擎通过 {@link #loadRecent} 加载历史 Trace 分析。</p>
 */
@Slf4j
@Component
public class TraceRecorder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EvolveConfig config;
    private final ObjectMapper mapper;
    private final Path tracesRoot;

    public TraceRecorder(EvolveConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        // JSONL 格式要求每条 Trace 单行，禁用 INDENT_OUTPUT
        this.tracesRoot = config.getEvolveDir().resolve("traces");
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(tracesRoot);
        } catch (IOException e) {
            log.warn("[Trace] 初始化 traces 目录失败: {}", tracesRoot, e);
        }
    }

    /**
     * 落盘一条 Trace。
     */
    public void record(Trace trace) {
        if (trace == null || trace.sessionId() == null) {
            return;
        }
        try {
            Path file = resolveTraceFile(trace.sessionId());
            String json = mapper.writeValueAsString(trace);
            Files.writeString(file, json + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("[Trace] 已记录 traceId={} sessionId={} verified={}",
                    trace.traceId(), trace.sessionId(), trace.verified());
        } catch (IOException e) {
            log.warn("[Trace] 写入失败 sessionId={}: {}", trace.sessionId(), e.getMessage());
        }
    }

    /**
     * 加载指定 Agent 最近 N 条 Trace（按时间倒序）。
     */
    public List<Trace> loadRecent(String agentId, int limit) {
        List<Trace> all = new ArrayList<>();
        try (Stream<Path> dateDirs = Files.list(tracesRoot)
                .filter(Files::isDirectory)
                .sorted(java.util.Comparator.reverseOrder())) {
            for (Path dateDir : (Iterable<Path>) dateDirs::iterator) {
                if (all.size() >= limit) break;
                try (Stream<Path> files = Files.list(dateDir).filter(Files::isRegularFile)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (all.size() >= limit) break;
                        readTracesFromFile(file).stream()
                                .filter(t -> agentId == null || agentId.equals(t.agentId()))
                                .forEach(all::add);
                    }
                } catch (IOException e) {
                    log.warn("[Trace] 列出目录失败: {}", dateDir, e);
                }
            }
        } catch (IOException e) {
            log.warn("[Trace] 遍历 traces 根目录失败", e);
        }
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    /**
     * 加载指定会话的所有 Trace。
     */
    public List<Trace> loadBySession(String sessionId) {
        Path file = resolveTraceFile(sessionId);
        return readTracesFromFile(file);
    }

    /**
     * 统计指定 Agent 最近 N 条 Trace 的校验通过率。
     */
    public VerificationStats stats(String agentId, int recentLimit) {
        List<Trace> traces = loadRecent(agentId, recentLimit);
        if (traces.isEmpty()) {
            return new VerificationStats(0, 0, 0, 0.0, 0, 0);
        }
        int verified = (int) traces.stream().filter(Trace::verified).count();
        int failed = traces.size() - verified;
        int totalToolCalls = traces.stream()
                .mapToInt(t -> t.toolCalls() != null ? t.toolCalls().size() : 0)
                .sum();
        int blockedToolCalls = (int) traces.stream()
                .flatMap(t -> t.toolCalls() != null ? t.toolCalls().stream() : java.util.stream.Stream.empty())
                .filter(ToolCallRecord::blocked)
                .count();
        double passRate = (double) verified / traces.size();
        return new VerificationStats(traces.size(), verified, failed, passRate, totalToolCalls, blockedToolCalls);
    }

    /**
     * 清理 N 天前的 Trace 文件。
     */
    public int cleanupOldTraces(int retentionDays) {
        if (retentionDays <= 0) return 0;
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        int[] deleted = {0};
        try (Stream<Path> dateDirs = Files.list(tracesRoot).filter(Files::isDirectory)) {
            for (Path dateDir : (Iterable<Path>) dateDirs::iterator) {
                try {
                    LocalDate dirDate = LocalDate.parse(dateDir.getFileName().toString(), DATE_FMT);
                    if (dirDate.isBefore(cutoff)) {
                        try (Stream<Path> walk = Files.walk(dateDir)) {
                            walk.sorted(java.util.Comparator.reverseOrder())
                                    .map(Path::toFile)
                                    .forEach(f -> {
                                        if (f.delete()) deleted[0]++;
                                    });
                        }
                    }
                } catch (Exception ignored) {
                    // 非日期目录跳过
                }
            }
        } catch (IOException e) {
            log.warn("[Trace] 清理旧 Trace 失败", e);
        }
        if (deleted[0] > 0) {
            log.info("[Trace] 已清理 {} 个旧 Trace 文件（>{} 天）", deleted[0], retentionDays);
        }
        return deleted[0];
    }

    private Path resolveTraceFile(String sessionId) {
        String date = LocalDate.now().format(DATE_FMT);
        return tracesRoot.resolve(date).resolve(sessionId + ".jsonl");
    }

    private List<Trace> readTracesFromFile(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Trace> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    result.add(mapper.readValue(line, Trace.class));
                } catch (Exception e) {
                    log.warn("[Trace] 解析行失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("[Trace] 读取文件失败: {}", file, e);
        }
        return result;
    }

    /**
     * 校验统计结果。
     */
    public record VerificationStats(
            int totalTraces,
            int passedTraces,
            int failedTraces,
            double passRate,
            int totalToolCalls,
            int blockedToolCalls
    ) {}
}
