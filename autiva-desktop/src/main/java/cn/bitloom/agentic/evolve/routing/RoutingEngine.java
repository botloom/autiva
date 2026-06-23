package cn.bitloom.agentic.evolve.routing;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.experience.Experience;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RoutingEngine {

    private final Path routingFile;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<RoutingEntry> entries;

    public RoutingEngine(EvolveConfig config) {
        this.routingFile = config.getRoutingFile();
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    public Optional<String> route(String intent) {
        if (intent == null || intent.isEmpty()) {
            return Optional.empty();
        }

        lock.readLock().lock();
        try {
            return entries.stream()
                    .filter(e -> matchesPattern(intent, e.pattern()))
                    .max(Comparator.comparingDouble(RoutingEntry::weight))
                    .map(RoutingEntry::geneId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateFromExperience(Experience experience) {
        RoutingEntry entry = new RoutingEntry(
                experience.pattern(),
                experience.targetId(),
                experience.confidence(),
                System.currentTimeMillis(),
                "experience"
        );
        addEntry(entry);
    }

    public void addRoute(String pattern, String geneId, double weight) {
        RoutingEntry entry = new RoutingEntry(pattern, geneId, weight,
                System.currentTimeMillis(), "manual");
        addEntry(entry);
    }

    public void removeRoute(String pattern) {
        lock.writeLock().lock();
        try {
            entries.removeIf(e -> e.pattern().equals(pattern));
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<RoutingEntry> listRoutes() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(entries);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void addEntry(RoutingEntry entry) {
        lock.writeLock().lock();
        try {
            entries.removeIf(e -> e.pattern().equals(entry.pattern()) && e.geneId().equals(entry.geneId()));
            entries.add(entry);
            save();
            log.info("[Evolve] 路由已更新: pattern={}, gene={}, weight={}",
                    entry.pattern(), entry.geneId(), entry.weight());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean matchesPattern(String intent, String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(intent).find();
        } catch (Exception e) {
            return intent.toLowerCase().contains(pattern.toLowerCase());
        }
    }

    private void load() {
        lock.writeLock().lock();
        try {
            if (Files.exists(routingFile)) {
                String json = Files.readString(routingFile, StandardCharsets.UTF_8);
                entries = mapper.readValue(json, new TypeReference<>() {});
            }
            if (entries == null) {
                entries = new ArrayList<>();
            }
        } catch (IOException e) {
            log.warn("[Evolve] 加载路由表失败", e);
            entries = new ArrayList<>();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void save() {
        try {
            String json = mapper.writeValueAsString(entries);
            Files.writeString(routingFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Evolve] 保存路由表失败", e);
        }
    }
}
