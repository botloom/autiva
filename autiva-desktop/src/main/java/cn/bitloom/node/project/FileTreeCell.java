package cn.bitloom.node.project;

import cn.bitloom.node.svg.SvgImageView;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 文件树单元格
 * 显示文件名和图标（文件夹 / 文件类型图标）
 * 使用 SvgImageView 加载 SVG 图标，符合 Apple 设计规范。
 *
 * <p>根据文件扩展名选择不同的 SVG 图标和样式修饰类，
 * 提供视觉上的文件类型区分：
 * <ul>
 *   <li>代码文件（.java/.js/.py 等）→ file-code.svg（青色）</li>
 *   <li>数据/配置文件（.json/.yaml/.xml 等）→ file-data.svg（橙色）</li>
 *   <li>Markdown 文件（.md）→ file-md.svg（蓝色）</li>
 *   <li>纯文本文件（.txt/.log）→ file-text.svg（灰色）</li>
 *   <li>图片文件（.png/.jpg 等）→ file-image.svg（绿色）</li>
 *   <li>其他 → file.svg（白色，兜底）</li>
 * </ul>
 */
@Slf4j
public class FileTreeCell extends TreeCell<Path> {

    private static final String FOLDER_SVG = "/cn/bitloom/images/folder-light.svg";
    private static final String DEFAULT_FILE_SVG = "/cn/bitloom/images/file.svg";
    private static final String CODE_FILE_SVG = "/cn/bitloom/images/file-code.svg";
    private static final String DATA_FILE_SVG = "/cn/bitloom/images/file-data.svg";
    private static final String MD_FILE_SVG = "/cn/bitloom/images/file-md.svg";
    private static final String TEXT_FILE_SVG = "/cn/bitloom/images/file-text.svg";
    private static final String IMAGE_FILE_SVG = "/cn/bitloom/images/file-image.svg";

    private static final double ICON_SIZE = 16;

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

    @Override
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll(
                    "file-tree__folder", "file-tree__file",
                    "file-tree__file--code", "file-tree__file--data",
                    "file-tree__file--md", "file-tree__file--text", "file-tree__file--image"
            );
            setOnDragDetected(null);
            return;
        }

        String fileName = item.getFileName() != null ? item.getFileName().toString() : item.toString();
        setText(fileName);
        setGraphic(createGraphic(item));

        refreshStyleClasses(item, fileName);

        // 仅文件可拖拽到对话框（目录无意义）
        if (Files.isRegularFile(item)) {
            setOnDragDetected(event -> {
                Dragboard db = startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putFiles(List.of(item.toFile()));
                db.setContent(content);
                event.consume();
            });
        } else {
            setOnDragDetected(null);
        }
    }

    /**
     * 创建节点图形：文件/文件夹图标。
     */
    private Node createGraphic(Path path) {
        return createIcon(path);
    }

    private SvgImageView createIcon(Path path) {
        SvgImageView icon = new SvgImageView();
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        if (Files.isDirectory(path)) {
            icon.setSvgPath(FOLDER_SVG);
        } else {
            icon.setSvgPath(resolveFileSvg(path.getFileName().toString()));
        }
        return icon;
    }

    private String resolveFileSvg(String fileName) {
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

    private void refreshStyleClasses(Path item, String fileName) {
        getStyleClass().removeAll(
                "file-tree__folder", "file-tree__file",
                "file-tree__file--code", "file-tree__file--data",
                "file-tree__file--md", "file-tree__file--text", "file-tree__file--image"
        );
        if (Files.isDirectory(item)) {
            getStyleClass().add("file-tree__folder");
            return;
        }
        getStyleClass().add("file-tree__file");
        String ext = extensionOf(fileName);
        if (ext == null) {
            return;
        }
        if (CODE_EXTS.contains(ext)) {
            getStyleClass().add("file-tree__file--code");
        } else if (DATA_EXTS.contains(ext)) {
            getStyleClass().add("file-tree__file--data");
        } else if (MD_EXTS.contains(ext)) {
            getStyleClass().add("file-tree__file--md");
        } else if (TEXT_EXTS.contains(ext)) {
            getStyleClass().add("file-tree__file--text");
        } else if (IMAGE_EXTS.contains(ext)) {
            getStyleClass().add("file-tree__file--image");
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
