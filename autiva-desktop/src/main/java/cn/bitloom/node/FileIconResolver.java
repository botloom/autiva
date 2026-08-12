package cn.bitloom.node;

import java.util.Set;

/**
 * 文件图标解析工具类。
 *
 * <p>根据文件扩展名返回对应的 SVG 图标路径，供文件树、Diff 列表、输入框 tag 等复用。
 * 扩展名分类与图标资源保持单一来源，避免多处复制导致不一致。
 */
public final class FileIconResolver {

    private static final String FOLDER_SVG = "/cn/bitloom/images/folder-light.svg";
    private static final String DEFAULT_FILE_SVG = "/cn/bitloom/images/file.svg";
    private static final String CODE_FILE_SVG = "/cn/bitloom/images/file-code.svg";
    private static final String DATA_FILE_SVG = "/cn/bitloom/images/file-data.svg";
    private static final String MD_FILE_SVG = "/cn/bitloom/images/file-md.svg";
    private static final String TEXT_FILE_SVG = "/cn/bitloom/images/file-text.svg";
    private static final String IMAGE_FILE_SVG = "/cn/bitloom/images/file-image.svg";

    private static final Set<String> CODE_EXTS = Set.of(
            "java", "kt", "kts", "scala", "groovy", "gradle",
            "js", "jsx", "mjs", "cjs", "ts", "tsx", "mts", "cts",
            "py", "pyw", "pyi",
            "c", "cpp", "cc", "h", "hpp", "go", "rs", "rb", "php",
            "swift", "m", "mm",
            "sh", "bash", "zsh", "bat", "cmd", "ps1"
    );

    private static final Set<String> DATA_EXTS = Set.of(
            "json", "json5", "geojson", "tsbuildinfo",
            "xml", "fxml", "html", "htm", "xhtml", "svg",
            "xsd", "xsl", "xslt", "dtd", "tld", "plist",
            "yml", "yaml",
            "properties", "ini", "conf", "cfg", "config", "env", "toml"
    );

    private static final Set<String> MD_EXTS = Set.of("md", "markdown", "mdx");
    private static final Set<String> TEXT_EXTS = Set.of("txt", "log", "csv", "tsv");
    private static final Set<String> IMAGE_EXTS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "icns", "tiff", "tif"
    );

    private FileIconResolver() {
    }

    /**
     * 根据文件名解析图标路径。
     *
     * @param fileName 文件名（含扩展名）
     * @return SVG 资源路径
     */
    public static String resolveIconPath(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return DEFAULT_FILE_SVG;
        }
        if (CODE_EXTS.contains(ext)) {
            return CODE_FILE_SVG;
        }
        if (DATA_EXTS.contains(ext)) {
            return DATA_FILE_SVG;
        }
        if (MD_EXTS.contains(ext)) {
            return MD_FILE_SVG;
        }
        if (TEXT_EXTS.contains(ext)) {
            return TEXT_FILE_SVG;
        }
        if (IMAGE_EXTS.contains(ext)) {
            return IMAGE_FILE_SVG;
        }
        return DEFAULT_FILE_SVG;
    }

    /**
     * 文件夹图标路径。
     */
    public static String folderIconPath() {
        return FOLDER_SVG;
    }

    /**
     * 默认文件图标路径（兜底）。
     */
    public static String defaultFileIconPath() {
        return DEFAULT_FILE_SVG;
    }

    /**
     * 文本片段图标路径（用于输入框文本 tag）。
     */
    public static String textSnippetIconPath() {
        return TEXT_FILE_SVG;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
