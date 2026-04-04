package cn.bitloom.agentic.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class GlobTool implements ITool {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_RESULTS = 1000;

    @Tool(name = "glob", description = "使用通配符模式搜索文件。支持 **、*、? 等通配符。按修改时间排序结果。")
    public ToolResult glob(
            @ToolParam(description = "通配符模式，如 **/*.java、src/**/*.ts") String pattern,
            @ToolParam(description = "搜索目录，默认当前工作目录", required = false) String path,
            @ToolParam(description = "最大结果数，默认100", required = false) Integer limit) {

        log.info("[ToolCall] glob - 搜索文件: pattern={}, path={}, limit={}", pattern, path, limit);

        if (pattern == null || pattern.trim().isEmpty()) {
            return ToolResult.failure("错误：搜索模式不能为空");
        }

        Path searchPath = resolvePath(path);
        int maxResults = limit != null && limit > 0 ? Math.min(limit, MAX_RESULTS) : DEFAULT_LIMIT;

        if (!Files.exists(searchPath)) {
            return ToolResult.failure("错误：目录不存在: " + searchPath);
        }

        if (!Files.isDirectory(searchPath)) {
            return ToolResult.failure("错误：路径不是目录: " + searchPath);
        }

        try {
            long startTime = System.currentTimeMillis();
            List<SearchResult> results = searchFiles(searchPath, pattern, maxResults);
            long duration = System.currentTimeMillis() - startTime;

            boolean truncated = results.size() >= maxResults;
            List<SearchResult> limitedResults = results.stream()
                    .limit(maxResults)
                    .toList();

            StringBuilder output = new StringBuilder();
            output.append(String.format("搜索模式: %s\n", pattern));
            output.append(String.format("搜索目录: %s\n", searchPath.toAbsolutePath()));
            output.append(String.format("找到文件: %d 个\n", limitedResults.size()));
            output.append(String.format("搜索耗时: %dms\n\n", duration));

            if (limitedResults.isEmpty()) {
                output.append("未找到匹配的文件");
            } else {
                output.append("文件列表:\n");
                for (SearchResult result : limitedResults) {
                    String relativePath = searchPath.toAbsolutePath().relativize(result.path).toString();
                    output.append(String.format("  %s\n", relativePath));
                }

                if (truncated) {
                    output.append(String.format("\n... [结果已截断，显示前 %d 个] ...", maxResults));
                }
            }

            log.info("[ToolCall] glob - 搜索完成: 找到 {} 个文件, 耗时 {}ms", limitedResults.size(), duration);
            return ToolResult.success("搜索完成", output.toString());

        } catch (IOException e) {
            log.error("[ToolCall] glob - 搜索失败: pattern={}", pattern, e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private List<SearchResult> searchFiles(Path searchPath, String pattern, int maxResults) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relativePath = searchPath.toAbsolutePath().relativize(file.toAbsolutePath());
                if (matcher.matches(relativePath) || matcher.matches(file.getFileName())) {
                    results.add(new SearchResult(file, attrs.lastModifiedTime().toMillis()));
                }
                return results.size() >= maxResults * 2 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (dirName.startsWith(".") && !dirName.equals(".") && !dirName.equals("..")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        results.sort(Comparator.comparingLong(SearchResult::getLastModified).reversed());
        return results;
    }

    private Path resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return Paths.get(System.getProperty("user.dir"));
        }
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return Paths.get(System.getProperty("user.dir"), path);
    }

    private static class SearchResult {
        final Path path;
        final long lastModified;

        SearchResult(Path path, long lastModified) {
            this.path = path;
            this.lastModified = lastModified;
        }

        long getLastModified() {
            return lastModified;
        }
    }
}
