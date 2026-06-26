package cn.bitloom.agentic.diff;

import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Diff 服务
 * 生成和管理文件 Diff
 */
@Slf4j
@Component
public class DiffService {

    private final ConcurrentMap<String, FileDiff> pendingDiffs = new ConcurrentHashMap<>();

    /**
     * 生成 Diff
     *
     * @param filePath    文件路径
     * @param oldContent  旧内容（null 表示新建文件）
     * @param newContent  新内容（null 表示删除文件）
     * @return FileDiff
     */
    public FileDiff generateDiff(Path filePath, String oldContent, String newContent) {
        String id = UUID.randomUUID().toString();
        boolean isCreate = oldContent == null;
        boolean isDelete = newContent == null;

        List<FileDiff.Hunk> hunks = generateHunks(
                oldContent != null ? oldContent : "",
                newContent != null ? newContent : ""
        );

        FileDiff diff = new FileDiff(id, filePath.toString(), hunks, isCreate, isDelete);
        pendingDiffs.put(id, diff);
        log.info("生成 Diff: {} ({})", filePath, id);
        // 发布 DiffEvent 通知 UI 层刷新 diff 列表
        EventBus.publishOut(DiffEvent.of(null, diff));
        return diff;
    }

    /**
     * 获取待审核的 Diff 列表
     */
    public List<FileDiff> getPendingDiffs() {
        return new ArrayList<>(pendingDiffs.values());
    }

    /**
     * 批准 Diff
     */
    public void approveDiff(String diffId) {
        pendingDiffs.remove(diffId);
        log.info("批准 Diff: {}", diffId);
    }

    /**
     * 拒绝 Diff
     */
    public void rejectDiff(String diffId) {
        pendingDiffs.remove(diffId);
        log.info("拒绝 Diff: {}", diffId);
    }

    /**
     * 生成 Hunks（简化的行级 Diff）
     */
    private List<FileDiff.Hunk> generateHunks(String oldContent, String newContent) {
        List<FileDiff.Hunk> hunks = new ArrayList<>();
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // 简化实现：将所有变化作为一个 Hunk
        List<FileDiff.DiffLine> lines = new ArrayList<>();

        // 添加旧内容作为 REMOVE 行
        for (String line : oldLines) {
            if (!line.isEmpty()) {
                lines.add(new FileDiff.DiffLine(FileDiff.Type.REMOVE, line));
            }
        }

        // 添加新内容作为 ADD 行
        for (String line : newLines) {
            if (!line.isEmpty()) {
                lines.add(new FileDiff.DiffLine(FileDiff.Type.ADD, line));
            }
        }

        if (!lines.isEmpty()) {
            hunks.add(new FileDiff.Hunk(1, oldLines.length, 1, newLines.length, lines));
        }

        return hunks;
    }
}
