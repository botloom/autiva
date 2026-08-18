package cn.bitloom.agentic.tool.file;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Diff 服务
 * 生成和管理文件 Diff，使用 JGit HistogramDiff 算法生成真正的行级 diff。
 * 实现 {@link DiffGenerator} 接口，供 core 层的 WriteTool/EditTool 通过依赖倒置调用。
 */
@Slf4j
@Component
public class DiffService implements DiffGenerator {

    private final ConcurrentMap<String, FileDiff> pendingDiffs = new ConcurrentHashMap<>();

    /**
     * 生成 Diff（实现 {@link DiffGenerator} 接口）。
     *
     * @param filePath    文件路径
     * @param oldContent  旧内容（null 表示新建文件）
     * @param newContent  新内容（null 表示删除文件）
     * @return 生成的 FileDiff（已存入 pendingDiffs，由调用方负责发布 DiffEvent）
     */
    @Override
    public FileDiff generateDiff(Path filePath, String oldContent, String newContent) {
        // 同一文件只保留最新一个 pending diff：移除同路径的旧 diff，避免审查列表重复
        pendingDiffs.values().removeIf(pending -> pending.filePath().equals(filePath.toString()));
        String id = UUID.randomUUID().toString();
        boolean isCreate = oldContent == null;
        boolean isDelete = newContent == null;
        String oldSafe = oldContent != null ? oldContent : "";
        String newSafe = newContent != null ? newContent : "";

        List<FileDiff.Hunk> hunks = generateHunks(oldSafe, newSafe);

        FileDiff diff = new FileDiff(id, filePath.toString(), hunks, isCreate, isDelete, oldSafe);
        pendingDiffs.put(id, diff);
        log.info("[DiffService] 生成 Diff: {} ({}), hunks={}", filePath, id, hunks.size());
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
     * 批准文件 Diff（直接基于 FileDiff 对象，不依赖 pendingDiffs）
     */
    public void approveFileDiff(FileDiff diff) {
        pendingDiffs.remove(diff.id());
        log.info("批准文件 Diff: {}", diff.filePath());
    }

    /**
     * 拒绝文件 Diff 并回滚文件内容（直接基于 FileDiff 对象）
     */
    public void rejectFileDiff(FileDiff diff) {
        pendingDiffs.remove(diff.id());
        try {
            Path path = Paths.get(diff.filePath());
            if (diff.isCreate()) {
                Files.deleteIfExists(path);
            } else if (diff.oldContent() != null) {
                Files.writeString(path, diff.oldContent(), StandardCharsets.UTF_8);
            }
            log.info("撤销文件 Diff 并回滚: {}", diff.filePath());
        } catch (IOException e) {
            log.error("回滚文件失败: {}", diff.filePath(), e);
        }
    }

    /**
     * 扫描 Git 工作区未提交变更
     * <p>
     * 用 JGit status 扫描工作区，对比 HEAD 版本生成 FileDiff 列表。
     * 不存入 pendingDiffs，仅返回供 UI 显示。
     */
    public List<FileDiff> scanWorkingTreeDiffs(Path projectPath) {
        List<FileDiff> result = new ArrayList<>();
        if (!Files.isDirectory(projectPath.resolve(".git"))) {
            return result;
        }
        try (Git git = Git.open(projectPath.toFile())) {
            Repository repo = git.getRepository();
            Status status = git.status().call();
            ObjectId head = repo.resolve("HEAD");
            Path workTree = repo.getWorkTree().toPath();

            for (String path : status.getModified()) {
                String oldContent = readHeadContent(repo, head, path);
                Path filePath = workTree.resolve(path);
                String newContent = Files.exists(filePath) ? Files.readString(filePath, StandardCharsets.UTF_8) : "";
                List<FileDiff.Hunk> hunks = generateHunks(oldContent, newContent);
                result.add(new FileDiff(UUID.randomUUID().toString(),
                        filePath.toString(), hunks, false, false, oldContent));
            }
            for (String path : status.getUntracked()) {
                Path filePath = workTree.resolve(path);
                if (!Files.isRegularFile(filePath)) continue;
                String newContent = Files.readString(filePath, StandardCharsets.UTF_8);
                List<FileDiff.Hunk> hunks = generateHunks("", newContent);
                result.add(new FileDiff(UUID.randomUUID().toString(),
                        filePath.toString(), hunks, true, false, ""));
            }
            for (String path : status.getMissing()) {
                String oldContent = readHeadContent(repo, head, path);
                List<FileDiff.Hunk> hunks = generateHunks(oldContent, "");
                result.add(new FileDiff(UUID.randomUUID().toString(),
                        workTree.resolve(path).toString(), hunks, false, true, oldContent));
            }
        } catch (Exception e) {
            log.error("扫描工作区 diff 失败: {}", projectPath, e);
        }
        return result;
    }

    /**
     * 读取 HEAD 版本中指定文件的内容
     */
    private String readHeadContent(Repository repo, ObjectId head, String path) {
        if (head == null) return "";
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(head);
            try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, commit.getTree())) {
                if (treeWalk == null) return "";
                ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 重新计算 Diff（基于当前文件内容）
     * <p>
     * 用于历史对话场景：文件可能已被进一步修改，需要用 JGit 重新生成 hunks。
     * 不存储/发布事件，仅返回新的 FileDiff 供 UI 显示。
     * 读取文件失败时返回原始 diff。
     */
    public FileDiff recomputeDiff(FileDiff original) {
        Path filePath = Paths.get(original.filePath());
        String oldContent = original.oldContent() != null ? original.oldContent() : "";
        String newContent = "";
        try {
            if (Files.exists(filePath)) {
                newContent = Files.readString(filePath, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("重新计算 Diff 时读取文件失败: {}, 使用原始 diff", filePath, e);
            return original;
        }
        List<FileDiff.Hunk> hunks = generateHunks(oldContent, newContent);
        return new FileDiff(original.id(), original.filePath(), hunks,
                original.isCreate(), original.isDelete(), original.oldContent());
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
