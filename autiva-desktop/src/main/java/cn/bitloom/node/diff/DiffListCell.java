package cn.bitloom.node.diff;

import cn.bitloom.agentic.tool.file.FileDiff;
import cn.bitloom.node.svg.SvgImageView;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.List;

/**
 * 变更文件列表单元格。
 *
 * <p>富 ListCell 渲染：文件类型图标 + 文件名 + 相对目录 + 变更类型徽章（A/M/D）+ 行数统计（+N -M）。
 * 按扩展名显示对应类型的文件图标，与 FileTreeCell 中的图标解析逻辑保持一致。
 *
 * <p>样式类：
 * <ul>
 *   <li>{@code .diff-list-cell}: 单元格根 HBox</li>
 *   <li>{@code .diff-list-cell__icon}: 文件图标</li>
 *   <li>{@code .diff-list-cell__name}: 文件名 Label</li>
 *   <li>{@code .diff-list-cell__path}: 相对路径 Label（灰色小字）</li>
 *   <li>{@code .diff-list-cell__spacer}: 弹性间隔 Region</li>
 *   <li>{@code .diff-list-cell__badge}: 变更类型徽章 Label</li>
 *   <li>{@code .diff-list-cell__badge--add}/{@code --modify}/{@code --delete}: A/M/D 徽章修饰</li>
 *   <li>{@code .diff-list-cell__stat--add}/{@code --remove}: 行数统计 Label</li>
 * </ul>
 */
public class DiffListCell extends ListCell<FileDiff> {

    private static final double ICON_SIZE = 16;

    @Override
    protected void updateItem(FileDiff item, boolean empty) {
        super.updateItem(item, empty);
        // 先清除上次的状态样式类
        getStyleClass().removeAll("diff-list-cell--add", "diff-list-cell--modify", "diff-list-cell--delete");
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setOnDragDetected(null);
            return;
        }

        String filePath = item.filePath();
        String fileName = extractFileName(filePath);
        String dirPath = extractDirPath(filePath);

        int[] stats = computeStats(item);
        int added = stats[0];
        int removed = stats[1];

        // 按变更类型给整个 cell 加修饰类，由 CSS 控制背景色
        if (item.isCreate()) {
            getStyleClass().add("diff-list-cell--add");
        } else if (item.isDelete()) {
            getStyleClass().add("diff-list-cell--delete");
        } else {
            getStyleClass().add("diff-list-cell--modify");
        }

        setGraphic(buildGraphic(fileName, dirPath, added, removed));
        setText(null);

        // 支持拖拽变更文件到对话框
        setOnDragDetected(event -> {
            File file = new File(filePath);
            Dragboard db = startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(file));
            db.setContent(content);
            event.consume();
        });
    }

    private HBox buildGraphic(String fileName, String dirPath, int added, int removed) {
        SvgImageView icon = new SvgImageView();
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        icon.setSvgPath(resolveIconPath(fileName));
        icon.getStyleClass().add("diff-list-cell__icon");

        Label nameLabel = new Label(fileName);
        nameLabel.getStyleClass().add("diff-list-cell__name");

        Label pathLabel = new Label(dirPath.isEmpty() ? " " : dirPath);
        pathLabel.getStyleClass().add("diff-list-cell__path");

        VBox textBox = new VBox(nameLabel, pathLabel);
        textBox.getStyleClass().add("diff-list-cell__text");
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.NEVER);

        Label addedLabel = new Label("+" + added);
        addedLabel.getStyleClass().addAll("diff-list-cell__stat", "diff-list-cell__stat--add");

        Label removedLabel = new Label("-" + removed);
        removedLabel.getStyleClass().addAll("diff-list-cell__stat", "diff-list-cell__stat--remove");

        HBox statsBox = new HBox(addedLabel, removedLabel);
        statsBox.getStyleClass().add("diff-list-cell__stats");

        HBox root = new HBox(icon, textBox, spacer, statsBox);
        root.getStyleClass().add("diff-list-cell");
        return root;
    }

    private String resolveIconPath(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return "/cn/bitloom/images/file.svg";
        }
        if (CODE_EXTS.contains(ext)) {
            return "/cn/bitloom/images/file-code.svg";
        }
        if (DATA_EXTS.contains(ext)) {
            return "/cn/bitloom/images/file-data.svg";
        }
        if (MD_EXTS.contains(ext)) {
            return "/cn/bitloom/images/file-md.svg";
        }
        if (TEXT_EXTS.contains(ext)) {
            return "/cn/bitloom/images/file-text.svg";
        }
        if (IMAGE_EXTS.contains(ext)) {
            return "/cn/bitloom/images/file-image.svg";
        }
        return "/cn/bitloom/images/file.svg";
    }

    private static int[] computeStats(FileDiff diff) {
        int added = 0;
        int removed = 0;
        if (diff.hunks() == null) {
            return new int[]{0, 0};
        }
        for (FileDiff.Hunk hunk : diff.hunks()) {
            if (hunk.lines() == null) {
                continue;
            }
            for (FileDiff.DiffLine line : hunk.lines()) {
                if (line.type() == FileDiff.Type.ADD) {
                    added++;
                } else if (line.type() == FileDiff.Type.REMOVE) {
                    removed++;
                }
            }
        }
        return new int[]{added, removed};
    }

    private static String extractFileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String extractDirPath(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    // 与 FileTreeCell 保持一致的扩展名集合，供图标解析使用
    private static final java.util.Set<String> CODE_EXTS = java.util.Set.of(
            "java", "kt", "kts", "scala", "groovy", "gradle",
            "js", "jsx", "mjs", "cjs", "ts", "tsx", "mts", "cts",
            "py", "pyw", "pyi",
            "c", "cpp", "cc", "h", "hpp", "go", "rs", "rb", "php",
            "swift", "m", "mm",
            "sh", "bash", "zsh", "bat", "cmd", "ps1"
    );
    private static final java.util.Set<String> DATA_EXTS = java.util.Set.of(
            "json", "json5", "geojson", "tsbuildinfo",
            "xml", "fxml", "html", "htm", "xhtml", "svg",
            "xsd", "xsl", "xslt", "dtd", "tld", "plist",
            "yml", "yaml",
            "properties", "ini", "conf", "cfg", "config", "env", "toml"
    );
    private static final java.util.Set<String> MD_EXTS = java.util.Set.of("md", "markdown", "mdx");
    private static final java.util.Set<String> TEXT_EXTS = java.util.Set.of("txt", "log", "csv", "tsv");
    private static final java.util.Set<String> IMAGE_EXTS = java.util.Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "icns", "tiff", "tif"
    );
}
