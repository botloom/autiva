package cn.bitloom.project.git;

import cn.bitloom.project.GitService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Git 状态查询服务
 * 基于 jgit 查询项目工作区的文件状态（新增/修改/未跟踪），供目录树与文件视图着色。
 */
@Slf4j
@Component
public class GitStatusService {

    private final GitService gitService;

    public GitStatusService(GitService gitService) {
        this.gitService = gitService;
    }

    /**
     * 返回项目根下所有改动文件的「绝对规范化路径 → 状态」映射。
     * 已跟踪且无改动、被忽略的文件不在 map 中；非 Git 仓库返回空 map。
     *
     * @param projectRoot 项目根路径
     */
    public Map<Path, GitFileStatus> queryStatusMap(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot) || !gitService.isGitRepository(projectRoot)) {
            return Map.of();
        }
        try (Git git = Git.open(projectRoot.toFile())) {
            Status status = git.status().call();
            Map<Path, GitFileStatus> map = new HashMap<>();
            // 状态优先：ADDED > MODIFIED > UNTRACKED（putIfAbsent 保留更高优先级）
            collect(status.getAdded(), GitFileStatus.ADDED, projectRoot, map);
            collect(status.getModified(), GitFileStatus.MODIFIED, projectRoot, map);
            collect(status.getChanged(), GitFileStatus.MODIFIED, projectRoot, map);
            collect(status.getMissing(), GitFileStatus.MODIFIED, projectRoot, map);
            collect(status.getUntracked(), GitFileStatus.UNTRACKED, projectRoot, map);
            return map;
        } catch (Exception e) {
            log.warn("查询 Git 状态失败: {}", projectRoot, e);
            return Map.of();
        }
    }

    private void collect(Set<String> paths, GitFileStatus status, Path projectRoot, Map<Path, GitFileStatus> map) {
        if (paths == null) {
            return;
        }
        for (String p : paths) {
            Path abs = projectRoot.resolve(p).toAbsolutePath().normalize();
            map.putIfAbsent(abs, status);
        }
    }

    /**
     * 从状态映射推导「含改动的目录」的绝对规范化路径集合，用于目录节点弱着色。
     */
    public Set<Path> collectChangedDirs(Map<Path, GitFileStatus> statusMap) {
        Set<Path> dirs = new HashSet<>();
        for (Path abs : statusMap.keySet()) {
            Path parent = abs.getParent();
            while (parent != null) {
                dirs.add(parent.toAbsolutePath().normalize());
                parent = parent.getParent();
            }
        }
        return dirs;
    }

    /**
     * 计算单文件工作区相对 HEAD 的行级改动（键：工作区行号 0-based → 状态）。
     * 供文本编辑器行号处的按行着色使用。
     * <p>状态映射规则：
     * <ul>
     *   <li>INSERT → 新增行标记 {@link GitFileStatus#ADDED}（未跟踪新文件视为全部新增）</li>
     *   <li>REPLACE → 内容被修改的行标记 {@link GitFileStatus#MODIFIED}</li>
     *   <li>DELETE → 删除锚点落在删除后紧跟的那一行，标记 {@link GitFileStatus#MODIFIED}</li>
     * </ul>
     *
     * @param projectRoot Git 仓库根路径
     * @param filePath    目标文件
     * @return 行号 → 状态映射；非 Git 仓库 / 读取失败 / 文件在根外返回空 map
     */
    public Map<Integer, GitFileStatus> diffLineStatus(Path projectRoot, Path filePath) {
        return diffLineStatus(projectRoot, filePath, null);
    }

    /**
     * 计算单文件相对 HEAD 的行级改动，工作区内容可显式传入（供编辑器实时标注：编辑未保存时以内存文本参与 diff，
     * 而非磁盘上的旧内容）；{@code workingContent} 为 null 时按原逻辑读取磁盘内容。
     *
     * @param projectRoot     Git 仓库根路径
     * @param filePath        目标文件
     * @param workingContent  编辑中的工作区内容（可为 null，此时读磁盘）
     * @return 行号 → 状态映射；非 Git 仓库 / 读取失败 / 文件在根外返回空 map
     */
    public Map<Integer, GitFileStatus> diffLineStatus(Path projectRoot, Path filePath, String workingContent) {
        Map<Integer, GitFileStatus> result = new HashMap<>();
        if (projectRoot == null || filePath == null || !Files.isRegularFile(filePath)
                || !gitService.isGitRepository(projectRoot)) {
            return result;
        }
        Path absFile = filePath.toAbsolutePath().normalize();
        Path absRoot = projectRoot.toAbsolutePath().normalize();
        if (!absFile.startsWith(absRoot)) {
            return result;
        }
        String relPath = absRoot.relativize(absFile).toString().replace('\\', '/');
        try (Git git = Git.open(absRoot.toFile())) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            String headContent = normalizeLineEndings(readHeadContent(repo, head, relPath));
            String curContent = normalizeLineEndings(
                    workingContent != null ? workingContent : Files.readString(absFile, StandardCharsets.UTF_8));
            RawText a = new RawText(headContent.getBytes(StandardCharsets.UTF_8));
            RawText b = new RawText(curContent.getBytes(StandardCharsets.UTF_8));
            DiffAlgorithm algo = new HistogramDiff();
            EditList edits = algo.diff(RawTextComparator.DEFAULT, a, b);
            int bSize = b.size();
            for (Edit e : edits) {
                switch (e.getType()) {
                    case INSERT -> {
                        for (int i = e.getBeginB(); i < e.getEndB(); i++) {
                            result.put(i, GitFileStatus.ADDED);
                        }
                    }
                    case DELETE -> {
                        int anchor = e.getBeginB();
                        if (anchor >= bSize) {
                            anchor = Math.max(0, bSize - 1);
                        }
                        result.putIfAbsent(anchor, GitFileStatus.MODIFIED);
                    }
                    case REPLACE -> {
                        for (int i = e.getBeginB(); i < e.getEndB(); i++) {
                            result.put(i, GitFileStatus.MODIFIED);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("计算行级 diff 失败: {}", absFile, e);
        }
        return result;
    }

    /**
     * 统一换行符为 LF，避免因工作区（autocrlf 下为 CRLF）与 HEAD（LF）行尾差异
     * 导致 diff 将每一行都判定为改动。
     */
    private static String normalizeLineEndings(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 读取 HEAD 版本中指定文件的文本内容（相对仓库根）。
     */
    private String readHeadContent(Repository repo, ObjectId head, String relPath) {
        if (head == null) {
            return "";
        }
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(head);
            try (TreeWalk tw = TreeWalk.forPath(repo, relPath, commit.getTree())) {
                if (tw == null) {
                    return "";
                }
                ObjectLoader loader = repo.open(tw.getObjectId(0));
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 递归列出项目根下所有需监听的子目录（过滤忽略目录），供文件监听使用。
     */
    public Set<Path> collectWatchDirs(Path projectRoot) {
        Set<Path> dirs = new HashSet<>();
        collectWatchDirsRecursive(projectRoot, dirs);
        return dirs;
    }

    private void collectWatchDirsRecursive(Path dir, Set<Path> dirs) {
        dirs.add(dir);
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !isIgnored(p))
                    .forEach(child -> collectWatchDirsRecursive(child, dirs));
        } catch (Exception e) {
            log.warn("扫描目录失败: {}", dir, e);
        }
    }

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "build", ".idea",
            ".vscode", "dist", "__pycache__", ".gradle", ".mvn"
    );

    private boolean isIgnored(Path path) {
        for (Path part : path) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为应忽略的路径（供监听事件过滤）。
     */
    public boolean isIgnoredPath(Path path) {
        if (path == null) {
            return true;
        }
        Path abs = path.toAbsolutePath();
        for (Path part : abs) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
