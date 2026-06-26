package cn.bitloom.agentic.diff;

import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Diff 服务
 * 生成和管理文件 Diff，使用 JGit HistogramDiff 算法生成真正的行级 diff
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
        String oldSafe = oldContent != null ? oldContent : "";
        String newSafe = newContent != null ? newContent : "";

        List<FileDiff.Hunk> hunks = generateHunks(oldSafe, newSafe);

        FileDiff diff = new FileDiff(id, filePath.toString(), hunks, isCreate, isDelete, oldSafe);
        pendingDiffs.put(id, diff);
        log.info("生成 Diff: {} ({})", filePath, id);
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
     * 批准 Diff（仅从待审核列表移除，不修改文件）
     */
    public void approveDiff(String diffId) {
        pendingDiffs.remove(diffId);
        log.info("批准 Diff: {}", diffId);
    }

    /**
     * 拒绝 Diff 并回滚文件内容到 oldContent
     * 新建文件 → 删除；修改文件 → 恢复旧内容
     */
    public void rejectDiff(String diffId) {
        FileDiff diff = pendingDiffs.remove(diffId);
        if (diff == null) {
            return;
        }
        try {
            Path path = Paths.get(diff.filePath());
            if (diff.isCreate()) {
                Files.deleteIfExists(path);
            } else if (diff.oldContent() != null) {
                Files.writeString(path, diff.oldContent(), StandardCharsets.UTF_8);
            }
            log.info("撤销 Diff 并回滚文件: {} ({})", diff.filePath(), diffId);
        } catch (IOException e) {
            log.error("回滚文件失败: {} ({})", diff.filePath(), diffId, e);
        }
    }

    /**
     * 使用 JGit HistogramDiff 生成行级 Hunks
     * 算法对代码重构场景更友好，输出可读性优于 MyersDiff
     */
    private List<FileDiff.Hunk> generateHunks(String oldContent, String newContent) {
        List<FileDiff.Hunk> hunks = new ArrayList<>();
        RawText a = new RawText(oldContent.getBytes(StandardCharsets.UTF_8));
        RawText b = new RawText(newContent.getBytes(StandardCharsets.UTF_8));
        DiffAlgorithm algo = new HistogramDiff();
        EditList edits = algo.diff(RawTextComparator.DEFAULT, a, b);

        for (Edit edit : edits) {
            List<FileDiff.DiffLine> lines = new ArrayList<>();
            int oldBegin = edit.getBeginA();
            int oldEnd = edit.getEndA();
            int newBegin = edit.getBeginB();
            int newEnd = edit.getEndB();
            for (int i = oldBegin; i < oldEnd; i++) {
                lines.add(new FileDiff.DiffLine(FileDiff.Type.REMOVE, a.getString(i)));
            }
            for (int j = newBegin; j < newEnd; j++) {
                lines.add(new FileDiff.DiffLine(FileDiff.Type.ADD, b.getString(j)));
            }
            if (!lines.isEmpty()) {
                hunks.add(new FileDiff.Hunk(
                        oldBegin + 1, oldEnd - oldBegin,
                        newBegin + 1, newEnd - newBegin,
                        lines));
            }
        }
        return hunks;
    }
}
