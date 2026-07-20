package cn.bitloom.agentic.tool.search;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.ToolUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 纯Java grep实现，不需要安装外部ripgrep。使用Java NIO.2进行文件遍历，
 * 使用regex Pattern/Matcher进行搜索。
 */
public class GrepTool extends AbstractTool<GrepTool.Input> {

    private static final String DESCRIPTION = """
            基于正则的内容搜索(纯 Java 实现)。优先使用此工具而非 bash grep。支持 glob 文件过滤、多行模式。输出模式:content(匹配行)、files_with_matches(文件路径)、count(计数)。
            """;

    private final int maxOutputLength;

    private final int maxDepth;

    private final int maxLineLength;

    private final Path workingDirectory;

    private static final Map<String, String[]> FILE_TYPE_EXTENSIONS = new HashMap<>();
    static {
        FILE_TYPE_EXTENSIONS.put("java", new String[] { "*.java" });
        FILE_TYPE_EXTENSIONS.put("js", new String[] { "*.js", "*.jsx" });
        FILE_TYPE_EXTENSIONS.put("ts", new String[] { "*.ts", "*.tsx" });
        FILE_TYPE_EXTENSIONS.put("py", new String[] { "*.py" });
        FILE_TYPE_EXTENSIONS.put("rust", new String[] { "*.rs" });
        FILE_TYPE_EXTENSIONS.put("go", new String[] { "*.go" });
        FILE_TYPE_EXTENSIONS.put("cpp", new String[] { "*.cpp", "*.cc", "*.cxx", "*.hpp", "*.h" });
        FILE_TYPE_EXTENSIONS.put("c", new String[] { "*.c", "*.h" });
        FILE_TYPE_EXTENSIONS.put("rb", new String[] { "*.rb" });
        FILE_TYPE_EXTENSIONS.put("php", new String[] { "*.php" });
        FILE_TYPE_EXTENSIONS.put("cs", new String[] { "*.cs" });
        FILE_TYPE_EXTENSIONS.put("xml", new String[] { "*.xml" });
        FILE_TYPE_EXTENSIONS.put("json", new String[] { "*.json" });
        FILE_TYPE_EXTENSIONS.put("yaml", new String[] { "*.yaml", "*.yml" });
        FILE_TYPE_EXTENSIONS.put("md", new String[] { "*.md", "*.markdown" });
        FILE_TYPE_EXTENSIONS.put("txt", new String[] { "*.txt" });
        FILE_TYPE_EXTENSIONS.put("sh", new String[] { "*.sh", "*.bash" });
    }

    /**
     * grep的输出模式
     */
    public enum OutputMode {
        files_with_matches,
        count,
        content
    }

    public record Input(
            @ToolParam(description = "要在文件内容中搜索的正则表达式模式") String pattern,
            @ToolParam(description = "要搜索的文件或目录。默认为当前工作目录。", required = false) String path,
            @ToolParam(description = "用于过滤文件的glob模式", required = false) String glob,
            @ToolParam(description = "输出模式：content/files_with_matches/count", required = false) OutputMode outputMode,
            @ToolParam(description = "每个匹配前显示的行数", required = false) Integer contextBefore,
            @ToolParam(description = "每个匹配后显示的行数", required = false) Integer contextAfter,
            @ToolParam(description = "每个匹配前后显示的行数", required = false) Integer context,
            @ToolParam(description = "在输出中显示行号", required = false) Boolean showLineNumbers,
            @ToolParam(description = "不区分大小写搜索", required = false) Boolean caseInsensitive,
            @ToolParam(description = "要搜索的文件类型", required = false) String type,
            @ToolParam(description = "限制输出为前N行/条目", required = false) Integer headLimit,
            @ToolParam(description = "跳过前N行/条目", required = false) Integer offset,
            @ToolParam(description = "启用多行模式", required = false) Boolean multiline
    ) {}

