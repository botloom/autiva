package cn.bitloom.agentic.skill;

import cn.bitloom.agentic.util.MarkdownParser;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class SkillManager {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.loadSkills();
    }

    public void loadSkills() {
        skills.clear();
        List<Skill> loadedSkills = loadDirectory(AppConstants.Base.SKILLS_DIR.toString());
        loadedSkills.forEach(skill -> {
            if (skill.name() != null) {
                skills.put(skill.name(), skill);
            }
        });
    }

    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        skills.forEach((name, skill) -> {
            sb.append("- ").append(name);
            sb.append(": ").append(skill.description());
            sb.append("\n");
        });
        return sb.toString();
    }

    /**
     * 返回紧凑的技能列表（每个 skill 一行，描述截断到 40 字）。
     * 用于上下文注入，减少 token 消耗。对标 Claude Code 的 1% 上下文预算策略。
     */
    public String getCompactListing() {
        if (skills.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        skills.forEach((name, skill) -> {
            sb.append("- ").append(name);
            String desc = skill.description();
            if (desc != null && !desc.isEmpty()) {
                sb.append(": ");
                sb.append(desc.length() > 40 ? desc.substring(0, 40) + "..." : desc);
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    public String getContent(String name) {
        Skill skill = skills.get(name);
        return skill != null ? skill.content() : null;
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public List<Skill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    public List<Skill> loadDirectory(String rootDirectory) {
        Path rootPath = Paths.get(rootDirectory);

        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
                createSampleSkill(rootPath);
            } catch (IOException e) {
                log.error("Failed to create skill directory: {}", rootPath, e);
                return List.of();
            }
        }

        if (!Files.isDirectory(rootPath)) {
            log.error("Path is not a directory: {}", rootPath);
            return List.of();
        }

        List<Skill> result = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(SKILL_FILE_NAME))
                    .forEach(path -> {
                        try {
                            String markdown = Files.readString(path, StandardCharsets.UTF_8);
                            markdown = markdown.replaceAll("[\\uFEFF\\u200B\\u200C\\u200D]", "");
                            MarkdownParser parser = new MarkdownParser(markdown);
                            result.add(new Skill(path.getParent().toString(), parser.getFrontMatter(), parser.getContent()));
                        } catch (IOException e) {
                            log.error("Failed to read SKILL.md file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk root directory: {}", rootPath, e);
        }

        return result;
    }

    public List<Skill> loadDirectories(List<String> rootDirectories) {
        List<Skill> result = new ArrayList<>();
        for (String rootDirectory : rootDirectories) {
            result.addAll(loadDirectory(rootDirectory));
        }
        return result;
    }

    public List<Skill> loadResources(List<Resource> skillsResources) {
        List<Skill> result = new ArrayList<>();
        for (Resource resource : skillsResources) {
            result.addAll(loadResource(resource));
        }
        return result;
    }

    public List<Skill> loadResource(Resource... skillsResources) {
        List<Skill> result = new ArrayList<>();
        for (Resource resource : skillsResources) {
            try {
                String path = resource.getFile().toPath().toAbsolutePath().toString();
                result.addAll(loadDirectory(path));
            } catch (IOException ex) {
                try {
                    result.addAll(loadJarResource(resource));
                } catch (IOException jarEx) {
                    log.error("Failed to load skills from resource: {}", resource, jarEx);
                }
            }
        }
        return result;
    }

    private List<Skill> loadJarResource(Resource resource) throws IOException {
        URL resourceUrl;
        try {
            resourceUrl = resource.getURL();
        } catch (FileNotFoundException ex) {
            if (resource instanceof ClassPathResource classPathResource) {
                return loadFromClasspath(classPathResource.getPath());
            }
            throw ex;
        }

        String protocol = resourceUrl.getProtocol();
        if (!"jar".equals(protocol)) {
            throw new IOException("Unsupported protocol for JAR loading: " + protocol);
        }

        JarURLConnection jarConnection = (JarURLConnection) resourceUrl.openConnection();
        String entryPrefix = jarConnection.getEntryName();
        if (!entryPrefix.endsWith("/")) {
            entryPrefix = entryPrefix + "/";
        }
        return scanJarForSkills(jarConnection.getJarFile(), entryPrefix);
    }

    private List<Skill> loadFromClasspath(String classpathPrefix) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:" + classpathPrefix + "/**/SKILL.md");

        if (resources.length > 0) {
            List<Skill> result = new ArrayList<>();
            for (Resource skillResource : resources) {
                try (InputStream is = skillResource.getInputStream()) {
                    String basePath = deriveBasePathFromUrl(skillResource.getURL());
                    result.add(parseSkill(is, basePath));
                }
            }
            return result;
        }

        return scanClasspathJarsForSkills(classpathPrefix);
    }

    private List<Skill> scanClasspathJarsForSkills(String classpathPrefix) throws IOException {
        String prefix = classpathPrefix.endsWith("/") ? classpathPrefix : classpathPrefix + "/";

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SkillManager.class.getClassLoader();
        }

        List<Skill> result = new ArrayList<>();
        Enumeration<URL> manifests = classLoader.getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            URL manifestUrl = manifests.nextElement();
            if (!"jar".equals(manifestUrl.getProtocol())) {
                continue;
            }
            JarURLConnection jarConnection = (JarURLConnection) manifestUrl.openConnection();
            result.addAll(scanJarForSkills(jarConnection.getJarFile(), prefix));
        }
        return result;
    }

    private List<Skill> scanJarForSkills(JarFile jarFile, String entryPrefix) throws IOException {
        List<Skill> result = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (!entry.isDirectory() && entryName.startsWith(entryPrefix) && entryName.endsWith("/SKILL.md")) {
                try (InputStream is = jarFile.getInputStream(entry)) {
                    result.add(parseSkill(is, entryName));
                }
            }
        }
        return result;
    }

    private Skill parseSkill(InputStream is, String entryPath) throws IOException {
        String markdown = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        MarkdownParser parser = new MarkdownParser(markdown);
        String basePath = entryPath.endsWith("/SKILL.md")
                ? entryPath.substring(0, entryPath.lastIndexOf('/'))
                : entryPath;
        return new Skill(basePath, parser.getFrontMatter(), parser.getContent());
    }

    private String deriveBasePathFromUrl(URL skillUrl) {
        String urlStr = skillUrl.toString();
        String basePath = urlStr.substring(0, urlStr.lastIndexOf("/SKILL.md"));
        if (basePath.contains("!/")) {
            basePath = basePath.substring(basePath.indexOf("!/") + 2);
        }
        return basePath;
    }

    public Skill importSkillFromZip(Path zipPath) throws IOException {
        if (!Files.exists(zipPath)) {
            throw new IOException("ZIP file not found: " + zipPath);
        }

        Path tempDir = Files.createTempDirectory("skill-import-");
        try {
            unzipFile(zipPath, tempDir);

            Path skillDir = findSkillDirectory(tempDir);
            if (skillDir == null) {
                throw new IOException("No valid skill directory found in ZIP. Expected a directory containing SKILL.md");
            }

            Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
            if (!Files.exists(skillFile)) {
                throw new IOException("SKILL.md not found in the ZIP file");
            }

            String markdown = Files.readString(skillFile, StandardCharsets.UTF_8);
            MarkdownParser parser = new MarkdownParser(markdown);
            Map<String, Object> frontMatter = parser.getFrontMatter();

            String name = frontMatter.get("name") != null ? frontMatter.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                throw new IOException("Invalid SKILL.md format. 'name' field is required");
            }

            Skill parsedSkill = new Skill(skillDir.toString(), frontMatter, parser.getContent());

            Path targetDir = AppConstants.Base.SKILLS_DIR.resolve(name);
            if (Files.exists(targetDir)) {
                deleteDirectory(targetDir);
            }

            copyDirectory(skillDir, targetDir);

            return new Skill(targetDir.toString(), frontMatter, parser.getContent());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    public Skill importSkillFromUrl(String url) throws IOException, InterruptedException {
        Path tempZip = Files.createTempFile("skill-download-", ".zip");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<Path> response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(tempZip, StandardOpenOption.TRUNCATE_EXISTING));

            if (response.statusCode() != 200) {
                throw new IOException("Failed to download skill: HTTP " + response.statusCode());
            }

            return importSkillFromZip(tempZip);
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    public void saveSkill(Skill skill) {
        Path skillDir = AppConstants.Base.SKILLS_DIR.resolve(Objects.requireNonNull(skill.name()));

        try {
            if (!Files.exists(skillDir)) {
                Files.createDirectory(skillDir);
            }

            Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
            String content = buildSkillContent(skill);
            Files.writeString(skillFile, content);
        } catch (IOException e) {
            log.error("Failed to save skill: {}", skill.name(), e);
            throw StorageException.writeError("skill-" + skill.name(), e);
        }
    }

    public void deleteSkill(String name) {
        Path skillDir = AppConstants.Base.SKILLS_DIR.resolve(name);

        try {
            if (Files.exists(skillDir)) {
                deleteDirectory(skillDir);
            }
        } catch (IOException e) {
            log.error("Failed to delete skill: {}", name, e);
            throw StorageException.writeError("skill-" + name, e);
        }
    }

    private String buildSkillContent(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        for (Map.Entry<String, Object> entry : skill.frontMatter().entrySet()) {
            sb.append(entry.getKey()).append(": \"").append(entry.getValue()).append("\"\n");
        }
        sb.append("---\n");
        sb.append(skill.content());
        return sb.toString();
    }

    private void unzipFile(Path zipPath, Path targetDir) throws IOException {
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private Path findSkillDirectory(Path root) throws IOException {
        Path skillFile = root.resolve(SKILL_FILE_NAME);
        if (Files.exists(skillFile)) {
            return root;
        }

        try (Stream<Path> paths = Files.walk(root, 2)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(SKILL_FILE_NAME)))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);

        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(sourcePath -> {
                try {
                    Path relativePath = source.relativize(sourcePath);
                    Path targetPath = target.resolve(relativePath);

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("Failed to copy: {}", sourcePath, e);
                }
            });
        }
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
                            log.error("Failed to delete: {}", p, e);
                        }
                    });
        }
    }

    private void createSampleSkill(Path skillDir) {
        Map<String, Object> frontMatter = new LinkedHashMap<>();
        frontMatter.put("name", "hello");
        frontMatter.put("description", "A simple hello world skill. Invoke when user asks for a greeting or demo.");
        Skill sample = new Skill(skillDir.resolve("hello").toString(), frontMatter,
                "This is a sample skill that demonstrates the skill format.\n\nYou can customize this skill to do various tasks.");
        saveSkill(sample);
    }

}
