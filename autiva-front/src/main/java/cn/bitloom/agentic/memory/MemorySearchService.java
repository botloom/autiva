package cn.bitloom.agentic.memory;

import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class MemorySearchService {

    private static final int MAX_RESULT_LENGTH = 300;

    private final Path memoriesDir;

    public MemorySearchService() {
        this.memoriesDir = AppConstants.Base.WORKSPACE_DIR.resolve("MAIN").resolve("memories");
    }

    public String searchAndFormat(String query, int limit) {
        if (query == null || query.isBlank()) {
            return null;
        }

        List<SearchResult> results = search(query, limit);
        if (results.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (SearchResult result : results) {
            sb.append("- **").append(result.fileName).append("**");
            if (result.description != null && !result.description.isBlank()) {
                sb.append(" — ").append(result.description);
            }
            sb.append("\n");
            if (result.snippet != null && !result.snippet.isBlank()) {
                sb.append("  ").append(truncate(result.snippet, 200)).append("\n");
            }
        }
        return sb.toString();
    }

    private List<SearchResult> search(String query, int limit) {
        List<SearchResult> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        if (!Files.exists(memoriesDir) || !Files.isDirectory(memoriesDir)) {
            return results;
        }

        try (Stream<Path> walk = Files.walk(memoriesDir)) {
            walk.filter(p -> p.toString().endsWith(".md"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        if (results.size() >= limit) return;
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            String lowerContent = content.toLowerCase();
                            String fileName = memoriesDir.relativize(p).toString();

                            if (lowerContent.contains(lowerQuery) || fileName.toLowerCase().contains(lowerQuery)) {
                                SearchResult result = new SearchResult();
                                result.fileName = fileName;
                                result.description = extractDescription(content);
                                result.snippet = extractSnippet(content, lowerQuery);
                                results.add(result);
                            }
                        }
                        catch (Exception e) {
                            log.debug("搜索记忆文件失败: {}", p, e);
                        }
                    });
        }
        catch (Exception e) {
            log.error("遍历记忆目录失败: {}", memoriesDir, e);
        }

        return results.subList(0, Math.min(results.size(), limit));
    }

    private String extractDescription(String content) {
        int descIdx = content.indexOf("description:");
        if (descIdx == -1) return null;
        int start = descIdx + "description:".length();
        int end = content.indexOf('\n', start);
        if (end == -1) end = Math.min(content.length(), start + 150);
        return content.substring(start, end).trim();
    }

    private String extractSnippet(String content, String query) {
        int idx = content.toLowerCase().indexOf(query);
        if (idx == -1) return null;
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 150);
        String snippet = content.substring(start, end).replaceAll("\\s+", " ").trim();
        return truncate(snippet, MAX_RESULT_LENGTH);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private static class SearchResult {
        String fileName;
        String description;
        String snippet;
    }
}
