package cn.bitloom.agentic.evolve.trajectory;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 轨迹仓库 — 以 NDJSON 格式持久化轨迹到文件系统。
 * <p>
 * 文件路径：{@code AppConstants.Evolve.EXECUTIONS_DIR/{date}.jsonl}，
 * 每行一个 JSON 对象，date 格式为 {@code yyyy-MM-dd}（基于轨迹 startTime 的 UTC 日期）。
 */
@Slf4j
public class TrajectoryRepository {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 保存轨迹，追加写入当天文件。
     */
    public void save(Trajectory trajectory) {
        try {
            Path dir = AppConstants.Evolve.EXECUTIONS_DIR;
            Files.createDirectories(dir);

            String date = formatDate(trajectory.startTime());
            Path file = AppConstants.Evolve.executionLogFile(date);
            String line = JsonUtils.toJson(trajectory) + System.lineSeparator();

            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[TrajectoryRepository] 保存轨迹失败: trajectoryId={}", trajectory.id(), e);
            throw new RuntimeException("保存轨迹失败", e);
        }
    }

    /**
     * 按时间范围查询轨迹。
     */
    public List<Trajectory> findByDateRange(Instant from, Instant to) {
        List<Trajectory> results = new ArrayList<>();
        LocalDate startDate = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate endDate = LocalDate.ofInstant(to, ZoneOffset.UTC);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Path file = AppConstants.Evolve.executionLogFile(date.format(DATE_FORMAT));
            if (Files.exists(file)) {
                readTrajectoriesFromFile(file, results);
            }
        }

        // 按精确时间范围过滤
        return results.stream()
                .filter(t -> !t.startTime().isBefore(from) && !t.startTime().isAfter(to))
                .toList();
    }

    /**
     * 按会话 ID 查询轨迹（全扫描过滤）。
     */
    public List<Trajectory> findBySessionId(String sessionId) {
        List<Trajectory> all = loadAll();
        return all.stream()
                .filter(t -> sessionId.equals(t.sessionId()))
                .toList();
    }

    /**
     * 按智能体名称查询轨迹（全扫描过滤）。
     */
    public List<Trajectory> findByAgentName(String agentName) {
        List<Trajectory> all = loadAll();
        return all.stream()
                .filter(t -> agentName.equals(t.agentName()))
                .toList();
    }

    /**
     * 按结果类型查询轨迹（全扫描过滤）。
     */
    public List<Trajectory> findByOutcome(TrajectoryOutcome outcome) {
        List<Trajectory> all = loadAll();
        return all.stream()
                .filter(t -> outcome.equals(t.outcome()))
                .toList();
    }

    // ===================== 内部方法 =====================

    /**
     * 加载所有轨迹文件中的轨迹。
     */
    private List<Trajectory> loadAll() {
        List<Trajectory> results = new ArrayList<>();
        Path dir = AppConstants.Evolve.EXECUTIONS_DIR;
        if (!Files.exists(dir)) {
            return results;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".jsonl"))
                    .forEach(p -> readTrajectoriesFromFile(p, results));
        } catch (IOException e) {
            log.error("[TrajectoryRepository] 扫描执行日志目录失败", e);
        }

        return results;
    }

    /**
     * 从单个 NDJSON 文件中读取所有轨迹。
     */
    private void readTrajectoriesFromFile(Path file, List<Trajectory> results) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    Trajectory trajectory = JsonUtils.fromJson(line, Trajectory.class);
                    results.add(trajectory);
                } catch (Exception e) {
                    String preview = line.length() > 100 ? line.substring(0, 100) + "..." : line;
                    log.warn("[TrajectoryRepository] 解析轨迹行失败: {}", preview);
                }
            }
        } catch (IOException e) {
            log.error("[TrajectoryRepository] 读取轨迹文件失败: {}", file, e);
        }
    }

    /**
     * 将 Instant 格式化为 UTC 日期字符串。
     */
    private String formatDate(Instant instant) {
        if (instant == null) {
            instant = Instant.now();
        }
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).format(DATE_FORMAT);
    }
}
