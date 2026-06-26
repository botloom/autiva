package cn.bitloom.node.project;

import javafx.scene.control.TreeItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 延迟加载的文件树节点
 * 重写 isLeaf() 基于文件系统实际类型判断，确保目录节点始终显示展开箭头。
 * 首次展开时通过回调加载子节点。
 */
public class LazyTreeItem extends TreeItem<Path> {

    private final Consumer<TreeItem<Path>> loadChildrenCallback;
    private boolean loaded = false;

    public LazyTreeItem(Path path, Consumer<TreeItem<Path>> loadChildrenCallback) {
        super(path);
        this.loadChildrenCallback = loadChildrenCallback;
        expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
            if (isNowExpanded && !loaded) {
                loaded = true;
                loadChildrenCallback.accept(this);
            }
        });
    }

    @Override
    public boolean isLeaf() {
        Path p = getValue();
        if (p == null) {
            return true;
        }
        try {
            return !Files.isDirectory(p);
        } catch (Exception e) {
            return true;
        }
    }
}
