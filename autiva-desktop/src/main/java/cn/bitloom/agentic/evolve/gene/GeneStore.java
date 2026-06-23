package cn.bitloom.agentic.evolve.gene;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.repository.GeneRepository;
import cn.bitloom.agentic.evolve.solidify.EvolutionEvent;
import cn.bitloom.exception.EvolveException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Slf4j
@Component
public class GeneStore {

    private final EvolveConfig config;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final GeneRepository geneRepository;

    public GeneStore(EvolveConfig config, GeneRepository geneRepository) {
        this.config = config;
        this.geneRepository = geneRepository;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(config.getEvolveDir());
            Files.createDirectories(config.getGenesDir());
            Files.createDirectories(config.getMemoryDir());
            Files.createDirectories(config.getExecutionsDir());

            if (!Files.exists(config.getGenesFile())) {
                String seed = readClasspathResource("evolve/genes.seed.json");
                if (seed != null) {
                    Files.writeString(config.getGenesFile(), seed, StandardCharsets.UTF_8);
                    log.info("[Evolve] 已从种子文件初始化基因库");
                } else {
                    Files.writeString(config.getGenesFile(), "[]", StandardCharsets.UTF_8);
                }
            }

            if (!Files.exists(config.getCapsulesFile())) {
                Files.writeString(config.getCapsulesFile(), "[]", StandardCharsets.UTF_8);
            }
            if (!Files.exists(config.getEventsFile())) {
                Files.writeString(config.getEventsFile(), "", StandardCharsets.UTF_8);
            }
            if (!Files.exists(config.getCandidatesFile())) {
                Files.writeString(config.getCandidatesFile(), "", StandardCharsets.UTF_8);
            }
            if (!Files.exists(config.getRoutingFile())) {
                Files.writeString(config.getRoutingFile(), "[]", StandardCharsets.UTF_8);
            }
            if (!Files.exists(config.getMemoryRulesFile())) {
                Files.writeString(config.getMemoryRulesFile(), "", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("[Evolve] 初始化存储失败", e);
        }
    }

    public List<Gene> loadGenes() {
        lock.readLock().lock();
        try {
            List<Gene> fromDir = loadGenesFromDir();
            if (!fromDir.isEmpty()) {
                return fromDir;
            }
            String json = Files.readString(config.getGenesFile(), StandardCharsets.UTF_8);
            List<Gene> genes = mapper.readValue(json, new TypeReference<>() {});
            return genes != null ? genes : Collections.emptyList();
        } catch (IOException e) {
            log.error("[Evolve] 加载基因失败", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Gene> loadEnabledGenes() {
        return loadGenes().stream().filter(Gene::enabled).toList();
    }

    public List<Capsule> loadCapsules() {
        lock.readLock().lock();
        try {
            String json = Files.readString(config.getCapsulesFile(), StandardCharsets.UTF_8);
            List<Capsule> capsules = mapper.readValue(json, new TypeReference<>() {});
            return capsules != null ? capsules : Collections.emptyList();
        } catch (IOException e) {
            log.error("[Evolve] 加载胶囊失败", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void upsertGene(Gene gene) {
        lock.writeLock().lock();
        try {
            saveGeneToDir(gene);
            List<Gene> genes = new ArrayList<>(loadGenesInternal());
            genes.removeIf(g -> g.id().equals(gene.id()));
            genes.add(gene);
            writeGenes(genes);
            geneRepository.commit(gene, "gene update: " + gene.id() + " v" + gene.version());
            log.info("[Evolve] 基因已更新: {} (v{})", gene.id(), gene.version());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteGene(String geneId) {
        lock.writeLock().lock();
        try {
            List<Gene> genes = new ArrayList<>(loadGenesInternal());
            genes.removeIf(g -> g.id().equals(geneId));
            writeGenes(genes);
            deleteGeneDir(geneId);
            log.info("[Evolve] 基因已删除: {}", geneId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void toggleGene(String geneId, boolean enabled) {
        lock.writeLock().lock();
        try {
            List<Gene> genes = new ArrayList<>(loadGenesInternal());
            List<Gene> updated = genes.stream()
                    .map(g -> g.id().equals(geneId) ? g.withEnabled(enabled) : g)
                    .toList();
            writeGenes(updated);
            log.info("[Evolve] 基因 {} 已{}", geneId, enabled ? "启用" : "禁用");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteCapsule(String capsuleId) {
        lock.writeLock().lock();
        try {
            List<Capsule> capsules = new ArrayList<>(loadCapsulesInternal());
            capsules.removeIf(c -> c.id().equals(capsuleId));
            writeCapsules(capsules);
            log.info("[Evolve] 胶囊已删除: {}", capsuleId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void appendEvent(EvolutionEvent event) {
        lock.writeLock().lock();
        try {
            String line = mapper.writeValueAsString(event) + "\n";
            Files.writeString(config.getEventsFile(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[Evolve] 写入事件失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<EvolutionEvent> readRecentEvents(int limit) {
        lock.readLock().lock();
        try {
            if (!Files.exists(config.getEventsFile())) {
                return Collections.emptyList();
            }
            List<String> lines = Files.readAllLines(config.getEventsFile(), StandardCharsets.UTF_8);
            List<EvolutionEvent> events = new ArrayList<>();
            int start = Math.max(0, lines.size() - limit);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                try {
                    events.add(mapper.readValue(line, EvolutionEvent.class));
                } catch (JsonProcessingException e) {
                    log.warn("[Evolve] 解析事件行失败: {}", line, e);
                }
            }
            return events;
        } catch (IOException e) {
            log.error("[Evolve] 读取事件失败", e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public String loadGeneCode(String geneId) {
        Path codeFile = config.getGenesDir().resolve(geneId).resolve("impl.java");
        try {
            if (Files.exists(codeFile)) {
                return Files.readString(codeFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[Evolve] 加载基因代码失败: {}", geneId, e);
        }
        return null;
    }

    public void saveGeneCode(String geneId, String code) {
        try {
            Path geneDir = config.getGenesDir().resolve(geneId);
            Files.createDirectories(geneDir);
            Files.writeString(geneDir.resolve("impl.java"), code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Evolve] 保存基因代码失败: {}", geneId, e);
        }
    }

    public List<GeneRepository.CommitInfo> getGeneHistory(String geneId) {
        return geneRepository.history(geneId);
    }

    public void revertGene(String geneId, String commitHash) {
        geneRepository.revert(geneId, commitHash);
        log.info("[Evolve] 基因已回滚: {} -> {}", geneId, commitHash);
    }

    public String diffGene(String geneId, String fromCommit, String toCommit) {
        return geneRepository.diff(geneId, fromCommit, toCommit);
    }

    private List<Gene> loadGenesFromDir() {
        if (!Files.exists(config.getGenesDir())) {
            return Collections.emptyList();
        }
        try (Stream<Path> dirs = Files.list(config.getGenesDir())) {
            return dirs
                    .filter(Files::isDirectory)
                    .map(this::loadGeneFromDir)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.warn("[Evolve] 扫描基因目录失败", e);
            return Collections.emptyList();
        }
    }

    private Gene loadGeneFromDir(Path geneDir) {
        Path metaFile = geneDir.resolve("gene.json");
        if (!Files.exists(metaFile)) {
            return null;
        }
        try {
            String json = Files.readString(metaFile, StandardCharsets.UTF_8);
            Gene gene = mapper.readValue(json, Gene.class);
            String code = loadGeneCode(gene.id());
            if (code != null && gene.code() == null) {
                return gene.withCode(code);
            }
            return gene;
        } catch (IOException e) {
            log.warn("[Evolve] 加载基因目录失败: {}", geneDir, e);
            return null;
        }
    }

    private void saveGeneToDir(Gene gene) {
        try {
            Path geneDir = config.getGenesDir().resolve(gene.id());
            Files.createDirectories(geneDir);
            Files.createDirectories(geneDir.resolve("versions"));

            String metaJson = mapper.writeValueAsString(gene);
            Files.writeString(geneDir.resolve("gene.json"), metaJson, StandardCharsets.UTF_8);

            if (gene.code() != null && !gene.code().isEmpty()) {
                Files.writeString(geneDir.resolve("impl.java"), gene.code(), StandardCharsets.UTF_8);
            }

            Path versionFile = geneDir.resolve("versions").resolve("v" + gene.version() + ".json");
            if (!Files.exists(versionFile)) {
                Files.writeString(versionFile, metaJson, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("[Evolve] 保存基因到目录失败: {}", gene.id(), e);
        }
    }

    private void deleteGeneDir(String geneId) {
        try {
            Path geneDir = config.getGenesDir().resolve(geneId);
            if (Files.exists(geneDir)) {
                try (Stream<Path> walk = Files.walk(geneDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            log.warn("[Evolve] 删除基因目录失败: {}", geneId, e);
        }
    }

    private List<Gene> loadGenesInternal() {
        try {
            String json = Files.readString(config.getGenesFile(), StandardCharsets.UTF_8);
            List<Gene> genes = mapper.readValue(json, new TypeReference<>() {});
            return genes != null ? genes : Collections.emptyList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<Capsule> loadCapsulesInternal() {
        try {
            String json = Files.readString(config.getCapsulesFile(), StandardCharsets.UTF_8);
            List<Capsule> capsules = mapper.readValue(json, new TypeReference<>() {});
            return capsules != null ? capsules : Collections.emptyList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private void writeGenes(List<Gene> genes) {
        try {
            String json = mapper.writeValueAsString(genes);
            Files.writeString(config.getGenesFile(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Evolve] 写入基因失败", e);
            throw EvolveException.storageError("写入基因失败", e);
        }
    }

    private void writeCapsules(List<Capsule> capsules) {
        try {
            String json = mapper.writeValueAsString(capsules);
            Files.writeString(config.getCapsulesFile(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Evolve] 写入胶囊失败", e);
            throw EvolveException.storageError("写入胶囊失败", e);
        }
    }

    private String readClasspathResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[Evolve] 读取classpath资源失败: {}", path);
        }
        return null;
    }
}
