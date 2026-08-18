package cn.bitloom.project.git;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件变化监听服务
 * 递归监听项目根目录下的文件新增/修改/删除，去抖（debounce）后在后台重算 Git 状态，
 * 并通过 {@link ProjectStatusStore#update} 触发 UI（目录树 / 已打开文件视图）刷新。
 * 仅监听非忽略目录，且跳过 .git 等高频写入目录以避免无效刷新。
 */
@Slf4j
@Component
public class ProjectFileWatcherService {

    private static final long DEBOUNCE_MS = 600;

    private final ProjectStatusStore projectStatusStore;
    private final GitStatusService gitStatusService;

    private final WatchService watchService;
    /** 仅负责调度目录注册与去抖刷新任务，不得提交阻塞死循环（监听循环跑在专用线程上） */
    private final ExecutorService executor;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
    /** 已注册的 Git 元数据目录（.git 根 / .git/refs 及其子目录），用于识别 Git 提交引发的刷新 */
    private final Set<Path> gitMetaDirs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Path currentRoot;
    private volatile boolean refreshScheduled = false;
    private volatile Thread pollThread;

    public ProjectFileWatcherService(ProjectStatusStore projectStatusStore,
                                     GitStatusService gitStatusService) throws IOException {
        this.projectStatusStore = projectStatusStore;
        this.gitStatusService = gitStatusService;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "autiva-file-watcher-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 开始递归监听指定项目根。若已监听其他根则先停止再监听。
     * 监听循环运行在独立守护线程，保证调度线程上的去抖刷新任务不被阻塞。
     */
    public synchronized void watch(Path root) {
        if (root == null) {
            return;
        }
        Path abs = root.toAbsolutePath().normalize();
        if (running.get() && abs.equals(currentRoot)) {
            return;
        }
        stopInternal();
        currentRoot = abs;
        running.set(true);
        executor.execute(this::registerAll);
        Thread t = new Thread(this::pollLoop, "autiva-file-watcher");
        t.setDaemon(true);
        this.pollThread = t;
        t.start();
    }

    /**
     * 停止监听。
     */
    public synchronized void stop() {
        stopInternal();
    }

    private void stopInternal() {
        running.set(false);
        currentRoot = null;
        keyToDir.keySet().forEach(k -> k.cancel());
        keyToDir.clear();
        gitMetaDirs.clear();
        // 中断监听线程，唤醒可能阻塞在 take() 的循环使其退出
        Thread t = pollThread;
        if (t != null) {
            t.interrupt();
            pollThread = null;
        }
    }

    private void registerAll() {
        if (currentRoot == null || !running.get()) {
            return;
        }
        Set<Path> dirs = gitStatusService.collectWatchDirs(currentRoot);
        for (Path dir : dirs) {
            registerDir(dir);
        }
        registerGitMetaDirs(currentRoot);
    }

    /**
     * 额外注册 Git 元数据目录（.git 根 / .git/refs 及其子目录），
     * 使 git commit/add 等变更 HEAD/索引/引用后能触发状态刷新（提交后工作区改动应被清空重算）。
     * 不注册 .git/objects 等高频写入目录，避免无效刷新。
     */
    private void registerGitMetaDirs(Path root) {
        Path gitDir = root.resolve(".git");
        if (!java.nio.file.Files.isDirectory(gitDir)) {
            return;
        }
        gitMetaDirs.add(gitDir.toAbsolutePath().normalize());
        registerDir(gitDir);
        collectRefDirs(gitDir.resolve("refs"));
    }

    private void collectRefDirs(Path refsDir) {
        if (!java.nio.file.Files.isDirectory(refsDir)) {
            return;
        }
        gitMetaDirs.add(refsDir.toAbsolutePath().normalize());
        registerDir(refsDir);
        try (var stream = java.nio.file.Files.list(refsDir)) {
            stream.filter(java.nio.file.Files::isDirectory)
                    .forEach(this::collectRefDirs);
        } catch (Exception e) {
            log.debug("扫描 Git refs 目录失败: {}", refsDir, e);
        }
    }

    private void registerDir(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            keyToDir.put(key, dir);
        } catch (IOException e) {
            log.debug("注册监听目录失败（可能已不存在）: {}", dir, e);
        }
    }

    private void pollLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Path dir = keyToDir.get(key);
            boolean relevant = false;
            if (dir != null) {
                // Git 元数据目录（.git 根 / refs）的事件：多为 commit/add 导致 HEAD/索引/引用变化，
                // 即使路径位于 .git 下也应触发状态重算（否则提交后工作区改动不会被清空重算）
                boolean isGitMeta = gitMetaDirs.contains(dir.toAbsolutePath().normalize());
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path child = dir.resolve((Path) event.context());
                    if (isGitMeta) {
                        relevant = true;
                        continue;
                    }
                    if (gitStatusService.isIgnoredPath(child)) {
                        continue;
                    }
                    relevant = true;
                    // 新增目录需要补注册，以覆盖其子树
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            && child.toFile().isDirectory()) {
                        executor.execute(this::registerAll);
                    }
                }
            }
            key.reset();
            if (relevant) {
                onChanged();
            }
        }
    }

    /**
     * 记录一次变化，去抖合并后统一触发 Git 状态重算与刷新。
     * 去抖通过单线程串行调度：若已有待处理的刷新任务，则重置并发原点不重复创建。
     */
    private void onChanged() {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        executor.execute(() -> {
            try {
                Thread.sleep(DEBOUNCE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                refreshScheduled = false;
                return;
            }
            synchronized (ProjectFileWatcherService.this) {
                refreshScheduled = false;
                if (!running.get() || currentRoot == null) {
                    return;
                }
                refreshStatus();
            }
        });
    }

    private void refreshStatus() {
        Path root = currentRoot;
        if (root == null) {
            return;
        }
        try {
            projectStatusStore.update(root, gitStatusService.queryStatusMap(root));
        } catch (Exception e) {
            log.warn("刷新 Git 状态失败: {}", root, e);
        }
    }

    /** 供 App 关闭时调用释放资源 */
    @PreDestroy
    public void destroy() {
        stopInternal();
        try {
            watchService.close();
        } catch (IOException e) {
            log.warn("关闭文件监听失败", e);
        }
        executor.shutdownNow();
    }
}
