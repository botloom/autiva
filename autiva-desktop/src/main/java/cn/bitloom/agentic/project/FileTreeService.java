package cn.bitloom.agentic.project;

import cn.bitloom.agentic.tool.ToolUtils;
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
 * 构建项目目录树，支持延迟加载和忽略目录过滤
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
        TreeItem<Path> rootItem = new TreeItem<>(rootPath);
        rootItem.setExpanded(true);

        // 延迟加载子节点
        addLazyChildren(rootItem);

        return rootItem;
    }

    /**
     * 为节点添加延迟加载的子节点
     */
    private void addLazyChildren(TreeItem<Path> parent) {
        Path parentPath = parent.getValue();

        // 设置展开监听器，首次展开时加载子节点
        parent.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
            if (isNowExpanded && parent.getChildren().isEmpty()) {
                loadChildren(parent);
            }
        });

        // 首次加载根节点的直接子节点
        if (parent.getParent() == null) {
            loadChildren(parent);
        }
    }

    /**
     * 加载子节点
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
                        // 过滤忽略目录
                        if (ToolUtils.isIgnoredPath(child)) {
                            return;
                        }

                        TreeItem<Path> childItem = new TreeItem<>(child);
                        parent.getChildren().add(childItem);

                        // 如果是目录，设置延迟加载
                        if (Files.isDirectory(child)) {
                            addLazyChildren(childItem);
                        }
                    });
        } catch (IOException e) {
            log.warn("加载目录失败: {}", parentPath, e);
        }
    }
}
