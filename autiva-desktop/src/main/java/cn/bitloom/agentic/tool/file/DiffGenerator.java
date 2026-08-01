package cn.bitloom.agentic.tool.file;

import java.nio.file.Path;

/**
 * 文件 diff 生成器接口（依赖倒置）。
 * core 层的 WriteTool/EditTool 依赖此接口，实现由应用层提供。
 * <p>
 * 返回生成的 {@link FileDiff}，由调用方（WriteTool/EditTool）通过
 * EventPublisher 推送到 agent 事件流，不再由实现方直接发布事件。
 */
@FunctionalInterface
public interface DiffGenerator {
    /**
     * 生成文件 diff 并返回 FileDiff 对象。
     *
     * @param filePath   文件路径
     * @param oldContent 旧内容（null 表示新建文件）
     * @param newContent 新内容（null 表示删除文件）
     * @return 生成的 FileDiff
     */
    FileDiff generateDiff(Path filePath, String oldContent, String newContent);
}