    private GrepTool(Builder builder) {
        super("Grep", DESCRIPTION, Input.class);
        this.maxOutputLength = builder.maxOutputLength;
        this.maxDepth = builder.maxDepth;
        this.maxLineLength = builder.maxLineLength;
        this.workingDirectory = builder.workingDirectory;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        try {
            Path searchPath = ToolUtils.resolveWorkingDirectory(input.path(), this.workingDirectory);

            if (!Files.exists(searchPath)) {
                String errorMsg = "错误：路径不存在: " + searchPath.toAbsolutePath();
                return ToolResult.error(errorMsg, errorMsg);
            }

            int flags = Pattern.MULTILINE;
            if (Boolean.TRUE.equals(input.caseInsensitive())) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            if (Boolean.TRUE.equals(input.multiline())) {
                flags |= Pattern.DOTALL;
            }

            Pattern searchPattern;
            try {
                searchPattern = Pattern.compile(input.pattern(), flags);
            }
            catch (Exception e) {
                String errorMsg = "错误：无效的正则表达式模式: " + e.getMessage();
                return ToolResult.error(errorMsg, errorMsg);
            }

            OutputMode outputMode = input.outputMode() != null ? input.outputMode() : OutputMode.files_with_matches;

            List<PathMatcher> globMatchers = this.buildGlobMatchers(input.glob(), input.type());

            String result;
            ToolResult toolResult;
            switch (outputMode) {
                case files_with_matches:
                    result = this.searchFilesWithMatches(searchPath, searchPattern, globMatchers, input.headLimit(), input.offset());
                    if (result.startsWith("未找到匹配模式的文件")) {
                        return ToolResult.success(result);
                    }
                    int fileCount = result.split("\n").length;
                    toolResult = ToolResult.builder()
                            .status(ToolResult.Status.SUCCESS)
                            .message(fileCount + " 个文件匹配")
                            .data(Map.of("pattern", input.pattern(), "count", fileCount))
                            .rawOutput(result)
                            .build();
                    break;
                case count:
                    result = this.searchCount(searchPath, searchPattern, globMatchers, input.headLimit(), input.offset());
                    if (result.startsWith("未找到匹配模式的文件")) {
                        return ToolResult.success(result);
                    }
                    int countLines = result.split("\n").length;
                    toolResult = ToolResult.builder()
                            .status(ToolResult.Status.SUCCESS)
                            .message(countLines + " 个文件有匹配")
                            .data(Map.of("pattern", input.pattern(), "count", countLines))
                            .rawOutput(result)
                            .build();
                    break;
                case content:
                    int beforeContext = input.context() != null ? input.context() : (input.contextBefore() != null ? input.contextBefore() : 0);
                    int afterContext = input.context() != null ? input.context() : (input.contextAfter() != null ? input.contextAfter() : 0);
                    boolean lineNumbers = input.showLineNumbers() == null || input.showLineNumbers();
                    result = this.searchContent(searchPath, searchPattern, globMatchers, beforeContext, afterContext,
                            lineNumbers, input.headLimit(), input.offset());
                    if (result.startsWith("未找到匹配模式的文件")) {
                        return ToolResult.success(result);
                    }
                    toolResult = ToolResult.builder()
                            .status(ToolResult.Status.SUCCESS)
                            .message("搜索结果")
                            .data(Map.of("pattern", input.pattern()))
                            .rawOutput(result)
                            .build();
                    break;
                default:
                    result = this.searchFilesWithMatches(searchPath, searchPattern, globMatchers, input.headLimit(), input.offset());
                    if (result.startsWith("未找到匹配模式的文件")) {
                        return ToolResult.success(result);
                    }
                    int defaultCount = result.split("\n").length;
                    toolResult = ToolResult.builder()
                            .status(ToolResult.Status.SUCCESS)
                            .message(defaultCount + " 个文件匹配")
                            .data(Map.of("pattern", input.pattern(), "count", defaultCount))
                            .rawOutput(result)
                            .build();
            }

            // 应用截断到rawOutput
            String rawOutput = toolResult.getRawOutput();
            if (rawOutput != null && rawOutput.length() > this.maxOutputLength) {
                rawOutput = rawOutput.substring(0, this.maxOutputLength) + "\n... （输出已截断，"
                        + (rawOutput.length() - this.maxOutputLength) + " 个字符已省略）";
                toolResult = ToolResult.builder()
                        .status(toolResult.getStatus())
                        .message(toolResult.getMessage())
                        .data(toolResult.getData())
                        .rawOutput(rawOutput)
                        .build();
            }

            return toolResult;

        }
        catch (Exception e) {
            return ToolResult.error("执行grep时出错: " + e.getMessage());
        }
    }

