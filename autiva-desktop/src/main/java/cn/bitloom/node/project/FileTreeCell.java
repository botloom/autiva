package cn.bitloom.node.project;

import cn.bitloom.node.FileIconResolver;
import cn.bitloom.node.svg.SvgImageView;
import cn.bitloom.project.git.GitFileStatus;
import cn.bitloom.project.git.ProjectStatusStore;
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

/**
 * 文件树单元格
 * 显示文件名和图标（文件夹 / 文件类型图标）
 * 使用 SvgImageView 加载 SVG 图标，符合 Apple 设计规范。
 *
 * <p>图标解析委托 {@link FileIconResolver}，样式修饰类仍由本类根据扩展名添加。
 * <p>Git 状态着色依赖外部设置的 {@link ProjectStatusStore}（全局共享状态），无则忽略。
 */
@Slf4j
public class FileTreeCell extends TreeCell<Path> {

    private static final double ICON_SIZE = 16;

    /** 全局共享的 Git 状态存储，由 SideBarController 设置 */
    private static ProjectStatusStore statusStore;

    /**
     * 设置全局 Git 状态存储（SideBarController 打开项目时注入）。
     */
    public static void setStatusStore(ProjectStatusStore store) {
        statusStore = store;
    }

    @Override
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll(
                    "file-tree__folder", "file-tree__file",
                    "file-tree__file--code", "file-tree__file--data",
                    "file-tree__file--md", "file-tree__file--text", "file-tree__file--image",
                    "file-tree__file--git-added", "file-tree__file--git-modified",
                    "file-tree__file--git-untracked", "file-tree__folder--git-changed"
            );
            setOnDragDetected(null);
            return;
        }

        String fileName = item.getFileName() != null ? item.getFileName().toString() : item.toString();
        setText(fileName);
        setGraphic(createGraphic(item));

        refreshStyleClasses(item, fileName);

        // 文件与文件夹均可拖拽到对话框
        if (Files.exists(item)) {
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
        SvgImageView icon = new SvgImageView();
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        if (Files.isDirectory(path)) {
            icon.setSvgPath(FileIconResolver.folderIconPath());
        } else {
            icon.setSvgPath(FileIconResolver.resolveIconPath(path.getFileName().toString()));
        }
        return icon;
    }

    private void refreshStyleClasses(Path item, String fileName) {
        getStyleClass().removeAll(
                "file-tree__folder", "file-tree__file",
                "file-tree__file--code", "file-tree__file--data",
                "file-tree__file--md", "file-tree__file--text", "file-tree__file--image",
                "file-tree__file--git-added", "file-tree__file--git-modified",
                "file-tree__file--git-untracked", "file-tree__folder--git-changed"
        );
        if (Files.isDirectory(item)) {
            getStyleClass().add("file-tree__folder");
            if (statusStore != null && statusStore.isDirChanged(item)) {
                getStyleClass().add("file-tree__folder--git-changed");
            }
            return;
        }
        getStyleClass().add("file-tree__file");
        String iconPath = FileIconResolver.resolveIconPath(fileName);
        if (iconPath.endsWith("file-code.svg")) {
            getStyleClass().add("file-tree__file--code");
        } else if (iconPath.endsWith("file-data.svg")) {
            getStyleClass().add("file-tree__file--data");
        } else if (iconPath.endsWith("file-md.svg")) {
            getStyleClass().add("file-tree__file--md");
        } else if (iconPath.endsWith("file-text.svg")) {
            getStyleClass().add("file-tree__file--text");
        } else if (iconPath.endsWith("file-image.svg")) {
            getStyleClass().add("file-tree__file--image");
        }
        if (statusStore != null) {
            GitFileStatus st = statusStore.statusOf(item);
            if (st != null) {
                getStyleClass().add(switch (st) {
                    case ADDED -> "file-tree__file--git-added";
                    case MODIFIED -> "file-tree__file--git-modified";
                    case UNTRACKED -> "file-tree__file--git-untracked";
                });
            }
        }
    }
}
