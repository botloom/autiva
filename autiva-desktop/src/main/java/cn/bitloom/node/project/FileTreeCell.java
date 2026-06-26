package cn.bitloom.node.project;

import cn.bitloom.node.svg.SvgImageView;
import javafx.scene.control.TreeCell;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件树单元格
 * 显示文件名和图标（文件夹/文件类型区分）
 * 使用 SvgImageView 加载 SVG 图标，符合 Apple 设计规范
 */
@Slf4j
public class FileTreeCell extends TreeCell<Path> {

    private static final String FOLDER_SVG = "/cn/bitloom/images/folder.svg";
    private static final String FILE_SVG = "/cn/bitloom/images/file.svg";
    private static final double ICON_SIZE = 16;

    @Override
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        String fileName = item.getFileName() != null ? item.getFileName().toString() : item.toString();
        setText(fileName);
        setGraphic(createIcon(item));

        // 添加样式类
        getStyleClass().removeAll("file-tree__folder", "file-tree__file");
        if (Files.isDirectory(item)) {
            getStyleClass().add("file-tree__folder");
        } else {
            getStyleClass().add("file-tree__file");
        }
    }

    /**
     * 创建图标
     */
    private SvgImageView createIcon(Path path) {
        SvgImageView icon = new SvgImageView();
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        if (Files.isDirectory(path)) {
            icon.setSvgPath(FOLDER_SVG);
        } else {
            icon.setSvgPath(FILE_SVG);
        }
        return icon;
    }
}
