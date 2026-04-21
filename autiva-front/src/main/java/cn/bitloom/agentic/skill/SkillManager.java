package cn.bitloom.agentic.skill;

import cn.bitloom.agentic.util.MarkdownParser;
import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

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
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class SkillManager {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private static final String TOOL_DESCRIPTION_TEMPLATE = """
            在主对话中执行技能

            <skills_instructions>
            当用户要求你执行任务时，检查以下可用技能中是否有任何技能可以更有效地帮助完成任务。技能提供专门的能力和领域知识。

            如何使用技能：
            - 使用此工具仅传入技能名称调用技能（不带参数）
            - 当你调用技能时，你将看到 <command-message>"{name}"技能正在加载</command-message>
            - 技能的提示将展开并提供关于如何完成任务的详细说明

            注意：响应始终以技能执行环境的基本目录开始。你可以使用它来检索其他文件或调用shell命令。
            技能描述紧跟在基本目录行之后。

            重要：
            - 仅使用<available_skills>下面列出的技能
            - 不要调用已经在运行的技能
            </skills_instructions>

            <available_skills>
            %s
            </available_skills>
            """;

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSkills();
    }

    public void loadSkills() {
        skills.clear();
        List<Skill> loadedSkills = loadDirectory(AppConstants.Base.SKILL_DIR.toString());
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

    public ToolCallback buildToolCallback() {
        Assert.notEmpty(this.skills, "必须至少配置一个技能");

        String skillsXml = this.skills.values().stream()
                .map(Skill::toXml)
                .collect(Collectors.joining("\n"));

        return FunctionToolCallback.builder("Skill", new SkillsFunction(this.skills))
                .description(TOOL_DESCRIPTION_TEMPLATE.formatted(skillsXml))
                .inputType(SkillsInput.class)
                .build();
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

            Path targetDir = AppConstants.Base.SKILL_DIR.resolve(name);
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
        Path skillDir = AppConstants.Base.SKILL_DIR.resolve(skill.name());

        try {
            if (!Files.exists(skillDir)) {
                Files.createDirectory(skillDir);
            }

            Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
            String content = buildSkillContent(skill);
            Files.writeString(skillFile, content);
        } catch (IOException e) {
            log.error("Failed to save skill: {}", skill.name(), e);
            throw new RuntimeException("Failed to save skill: " + skill.name(), e);
        }
    }

    public void deleteSkill(String name) {
        Path skillDir = AppConstants.Base.SKILL_DIR.resolve(name);

        try {
            if (Files.exists(skillDir)) {
                deleteDirectory(skillDir);
            }
        } catch (IOException e) {
            log.error("Failed to delete skill: {}", name, e);
            throw new RuntimeException("Failed to delete skill: " + name, e);
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

    public record SkillsInput(
            @ToolParam(description = "技能名称（不带参数）。例如，\"pdf\"或\"xlsx\"") String command) {
    }

    public static class SkillsFunction implements Function<SkillsInput, String> {

        private final Map<String, Skill> skillsMap;

        public SkillsFunction(Map<String, Skill> skillsMap) {
            this.skillsMap = skillsMap;
        }

        @Override
        public String apply(SkillsInput input) {
            Skill skill = this.skillsMap.get(input.command());

            if (skill != null) {
                return "此技能的基本目录：%s\n\n%s".formatted(skill.basePath(), skill.content());
            }

            return "未找到技能：" + input.command();
        }

    }

}
