package cn.bitloom.agentic.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GrepTool implements ITool {

    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_LINE_LENGTH = 500;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final List<String> VCS_DIRS = List.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl");
    private static final List<String> BINARY_EXTENSIONS = List.of(
        ".exe", ".dll", ".so", ".dylib", ".class", ".jar", ".zip", ".tar", ".gz", ".rar", ".7z",
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico", ".pdf", ".doc", ".docx"
    );

    @Tool(name = "grep", description = "在文件内容中搜索正则表达式模式。支持多种输出模式：内容、文件列表、计数。")
    public ToolResult grep(
            @ToolParam(description = "要搜索的正则表达式模式") String pattern,
            @ToolParam(description = "搜索路径，默认当前工作目录", required = false) String path,
            @ToolParam(description = "文件类型过滤，如 *.java、*.ts", required = false) String glob,
            @ToolParam(description = "输出模式：content(显示匹配行)、files(仅文件名)、count(计数)，默认files", required = false) String outputMode,
            @ToolParam(description = "是否忽略大小写，默认false", required = false) Boolean ignoreCase,
            @ToolParam(description = "显示匹配行前后的上下文行数", required = false) Integer context,
            @ToolParam(description = "限制输出行数/文件数，默认250", required = false) Integer headLimit) {

        log.info("[ToolCall] grep - 搜索内容: pattern={}, path={}, outputMode={}", pattern, path, outputMode);

        if (pattern == null || pattern.trim().isEmpty()) {
            return ToolResult.failure("错误：搜索模式不能为空");
        }

        Path searchPath = resolvePath(path);
        String mode = parseOutputMode(outputMode);
        boolean caseInsensitive = Boolean.TRUE.equals(ignoreCase);
        int contextLines = context != null && context > 0 ? context : 0;
        int maxResults = headLimit != null && headLimit > 0 ? Math.min(headLimit, MAX_RESULTS) : DEFAULT_HEAD_LIMIT;

        if (!Files.exists(searchPath)) {
            return ToolResult.failure("错误：路径不存在: " + searchPath);
        }

        Pattern regex;
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            regex = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            return ToolResult.failure("错误：无效的正则表达式: " + e.getMessage());
        }

        try {
            long startTime = System.currentTimeMillis();
            List<SearchResult> results = searchContent(searchPath, regex, glob, mode, contextLines, maxResults);
            long duration = System.currentTimeMillis() - startTime;

            String output = formatOutput(results, mode, searchPath, duration, maxResults);
            log.info("[ToolCall] grep - 搜索完成: 找到 {} 个结果, 耗时 {}ms", results.size(), duration);
            return ToolResult.success("搜索完成", output);

        } catch (IOException e) {
            log.error("[ToolCall] grep - 搜索失败: pattern={}", pattern, e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private String parseOutputMode(String outputMode) {
        if (outputMode == null) return "files";
        return switch (outputMode.toLowerCase()) {
            case "content" -> "content";
            case "count" -> "count";
            case "files", "files_with_matches" -> "files";
            default -> "files";
        };
    }

    private List<SearchResult> searchContent(Path searchPath, Pattern pattern, String glob, 
                                              String mode, int contextLines, int maxResults) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        PathMatcher pathMatcher = glob != null ? FileSystems.getDefault().getPathMatcher("glob:" + glob) : null;

        Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= maxResults * 2) {
                    return FileVisitResult.TERMINATE;
                }

                if (pathMatcher != null && !pathMatcher.matches(file.getFileName())) {
                    return FileVisitResult.CONTINUE;
                }

                if (isBinaryFile(file) || attrs.size() > MAX_FILE_SIZE) {
                    return FileVisitResult.CONTINUE;
                }

                try {
                    SearchResult result = searchInFile(file, pattern, mode, contextLines, maxResults - results.size());
                    if (result != null) {
                        results.add(result);
                    }
                } catch (IOException e) {
                    log.debug("[ToolCall] grep - 无法读取文件: {}", file);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (VCS_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        results.sort(Comparator.comparingLong(SearchResult::lastModified).reversed());
        return results.stream().limit(maxResults).collect(Collectors.toList());
    }

    private SearchResult searchInFile(Path file, Pattern pattern, String mode, int contextLines, int remaining) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<MatchResult> matches = new ArrayList<>();
        int matchCount = 0;

        for (int lineNum = 0; lineNum < lines.size() && matches.size() < remaining; lineNum++) {
            String line = lines.get(lineNum);
            if (pattern.matcher(line).find()) {
                matchCount++;
                if ("content".equals(mode)) {
                    int start = Math.max(0, lineNum - contextLines);
                    int end = Math.min(lines.size(), lineNum + contextLines + 1);
                    List<String> contextLinesList = new ArrayList<>();
                    for (int i = start; i < end; i++) {
                        String ctxLine = lines.get(i);
                        if (ctxLine.length() > MAX_LINE_LENGTH) {
                            ctxLine = ctxLine.substring(0, MAX_LINE_LENGTH) + "...";
                        }
                        contextLinesList.add((i + 1) + ":" + ctxLine);
                    }
                    matches.add(new MatchResult(lineNum + 1, line, contextLinesList));
                } else if ("count".equals(mode)) {
                    matches.add(new MatchResult(lineNum + 1, line, null));
                } else {
                    return new SearchResult(file, matchCount, List.of(), System.currentTimeMillis());
                }
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        return new SearchResult(file, matchCount, matches, System.currentTimeMillis());
    }

    private boolean isBinaryFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String formatOutput(List<SearchResult> results, String mode, Path searchPath, long duration, int maxResults) {
        StringBuilder output = new StringBuilder();

        if (results.isEmpty()) {
            output.append("未找到匹配的内容\n");
            output.append(String.format("搜索耗时: %dms", duration));
            return output.toString();
        }

        switch (mode) {
            case "content":
                output.append(String.format("找到 %d 处匹配\n", results.stream().mapToInt(r -> r.matches.size()).sum()));
                output.append(String.format("搜索耗时: %dms\n\n", duration));
                for (SearchResult result : results) {
                    String relativePath = searchPath.toAbsolutePath().relativize(result.file).toString();
                    output.append(String.format("文件: %s\n", relativePath));
                    for (MatchResult match : result.matches) {
                        for (String ctxLine : match.contextLines) {
                            output.append("  ").append(ctxLine).append("\n");
                        }
                        output.append("\n");
                    }
                }
                break;

            case "count":
                output.append(String.format("找到 %d 处匹配\n", results.stream().mapToInt(r -> r.matchCount).sum()));
                output.append(String.format("搜索耗时: %dms\n\n", duration));
                for (SearchResult result : results) {
                    String relativePath = searchPath.toAbsolutePath().relativize(result.file).toString();
                    output.append(String.format("%s: %d\n", relativePath, result.matchCount));
                }
                break;

            case "files":
            default:
                output.append(String.format("找到 %d 个文件\n", results.size()));
                output.append(String.format("搜索耗时: %dms\n\n", duration));
                for (SearchResult result : results) {
                    String relativePath = searchPath.toAbsolutePath().relativize(result.file).toString();
                    output.append(relativePath).append("\n");
                }
                break;
        }

        if (results.size() >= maxResults) {
            output.append(String.format("\n... [结果已截断，显示前 %d 个] ...", maxResults));
        }

        return output.toString();
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

    private record SearchResult(Path file, int matchCount, List<MatchResult> matches, long lastModified) {
    }

    private record MatchResult(int lineNumber, String line, List<String> contextLines) {
    }
}
