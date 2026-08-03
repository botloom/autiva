package cn.bitloom.agentic.evolve.gene;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 基因仓库，负责基因的持久化加载、版本管理与元数据更新。
 * <p>
 * 参考 {@code SkillManager} 的目录扫描模式，每个基因对应 GENES_DIR 下的一个子目录，
 * 目录中包含 gene.md（YAML frontmatter + Markdown body）和可选的 versions/ 历史版本目录。
 */
@Slf4j
@Component
public class GeneRepository {

    private static final String GENE_FILE_NAME = "gene.md";
    private static final String FRONTMATTER_SEPARATOR = "---";

    private final Map<String, Gene> genes = new ConcurrentHashMap<>();

    /**
     * 启动时加载所有基因。
     */
    @PostConstruct
    public void loadAll() {
        genes.clear();
        Path genesDir = AppConstants.Evolve.GENES_DIR;

        if (!Files.exists(genesDir)) {
            try {
                Files.createDirectories(genesDir);
            } catch (IOException e) {
                log.error("创建基因目录失败: {}", genesDir, e);
                return;
            }
            return;
        }

        if (!Files.isDirectory(genesDir)) {
            log.error("基因路径不是目录: {}", genesDir);
            return;
        }

        try (Stream<Path> paths = Files.list(genesDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(this::loadGeneFromDir);
        } catch (IOException e) {
            log.error("遍历基因目录失败: {}", genesDir, e);
        }

        log.info("已加载 {} 个基因", genes.size());
    }

    private void loadGeneFromDir(Path geneDir) {
        Path geneFile = geneDir.resolve(GENE_FILE_NAME);
        if (!Files.exists(geneFile)) {
            return;
        }
        try {
            String markdown = Files.readString(geneFile, StandardCharsets.UTF_8);
            markdown = markdown.replaceAll("[\\uFEFF\\u200B\\u200C\\u200D]", "");
            String id = geneDir.getFileName().toString();
            Gene gene = parseGene(id, geneDir.toString(), markdown);
            if (gene != null) {
                genes.put(id, gene);
            }
        } catch (IOException e) {
            log.error("读取基因文件失败: {}", geneFile, e);
        }
    }

    /**
     * 解析 gene.md：分离 YAML frontmatter 和 Markdown body。
     * 使用 SnakeYAML 解析 frontmatter（支持嵌套结构），失败时回退到简单 key:value 解析。
     */
    Gene parseGene(String id, String basePath, String markdown) {
        String content = "";
        Map<String, Object> frontmatter = new LinkedHashMap<>();

        if (markdown.startsWith(FRONTMATTER_SEPARATOR)) {
            int endIndex = markdown.indexOf(FRONTMATTER_SEPARATOR, 3);
            if (endIndex != -1) {
                String frontMatterSection = markdown.substring(3, endIndex).trim();
                content = markdown.substring(endIndex + 3).trim();
                try {
                    Yaml yaml = new Yaml();
                    Object loaded = yaml.load(frontMatterSection);
                    if (loaded instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            frontmatter.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析基因 [{}] frontmatter 失败，回退到简单解析: {}", id, e.getMessage());
                    frontmatter.putAll(parseSimpleFrontmatter(frontMatterSection));
                }
            } else {
                content = markdown;
            }
        } else {
            content = markdown;
        }

        return new Gene(id, basePath, frontmatter, content);
    }

    /**
     * 简单 key:value 解析（SnakeYAML 失败时的回退）。
     */
    private Map<String, Object> parseSimpleFrontmatter(String section) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if (value.length() >= 2 &&
                        ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return result;
    }

    public List<Gene> findAll() {
        return List.copyOf(genes.values());
    }

    public Gene findById(String id) {
        return genes.get(id);
    }

    public Gene findByName(String name) {
        if (name == null) {
            return null;
        }
        return genes.values().stream()
                .filter(g -> name.equals(g.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 保存基因。如果已存在，先将旧版本备份到 versions/v{n}.md。
     */
    public void save(Gene gene) {
        String id = gene.id();
        Path geneDir = AppConstants.Evolve.geneDir(id);
        Path geneFile = AppConstants.Evolve.geneFile(id);

        try {
            Files.createDirectories(geneDir);

            if (Files.exists(geneFile)) {
                Gene existing = genes.get(id);
                int oldVersion = existing != null ? existing.version() : 1;
                backupVersion(id, geneFile, oldVersion);
            }

            String fileContent = buildGeneContent(gene);
            Files.writeString(geneFile, fileContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Gene saved = new Gene(id, geneDir.toString(), gene.frontmatter(), gene.content());
            genes.put(id, saved);
        } catch (IOException e) {
            log.error("保存基因失败: {}", id, e);
            throw StorageException.writeError("gene-" + id, e);
        }
    }

    private void backupVersion(String id, Path geneFile, int version) {
        Path versionsDir = AppConstants.Evolve.geneVersionsDir(id);
        try {
            Files.createDirectories(versionsDir);
            Path backup = versionsDir.resolve("v" + version + ".md");
            Files.copy(geneFile, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("备份基因版本失败: id={}, v={}", id, version, e);
        }
    }

    /**
     * 删除基因目录及缓存。
     */
    public void delete(String id) {
        Path geneDir = AppConstants.Evolve.geneDir(id);
        try {
            if (Files.exists(geneDir)) {
                deleteDirectory(geneDir);
            }
            genes.remove(id);
        } catch (IOException e) {
            log.error("删除基因失败: {}", id, e);
            throw StorageException.writeError("gene-" + id, e);
        }
    }

    /**
     * 调整表观遗传增强因子。
     */
    public void updateBoost(String id, double factor) {
        Gene gene = genes.get(id);
        if (gene == null) {
            throw StorageException.readError("gene-" + id);
        }
        Map<String, Object> fm = new LinkedHashMap<>(gene.frontmatter());
        fm.put("epigeneticBoost", factor);
        Gene updated = new Gene(id, gene.basePath(), fm, gene.content());
        writeGeneFile(updated);
        genes.put(id, updated);
    }

    /**
     * 成功计数 +1。
     */
    public void incrementSuccess(String id) {
        Gene gene = genes.get(id);
        if (gene == null) {
            throw StorageException.readError("gene-" + id);
        }
        Map<String, Object> fm = new LinkedHashMap<>(gene.frontmatter());
        fm.put("successCount", gene.successCount() + 1);
        Gene updated = new Gene(id, gene.basePath(), fm, gene.content());
        writeGeneFile(updated);
        genes.put(id, updated);
    }

    /**
     * 失败计数 +1。
     */
    public void incrementFailure(String id) {
        Gene gene = genes.get(id);
        if (gene == null) {
            throw StorageException.readError("gene-" + id);
        }
        Map<String, Object> fm = new LinkedHashMap<>(gene.frontmatter());
        fm.put("failureCount", gene.failureCount() + 1);
        Gene updated = new Gene(id, gene.basePath(), fm, gene.content());
        writeGeneFile(updated);
        genes.put(id, updated);
    }

    /**
     * 直接写入基因文件（元数据更新，不产生版本备份）。
     */
    private void writeGeneFile(Gene gene) {
        Path geneFile = AppConstants.Evolve.geneFile(gene.id());
        try {
            Files.createDirectories(geneFile.getParent());
            String fileContent = buildGeneContent(gene);
            Files.writeString(geneFile, fileContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("写入基因文件失败: {}", gene.id(), e);
            throw StorageException.writeError("gene-" + gene.id(), e);
        }
    }

    /**
     * 列出基因的历史版本号。
     */
    public List<Integer> getVersions(String id) {
        Path versionsDir = AppConstants.Evolve.geneVersionsDir(id);
        if (!Files.exists(versionsDir)) {
            return List.of();
        }
        List<Integer> versions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(versionsDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        if (fileName.startsWith("v") && fileName.endsWith(".md")) {
                            try {
                                int v = Integer.parseInt(fileName.substring(1, fileName.length() - 3));
                                versions.add(v);
                            } catch (NumberFormatException ignored) {
                                // 忽略不符合命名规范的文件
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("列出基因版本失败: {}", id, e);
        }
        Collections.sort(versions);
        return versions;
    }

    /**
     * 回滚到指定版本：备份当前版本，将指定版本复制回 gene.md 并重新加载。
     */
    public void rollback(String id, int version) {
        Path versionsDir = AppConstants.Evolve.geneVersionsDir(id);
        Path backupFile = versionsDir.resolve("v" + version + ".md");
        if (!Files.exists(backupFile)) {
            throw StorageException.readError(backupFile.toString());
        }

        Path geneFile = AppConstants.Evolve.geneFile(id);
        try {
            if (Files.exists(geneFile)) {
                Gene existing = genes.get(id);
                int currentVersion = existing != null ? existing.version() : 1;
                backupVersion(id, geneFile, currentVersion);
            }

            Files.copy(backupFile, geneFile, StandardCopyOption.REPLACE_EXISTING);

            String markdown = Files.readString(geneFile, StandardCharsets.UTF_8);
            markdown = markdown.replaceAll("[\\uFEFF\\u200B\\u200C\\u200D]", "");
            Gene restored = parseGene(id, AppConstants.Evolve.geneDir(id).toString(), markdown);
            if (restored != null) {
                genes.put(id, restored);
            }
        } catch (IOException e) {
            log.error("回滚基因失败: id={}, v={}", id, version, e);
            throw StorageException.writeError("gene-" + id + "-rollback", e);
        }
    }

    /**
     * 将 Gene 序列化为 gene.md 文件内容（YAML frontmatter + Markdown body）。
     */
    private String buildGeneContent(Gene gene) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        String frontMatterYaml = yaml.dump(gene.frontmatter());

        StringBuilder sb = new StringBuilder();
        sb.append(FRONTMATTER_SEPARATOR).append("\n");
        sb.append(frontMatterYaml);
        sb.append(FRONTMATTER_SEPARATOR).append("\n");
        sb.append(gene.content() != null ? gene.content() : "");
        return sb.toString();
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.error("删除失败: {}", p, e);
                        }
                    });
        }
    }
}
