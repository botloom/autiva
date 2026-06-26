package cn.bitloom.node.editor.syntax;

import java.nio.file.Path;
import java.util.Map;

/**
 * 语法高亮器工厂。
 *
 * <p>根据文件扩展名返回对应语言的 {@link SyntaxHighlighter} 实例。
 * 内部使用单例缓存，highlighter 实现需保证线程安全（无状态）。
 *
 * <p>未知扩展名返回 {@link PlainTextHighlighter}，不做任何高亮。
 */
public final class SyntaxHighlighterFactory {

    private static final SyntaxHighlighter PLAIN = new PlainTextHighlighter();

    private static final SyntaxHighlighter JAVA = new JavaSyntaxHighlighter();
    private static final SyntaxHighlighter JS = new JavaScriptSyntaxHighlighter();
    private static final SyntaxHighlighter PYTHON = new PythonSyntaxHighlighter();
    private static final SyntaxHighlighter JSON = new JsonSyntaxHighlighter();
    private static final SyntaxHighlighter XML = new XmlSyntaxHighlighter();
    private static final SyntaxHighlighter YAML = new YamlSyntaxHighlighter();
    private static final SyntaxHighlighter PROPERTIES = new PropertiesSyntaxHighlighter();
    private static final SyntaxHighlighter MARKDOWN = new MarkdownSyntaxHighlighter();

    private static final Map<String, SyntaxHighlighter> REGISTRY = Map.ofEntries(
            Map.entry("java", JAVA),
            Map.entry("kt", JAVA),
            Map.entry("kts", JAVA),
            Map.entry("scala", JAVA),
            Map.entry("groovy", JAVA),
            Map.entry("gradle", JAVA),

            Map.entry("js", JS),
            Map.entry("jsx", JS),
            Map.entry("mjs", JS),
            Map.entry("cjs", JS),
            Map.entry("ts", JS),
            Map.entry("tsx", JS),
            Map.entry("mts", JS),
            Map.entry("cts", JS),

            Map.entry("py", PYTHON),
            Map.entry("pyw", PYTHON),
            Map.entry("pyi", PYTHON),

            Map.entry("json", JSON),
            Map.entry("json5", JSON),
            Map.entry("geojson", JSON),
            Map.entry("tsbuildinfo", JSON),

            Map.entry("xml", XML),
            Map.entry("fxml", XML),
            Map.entry("html", XML),
            Map.entry("htm", XML),
            Map.entry("xhtml", XML),
            Map.entry("svg", XML),
            Map.entry("xsd", XML),
            Map.entry("xsl", XML),
            Map.entry("xslt", XML),
            Map.entry("dtd", XML),
            Map.entry("tld", XML),
            Map.entry("plist", XML),

            Map.entry("yml", YAML),
            Map.entry("yaml", YAML),

            Map.entry("properties", PROPERTIES),
            Map.entry("ini", PROPERTIES),
            Map.entry("conf", PROPERTIES),
            Map.entry("cfg", PROPERTIES),
            Map.entry("config", PROPERTIES),
            Map.entry("env", PROPERTIES),

            Map.entry("md", MARKDOWN),
            Map.entry("markdown", MARKDOWN),
            Map.entry("mdx", MARKDOWN)
    );

    private SyntaxHighlighterFactory() {
    }

    /**
     * 根据文件扩展名获取高亮器。
     *
     * @param extension 扩展名（不含点，大小写不敏感），如 "java"、"py"、"json"
     * @return 对应的高亮器，未知扩展名返回 {@link PlainTextHighlighter}
     */
    public static SyntaxHighlighter forExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return PLAIN;
        }
        return REGISTRY.getOrDefault(extension.toLowerCase(), PLAIN);
    }

    /**
     * 根据文件路径获取高亮器。
     *
     * @param path 文件路径，从文件名中解析扩展名
     * @return 对应的高亮器
     */
    public static SyntaxHighlighter forPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return PLAIN;
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return PLAIN;
        }
        return forExtension(fileName.substring(dot + 1));
    }
}
