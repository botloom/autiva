package cn.bitloom.project;

import cn.bitloom.agentic.tool.ToolUtils;
import cn.bitloom.node.project.LazyTreeItem;
import javafx.scene.control.TreeItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件树服务
 * 构建项目目录树，使用 LazyTreeItem 实现延迟加载和正确的目录展开行为。
 */
@Slf4j
@Component
public class FileTreeService {

    /**
     * 构建文件树
     *
     * @param rootPath 项目根路径
     * @return TreeItem 根节点
     */
    public TreeItem<Path> buildFileTree(Path rootPath) {
        LazyTreeItem rootItem = new LazyTreeItem(rootPath, this::loadChildren);
        rootItem.setExpanded(true);
        return rootItem;
    }

    /**
     * 加载子节点
     * 为每个子节点创建 LazyTreeItem，传入 this::loadChildren 作为延迟加载回调。
     */
    private void loadChildren(TreeItem<Path> parent) {
        Path parentPath = parent.getValue();
        if (!Files.isDirectory(parentPath)) {
            return;
        }

        try (Stream<Path> stream = Files.list(parentPath)) {
            stream.sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(child -> {
                        if (ToolUtils.isIgnoredPath(child)) {
                            return;
                        }
                        LazyTreeItem childItem = new LazyTreeItem(child, this::loadChildren);
                        parent.getChildren().add(childItem);
                    });
        } catch (IOException e) {
            log.warn("加载目录失败: {}", parentPath, e);
        }
    }
}
