package cn.bitloom.agentic.evolve.memory;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.experience.Experience;
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
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class MemoryEngine {

    private final Path memoryRulesFile;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MemoryEngine(EvolveConfig config) {
        this.memoryRulesFile = config.getMemoryRulesFile();
        this.mapper = new ObjectMapper();
    }

    public void addRuleFromExperience(Experience experience) {
        MemoryRule rule = new MemoryRule(
                "rule_" + UUID.randomUUID().toString().substring(0, 8),
                experience.pattern(),
                experience.fix(),
                experience.confidence(),
                0,
                System.currentTimeMillis(),
                "experience"
        );
        appendRule(rule);
        log.info("[Evolve] 从经验添加规则: {} -> {}", rule.pattern(), rule.action());
    }

    public void addManualRule(String pattern, String action, double confidence) {
        MemoryRule rule = new MemoryRule(
                "rule_" + UUID.randomUUID().toString().substring(0, 8),
                pattern,
                action,
                confidence,
                0,
                System.currentTimeMillis(),
                "manual"
        );
        appendRule(rule);
    }

    public List<MemoryRule> queryRules(String context) {
        List<MemoryRule> allRules = loadAllRules();
        if (context == null || context.isEmpty()) {
            return allRules;
        }
        return allRules.stream()
                .filter(r -> context.toLowerCase().contains(r.pattern().toLowerCase()))
                .sorted(Comparator.comparingDouble(MemoryRule::confidence).reversed())
                .toList();
    }

    public void hitRule(String ruleId) {
        lock.writeLock().lock();
        try {
            List<MemoryRule> rules = new ArrayList<>(loadAllRules());
            for (int i = 0; i < rules.size(); i++) {
                MemoryRule r = rules.get(i);
                if (r.id().equals(ruleId)) {
                    rules.set(i, r.withHitCount(r.hitCount() + 1));
                    break;
                }
            }
            rewriteRules(rules);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteRule(String ruleId) {
        lock.writeLock().lock();
        try {
            List<MemoryRule> rules = new ArrayList<>(loadAllRules());
            rules.removeIf(r -> r.id().equals(ruleId));
            rewriteRules(rules);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MemoryRule> loadAllRules() {
        lock.readLock().lock();
        try {
            if (!Files.exists(memoryRulesFile)) {
                return Collections.emptyList();
            }
            List<String> lines = Files.readAllLines(memoryRulesFile, StandardCharsets.UTF_8);
            List<MemoryRule> rules = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    rules.add(mapper.readValue(trimmed, MemoryRule.class));
                } catch (JsonProcessingException e) {
                    log.warn("[Evolve] 解析规则行失败: {}", trimmed, e);
                }
            }
            return rules;
        } catch (IOException e) {
            log.error("[Evolve] 加载规则失败", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void appendRule(MemoryRule rule) {
        lock.writeLock().lock();
        try {
            String line = mapper.writeValueAsString(rule) + "\n";
            Files.writeString(memoryRulesFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[Evolve] 写入规则失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void rewriteRules(List<MemoryRule> rules) {
        try {
            StringBuilder sb = new StringBuilder();
            for (MemoryRule rule : rules) {
                sb.append(mapper.writeValueAsString(rule)).append("\n");
            }
            Files.writeString(memoryRulesFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Evolve] 重写规则失败", e);
        }
    }
}