    private List<PathMatcher> buildGlobMatchers(String glob, String type) {
        List<PathMatcher> matchers = new ArrayList<>();

        if (StringUtils.hasText(type)) {
            String[] extensions = FILE_TYPE_EXTENSIONS.get(type.toLowerCase());
            if (extensions != null) {
                for (String ext : extensions) {
                    matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + ext));
                }
            }
        }

        if (StringUtils.hasText(glob)) {
            String globPattern = glob.startsWith("**/") ? glob : "**/" + glob;
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + globPattern));
        }

        return matchers;
    }

    private boolean matchesGlob(Path file, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) {
            return true;
        }

        for (PathMatcher matcher : matchers) {
            if (matcher.matches(file)) {
                return true;
            }
        }
        return false;
    }

    private String searchFilesWithMatches(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
            Integer headLimit, Integer offset) throws IOException {

        List<String> matchingFiles = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        this.processFiles(searchPath, matchers, file -> {
            if (count.get() >= skip + limit) {
                return false;
            }

            if (this.fileContainsPattern(file, pattern)) {
                if (count.getAndIncrement() >= skip) {
                    matchingFiles.add(file.toString());
                }
            }
            return true;
        });

        if (matchingFiles.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        return String.join("\n", matchingFiles);
    }

    private String searchCount(Path searchPath, Pattern pattern, List<PathMatcher> matchers, Integer headLimit,
            Integer offset) throws IOException {

        Map<String, Integer> fileCounts = new LinkedHashMap<>();
        AtomicInteger fileCount = new AtomicInteger(0);
        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        this.processFiles(searchPath, matchers, file -> {
            if (fileCount.get() >= skip + limit) {
                return false;
            }

            int matches = this.countMatchesInFile(file, pattern);
            if (matches > 0) {
                if (fileCount.getAndIncrement() >= skip) {
                    fileCounts.put(file.toString(), matches);
                }
            }
            return true;
        });

        if (fileCounts.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Integer> entry : fileCounts.entrySet()) {
            result.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }

        return result.toString().trim();
    }

    private String searchContent(Path searchPath, Pattern pattern, List<PathMatcher> matchers, int beforeContext,
            int afterContext, boolean lineNumbers, Integer headLimit, Integer offset) throws IOException {

        StringBuilder result = new StringBuilder();
        AtomicInteger lineCount = new AtomicInteger(0);
        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        this.processFiles(searchPath, matchers, file -> {
            if (lineCount.get() >= skip + limit) {
                return false;
            }

            List<String> matches = this.findMatchesWithContext(file, pattern, beforeContext, afterContext, lineNumbers);
            if (!matches.isEmpty()) {
                result.append(file.toString()).append("\n");

                for (String match : matches) {
                    if (lineCount.get() >= skip + limit) {
                        break;
                    }
                    if (lineCount.getAndIncrement() >= skip) {
                        result.append(match).append("\n");
                    }
                }
                result.append("\n");
            }
            return lineCount.get() < skip + limit;
        });

        if (result.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        return result.toString().trim();
    }

    private void processFiles(Path searchPath, List<PathMatcher> matchers, FileProcessor processor) throws IOException {
        if (Files.isRegularFile(searchPath)) {
            if (this.matchesGlob(searchPath, matchers)) {
                processor.process(searchPath);
            }
        }
        else if (Files.isDirectory(searchPath)) {
            try (Stream<Path> paths = Files.walk(searchPath, this.maxDepth, FileVisitOption.FOLLOW_LINKS)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> this.matchesGlob(p, matchers))
                        .filter(p -> !this.isIgnoredPath(p))
                        .anyMatch(file -> !processor.process(file));
            }
        }
    }

    private boolean isIgnoredPath(Path path) {
        return ToolUtils.isIgnoredPath(path);
    }

    private boolean fileContainsPattern(Path file, Pattern pattern) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > this.maxLineLength)
                    continue;
                if (pattern.matcher(line).find()) {
                    return true;
                }
            }
        }
        catch (IOException ignored) {
        }
        return false;
    }

    private int countMatchesInFile(Path file, Pattern pattern) {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > this.maxLineLength)
                    continue;
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    count++;
                }
            }
        }
        catch (IOException ignored) {
        }
        return count;
    }

    private List<String> findMatchesWithContext(Path file, Pattern pattern, int beforeContext, int afterContext,
            boolean lineNumbers) {
        List<String> results = new ArrayList<>();

        try {
            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Integer> matchingLineNumbers = new ArrayList<>();

            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i);
                if (line.length() > this.maxLineLength)
                    continue;
                if (pattern.matcher(line).find()) {
                    matchingLineNumbers.add(i);
                }
            }

            for (int matchLineNum : matchingLineNumbers) {
                int start = Math.max(0, matchLineNum - beforeContext);
                int end = Math.min(allLines.size() - 1, matchLineNum + afterContext);

                for (int i = start; i <= end; i++) {
                    String prefix = "";
                    if (lineNumbers) {
                        prefix = (i + 1) + ":";
                    }
                    if (i == matchLineNum) {
                        prefix += "  ";
                    }
                    else {
                        prefix += "- ";
                    }
                    results.add(prefix + allLines.get(i));
                }

                results.add("--");
            }

            if (!results.isEmpty() && results.get(results.size() - 1).equals("--")) {
                results.remove(results.size() - 1);
            }

        }
        catch (IOException ignored) {
        }

        return results;
    }

    @FunctionalInterface
    private interface FileProcessor {

        boolean process(Path file);

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int maxOutputLength = 100000;

        private int maxDepth = 100;

        private int maxLineLength = 10000;

        private Path workingDirectory = null;

        public Builder maxOutputLength(int maxOutputLength) {
            this.maxOutputLength = maxOutputLength;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder maxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        /**
         * 设置当智能体未指定路径时使用的工作目录。这允许工具在沙箱/工作区上下文中操作。
         * @param workingDirectory 工作目录路径
         * @return 此构建器
         */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        /**
         * 使用字符串路径设置工作目录。
         * @param workingDirectory 工作目录路径字符串
         * @return 此构建器
         */
        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory != null ? Paths.get(workingDirectory) : null;
            return this;
        }

        public GrepTool build() {
            return new GrepTool(this);
        }

    }

}
