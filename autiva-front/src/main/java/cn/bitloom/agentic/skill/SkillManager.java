package cn.bitloom.agentic.skill;

import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        loadSkills();
    }

    public void loadSkills() {
        skills.clear();
        List<Skill> loadedSkills = doLoadSkills();
        loadedSkills.forEach(skill -> skills.put(skill.getName(), skill));
    }

    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        skills.forEach((name, skill) -> {
            sb.append("- ").append(name);
            sb.append(": ").append(skill.getDescription());
            sb.append("\n");
        });
        return sb.toString();
    }

    public String getContent(String name) {
        Skill skill = skills.get(name);
        return skill != null ? skill.getContent() : null;
    }

    public List<Skill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    private List<Skill> doLoadSkills() {
        Path skillDir = AppConstants.Base.SKILL_DIR;
        
        if (!Files.exists(skillDir)) {
            try {
                Files.createDirectories(skillDir);
                createSampleSkill(skillDir);
            } catch (IOException e) {
                log.error("Failed to create skill directory", e);
                return List.of();
            }
        }

        try (Stream<Path> paths = Files.walk(skillDir, 1)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(p -> !p.equals(skillDir))
                    .map(this::loadSkillFromDirectory)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to load skills from directory: {}", skillDir, e);
            return List.of();
        }
    }

    private Optional<Skill> loadSkillFromDirectory(Path dir) {
        Path skillFile = dir.resolve(SKILL_FILE_NAME);
        
        if (!Files.exists(skillFile)) {
            log.warn("SKILL.md not found in directory: {}", dir);
            return Optional.empty();
        }

        try {
            String content = Files.readString(skillFile);
            return parseSkillContent(content, dir);
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillFile, e);
            return Optional.empty();
        }
    }

    private Optional<Skill> parseSkillContent(String content, Path dir) {
        if (content == null || content.isEmpty()) {
            log.warn("Empty skill content in directory: {}", dir);
            return Optional.empty();
        }

        String frontMatter = extractFrontMatter(content);
        if (frontMatter == null) {
            log.warn("No valid frontmatter found in directory: {}", dir);
            return Optional.empty();
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(frontMatter);
            
            String name = extractString(data, "name");
            String description = extractString(data, "description");
            
            if (name == null || name.isEmpty()) {
                log.warn("Missing required 'name' field in frontmatter: {}", dir);
                return Optional.empty();
            }
            
            if (description == null || description.isEmpty()) {
                log.warn("Missing required 'description' field in frontmatter: {}", dir);
                return Optional.empty();
            }

            String skillContent = extractBodyContent(content);
            String license = extractString(data, "license");
            String compatibility = extractString(data, "compatibility");

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = extractMetadata(data.get("metadata"));

            return Optional.of(Skill.builder()
                    .name(name)
                    .description(description)
                    .license(license)
                    .compatibility(compatibility)
                    .metadata(metadata)
                    .content(skillContent)
                    .filePath(dir.resolve(SKILL_FILE_NAME))
                    .build());
        } catch (Exception e) {
            log.error("Failed to parse skill frontmatter in directory: {}", dir, e);
            return Optional.empty();
        }
    }

    private String extractFrontMatter(String content) {
        int start = content.indexOf("---");
        if (start == -1) {
            return null;
        }

        int end = content.indexOf("---", start + 3);
        if (end == -1) {
            return null;
        }

        return content.substring(start + 3, end).trim();
    }

    private String extractBodyContent(String content) {
        int start = content.indexOf("---");
        if (start == -1) {
            return content.trim();
        }

        int end = content.indexOf("---", start + 3);
        if (end == -1) {
            return content.trim();
        }

        return content.substring(end + 3).trim();
    }

    private String extractString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractMetadata(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        
        if (value instanceof Map) {
            Map<String, String> result = new LinkedHashMap<>();
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return result;
        }
        
        return Collections.emptyMap();
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

            String content = Files.readString(skillFile);
            Optional<Skill> parsedSkill = parseSkillContent(content, skillDir);
            
            if (parsedSkill.isEmpty()) {
                throw new IOException("Invalid SKILL.md format. Check that name and description fields are present and valid");
            }

            Skill skill = parsedSkill.get();
            
            Path targetDir = AppConstants.Base.SKILL_DIR.resolve(skill.getName());
            if (Files.exists(targetDir)) {
                deleteDirectory(targetDir);
            }

            copyDirectory(skillDir, targetDir);

            return Skill.builder()
                    .name(skill.getName())
                    .description(skill.getDescription())
                    .license(skill.getLicense())
                    .compatibility(skill.getCompatibility())
                    .metadata(skill.getMetadata())
                    .content(skill.getContent())
                    .filePath(targetDir.resolve(SKILL_FILE_NAME))
                    .build();
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

    public void saveSkill(Skill skill) {
        Path skillDir = AppConstants.Base.SKILL_DIR.resolve(skill.getName());
        
        try {
            if (!Files.exists(skillDir)) {
                Files.createDirectory(skillDir);
            }
            
            Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
            String content = buildSkillContent(skill);
            Files.writeString(skillFile, content);
        } catch (IOException e) {
            log.error("Failed to save skill: {}", skill.getName(), e);
            throw new RuntimeException("Failed to save skill: " + skill.getName(), e);
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
        sb.append("name: ").append(skill.getName()).append("\n");
        sb.append("description: \"").append(skill.getDescription()).append("\"\n");
        
        if (skill.getLicense() != null && !skill.getLicense().isEmpty()) {
            sb.append("license: ").append(skill.getLicense()).append("\n");
        }
        
        if (skill.getCompatibility() != null && !skill.getCompatibility().isEmpty()) {
            sb.append("compatibility: ").append(skill.getCompatibility()).append("\n");
        }
        
        if (skill.getMetadata() != null && !skill.getMetadata().isEmpty()) {
            sb.append("metadata:\n");
            for (Map.Entry<String, String> entry : skill.getMetadata().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": \"").append(entry.getValue()).append("\"\n");
            }
        }
        
        sb.append("---\n");
        sb.append(skill.getContent());
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
                            log.error("Failed to delete: {}", p, e);
                        }
                    });
        }
    }

    private void createSampleSkill(Path skillDir) {
        Skill sample = Skill.builder()
                .name("hello")
                .description("A simple hello world skill. Invoke when user asks for a greeting or demo.")
                .content("This is a sample skill that demonstrates the skill format.\n\nYou can customize this skill to do various tasks.")
                .build();
        
        saveSkill(sample);
    }

}
