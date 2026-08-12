package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.tool.ToolUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯Java grep实现，不需要安装外部ripgrep。使用Java NIO.2进行文件遍历，
 * 使用regex Pattern/Matcher进行搜索。
 *
 * 性能优化要点：
 * 1. 使用 FileVisitor 在 preVisitDirectory 阶段剪枝，避免进入 .git/node_modules/target 等目录
 * 2. 候选文件收集后使用 parallelStream 并行扫描内容
 * 3. 按扩展名跳过二进制文件，按大小跳过过大文件
 */
@Slf4j
public class GrepTool extends AbstractTool<GrepTool.Input> {

    private static final String DESCRIPTION = """
            基于正则的内容搜索(纯 Java 实现)。优先使用此工具而非 bash grep。支持 glob 文件过滤、多行模式。输出模式:content(匹配行)、files_with_matches(文件路径)、count(计数)。
            """;

    /** 默认跳过超过此大小(字节)的文件，避免读取大日志/二进制产物 */
    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 常见二进制/产物文件扩展名，扫描时直接跳过 */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            // 类库与产物
            "class", "jar", "war", "ear", "aar", "dex",
            // 图片
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "tif", "tiff", "webp", "svgz",
            // 音视频
            "mp3", "mp4", "wav", "avi", "mov", "flv", "wmv", "aac", "ogg", "webm",
            // 压缩包
            "zip", "gz", "tar", "rar", "7z", "bz2", "xz",
            // 文档
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            // 数据库与锁文件
            "db", "sqlite", "lock", "dat",
            // 编译中间产物
            "o", "obj", "so", "dll", "exe", "bin", "pyc", "pyo",
            // 字体
            "ttf", "otf", "woff", "woff2", "eot"
    );

    private final int maxOutputLength;

    private final int maxDepth;

    private final int maxLineLength;

    private final long maxFileSize;

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
        this.maxFileSize = builder.maxFileSize;
        this.workingDirectory = builder.workingDirectory;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        try {
            Path searchPath = ToolUtils.resolveWorkingDirectory(input.path(), this.workingDirectory);
            log.debug("[Grep] pattern='{}', path='{}', resolvedPath='{}', outputMode='{}', glob='{}', type='{}'",
                    input.pattern(), input.path(), searchPath, input.outputMode(), input.glob(), input.type());

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
            log.error("[Grep] 执行出错: pattern='{}', path='{}', error={}", input.pattern(), input.path(), e.getMessage(), e);
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

    /**
     * 判断是否为二进制/产物文件，按扩展名快速跳过，避免无谓读取。
     */
    private boolean isBinaryFile(Path file) {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BINARY_EXTENSIONS.contains(ext);
    }

    private String searchFilesWithMatches(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
            Integer headLimit, Integer offset) throws IOException {

        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        // 并行收集所有匹配文件路径，最后做切片以保证顺序稳定
        ConcurrentLinkedQueue<String> matched = new ConcurrentLinkedQueue<>();
        this.processFiles(searchPath, matchers, file -> {
            if (this.fileContainsPattern(file, pattern)) {
                matched.add(file.toString());
            }
            return true;
        });

        if (matched.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        // 按路径排序后切片，保证 offset/headLimit 的语义稳定
        List<String> sorted = new ArrayList<>(matched);
        sorted.sort(Comparator.naturalOrder());
        int end = Math.min(sorted.size(), skip + limit);
        if (skip >= sorted.size()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }
        List<String> page = sorted.subList(skip, end);
        return String.join("\n", page);
    }

    private String searchCount(Path searchPath, Pattern pattern, List<PathMatcher> matchers, Integer headLimit,
            Integer offset) throws IOException {

        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        // 并行收集文件 -> 计数
        ConcurrentHashMap<String, Integer> fileCounts = new ConcurrentHashMap<>();
        this.processFiles(searchPath, matchers, file -> {
            int matches = this.countMatchesInFile(file, pattern);
            if (matches > 0) {
                fileCounts.put(file.toString(), matches);
            }
            return true;
        });

        if (fileCounts.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        // 按路径排序后切片
        List<String> keys = new ArrayList<>(fileCounts.keySet());
        keys.sort(Comparator.naturalOrder());
        int end = Math.min(keys.size(), skip + limit);
        if (skip >= keys.size()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }
        List<String> page = keys.subList(skip, end);

        StringBuilder result = new StringBuilder();
        for (String key : page) {
            result.append(key).append(":").append(fileCounts.get(key)).append("\n");
        }

        return result.toString().trim();
    }

    private String searchContent(Path searchPath, Pattern pattern, List<PathMatcher> matchers, int beforeContext,
            int afterContext, boolean lineNumbers, Integer headLimit, Integer offset) throws IOException {

        int skip = offset != null ? offset : 0;
        int limit = headLimit != null && headLimit > 0 ? headLimit : Integer.MAX_VALUE;

        // 并行收集每个文件的匹配片段（文件路径 + 内部匹配行列表）
        ConcurrentLinkedQueue<FileMatch> fileMatches = new ConcurrentLinkedQueue<>();
        this.processFiles(searchPath, matchers, file -> {
            List<String> matches = this.findMatchesWithContext(file, pattern, beforeContext, afterContext, lineNumbers);
            if (!matches.isEmpty()) {
                fileMatches.add(new FileMatch(file.toString(), matches));
            }
            return true;
        });

        if (fileMatches.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        // 按文件路径排序，保持输出稳定
        List<FileMatch> sorted = new ArrayList<>(fileMatches);
        sorted.sort(Comparator.comparing(fm -> fm.filePath));

        StringBuilder result = new StringBuilder();
        int emitted = 0;
        int seen = 0;
        for (FileMatch fm : sorted) {
            if (emitted >= limit) {
                break;
            }
            // 跨文件分块输出
            result.append(fm.filePath).append("\n");
            for (String line : fm.lines) {
                if (seen >= skip + limit) {
                    break;
                }
                if (seen >= skip) {
                    result.append(line).append("\n");
                    emitted++;
                }
                seen++;
            }
            result.append("\n");
        }

        if (result.isEmpty()) {
            return "未找到匹配模式的文件: " + pattern.pattern();
        }

        return result.toString().trim();
    }

    /**
     * 遍历搜索路径下的文件并交给 processor 处理。
     *
     * 优化点：
     * 1. 使用 FileVisitor 在 preVisitDirectory 阶段直接 SKIP_SUBTREE 跳过忽略目录，避免无谓遍历
     * 2. 候选文件收集完成后用 parallelStream 并行处理
     * 3. 二进制文件 / 超大文件直接跳过
     *
     * processor 返回 false 表示请求提前终止（并行下为软终止，已分配任务会完成）。
     */
    private void processFiles(Path searchPath, List<PathMatcher> matchers, FileProcessor processor) throws IOException {
        if (Files.isRegularFile(searchPath)) {
            if (this.matchesGlob(searchPath, matchers)
                    && !this.isIgnoredPath(searchPath)
                    && !this.isBinaryFile(searchPath)) {
                processor.process(searchPath);
            }
            return;
        }
        if (!Files.isDirectory(searchPath)) {
            return;
        }

        // 1. FileVisitor 剪枝，收集候选文件
        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(searchPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), this.maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 跳过 .git/node_modules/target 等忽略目录，不进入其子树
                if (!dir.equals(searchPath) && ToolUtils.isIgnoredPath(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.size() > GrepTool.this.maxFileSize) {
                    return FileVisitResult.CONTINUE;
                }
                if (!GrepTool.this.matchesGlob(file, matchers)) {
                    return FileVisitResult.CONTINUE;
                }
                if (GrepTool.this.isBinaryFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                candidates.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        if (candidates.isEmpty()) {
            log.warn("[Grep] candidates 为空，searchPath='{}', matchers={}", searchPath, matchers.size());
            return;
        }

        log.debug("[Grep] 候选文件数={}, 并行扫描中...", candidates.size());

        // 2. 并行处理候选文件；processor 返回 false 时尽量提前终止
        candidates.parallelStream()
                .anyMatch(file -> !processor.process(file));
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

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // 滚动缓冲：暂存匹配行之前的候选上下文行
            Deque<IndexedLine> pending = new ArrayDeque<>();
            String line;
            long lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                IndexedLine indexed = new IndexedLine(lineNum, line);
                boolean matched;
                if (line.length() > this.maxLineLength) {
                    matched = false;
                }
                else {
                    matched = pattern.matcher(line).find();
                }

                if (matched) {
                    // 输出前导上下文（beforeContext）
                    while (!pending.isEmpty()) {
                        IndexedLine prev = pending.removeFirst();
                        results.add(formatLine(prev, false, lineNumbers));
                    }
                    // 输出匹配行及其后续上下文（afterContext）
                    results.add(formatLine(indexed, true, lineNumbers));
                    int remaining = afterContext;
                    while (remaining-- > 0 && (line = reader.readLine()) != null) {
                        lineNum++;
                        IndexedLine ctx = new IndexedLine(lineNum, line);
                        results.add(formatLine(ctx, false, lineNumbers));
                    }
                    results.add("--");
                }
                else {
                    pending.addLast(indexed);
                    if (afterContext > 0) {
                        // 保持"前导上下文 + 后续补位"总量受限，防止无界增长
                        while (pending.size() > beforeContext + afterContext) {
                            pending.removeFirst();
                        }
                    }
                    else {
                        while (pending.size() > beforeContext) {
                            pending.removeFirst();
                        }
                    }
                }
            }

            if (!results.isEmpty() && results.get(results.size() - 1).equals("--")) {
                results.remove(results.size() - 1);
            }
        }
        catch (IOException ignored) {
        }

        return results;
    }

    private static String formatLine(IndexedLine indexed, boolean isMatch, boolean lineNumbers) {
        String prefix = lineNumbers ? indexed.lineNum + ":" : "";
        prefix += isMatch ? "  " : "- ";
        return prefix + indexed.line;
    }

    /** 带行号的单行，用于 context 缓冲。 */
    private static final class IndexedLine {
        final long lineNum;
        final String line;

        IndexedLine(long lineNum, String line) {
            this.lineNum = lineNum;
            this.line = line;
        }
    }

    /** 单文件的匹配结果，用于 content 模式并行收集后合并。 */
    private static final class FileMatch {
        final String filePath;
        final List<String> lines;

        FileMatch(String filePath, List<String> lines) {
            this.filePath = filePath;
            this.lines = lines;
        }
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

        private long maxFileSize = DEFAULT_MAX_FILE_SIZE;

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

        public Builder maxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
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
