package cn.bitloom.agentic.evolve.execution;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class ExecutionRecorder {

    private final EvolveConfig config;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ExecutionRecorder(EvolveConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(config.getExecutionsDir());
        } catch (IOException e) {
            log.error("[Evolve] 初始化执行日志目录失败", e);
        }
    }

    public void record(ExecutionLog executionLog) {
        lock.writeLock().lock();
        try {
            Path logFile = getTodayLogFile();
            String line = mapper.writeValueAsString(executionLog) + "\n";
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[Evolve] 写入执行日志失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<ExecutionLog> readRecentLogs(int limit) {
        lock.readLock().lock();
        try {
            Path logFile = getTodayLogFile();
            if (!Files.exists(logFile)) {
                return Collections.emptyList();
            }
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            List<ExecutionLog> logs = new ArrayList<>();
            int start = Math.max(0, lines.size() - limit);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                try {
                    logs.add(mapper.readValue(line, ExecutionLog.class));
                } catch (JsonProcessingException e) {
                    log.warn("[Evolve] 解析执行日志行失败: {}", line, e);
                }
            }
            return logs;
        } catch (IOException e) {
            log.error("[Evolve] 读取执行日志失败", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ExecutionLog> readLogsByGene(String geneId, int limit) {
        return readRecentLogs(500).stream()
                .filter(l -> geneId.equals(l.geneId()))
                .limit(limit)
                .toList();
    }

    public List<ExecutionLog> readFailedLogs(int limit) {
        return readRecentLogs(500).stream()
                .filter(l -> !l.success())
                .limit(limit)
                .toList();
    }

    private Path getTodayLogFile() {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return config.getExecutionsDir().resolve(date + ".jsonl");
    }
}
