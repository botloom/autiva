package cn.bitloom.agentic.permission;

import cn.bitloom.agentic.permission.command.CommandLineParser;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 批准存储 — 管理项目目录下 {@code .autiva/command-approvals.json} 的读写。
 *
 * <p>文件位置：{@code <项目目录>/.autiva/command-approvals.json}
 *
 * <p>线程安全：
 * <ul>
 *   <li>内存数据用 {@link CopyOnWriteArrayList}（读多写少）</li>
 *   <li>持久化用 {@code synchronized} 串行写</li>
 *   <li>读取无锁（{@code volatile} 内存可见性）</li>
 * </ul>
 */
@Slf4j
public class ApprovalStore {

    private static final String APPROVALS_DIR = ".autiva";
    private static final String APPROVALS_FILE = "command-approvals.json";
    private static final int FILE_VERSION = 1;

    private final Path projectDir;
    private final Path approvalsFile;
    private volatile ApprovalData data;

    public ApprovalStore(Path projectDir) {
        this.projectDir = projectDir;
        this.approvalsFile = projectDir.resolve(APPROVALS_DIR).resolve(APPROVALS_FILE);
        this.data = load();
    }

    /**
     * 命令前缀是否已被永久批准。
     */
    public boolean isAllowed(String commandPrefix) {
        return data.allow.contains(normalize(commandPrefix));
    }

    /**
     * 命令前缀是否已被永久拒绝。
     */
    public boolean isDenied(String commandPrefix) {
        return data.deny.contains(normalize(commandPrefix));
    }

    /**
     * 添加永久批准。
     */
    public synchronized void allow(String commandPrefix) {
        String normalized = normalize(commandPrefix);
        if (data.allow.add(normalized)) {
            data.updatedAt = LocalDateTime.now().toString();
            persist();
            log.info("[ApprovalStore] 永久批准: {} (写入 {})", normalized, approvalsFile);
        }
    }

    /**
     * 添加永久拒绝。
     */
    public synchronized void deny(String commandPrefix) {
        String normalized = normalize(commandPrefix);
        if (data.deny.add(normalized)) {
            data.updatedAt = LocalDateTime.now().toString();
            persist();
            log.info("[ApprovalStore] 永久拒绝: {} (写入 {})", normalized, approvalsFile);
        }
    }

    /**
     * 加载文件。文件不存在或解析失败时返回空数据（不抛异常）。
     */
    private ApprovalData load() {
        try {
            if (!Files.exists(approvalsFile)) {
                log.debug("[ApprovalStore] 文件不存在，初始化空数据: {}", approvalsFile);
                return ApprovalData.empty();
            }
            String content = Files.readString(approvalsFile);
            ApprovalData loaded = JsonUtils.fromJson(content, new TypeReference<ApprovalData>() {});
            if (loaded == null) {
                return ApprovalData.empty();
            }
            if (loaded.allow == null) loaded.allow = new CopyOnWriteArrayList<>();
            if (loaded.deny == null) loaded.deny = new CopyOnWriteArrayList<>();
            log.info("[ApprovalStore] 加载成功: allow={}, deny={}, file={}",
                    loaded.allow.size(), loaded.deny.size(), approvalsFile);
            return loaded;
        } catch (Exception e) {
            log.warn("[ApprovalStore] 加载失败，使用空数据: file={}, error={}", approvalsFile, e.getMessage());
            return ApprovalData.empty();
        }
    }

    /**
     * 原子写文件：先写 .tmp，再 Files.move 覆盖。
     */
    private void persist() {
        try {
            Files.createDirectories(approvalsFile.getParent());
            Path tmp = approvalsFile.resolveSibling(APPROVALS_FILE + ".tmp");
            String json = JsonUtils.toJson(data);
            Files.writeString(tmp, json);
            Files.move(tmp, approvalsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[ApprovalStore] 持久化失败: file={}, error={}", approvalsFile, e.getMessage(), e);
        } catch (Throwable t) {
            // 兜底：JsonUtils 可能抛 RuntimeException
            log.error("[ApprovalStore] 持久化异常: file={}, error={}", approvalsFile, t.getMessage(), t);
        }
    }

    /**
     * 规范化命令前缀：trim + 转小写。
     * 注意：命令前缀不区分大小写（cmd 也不区分），保留原始大小写意义不大。
     */
    private static String normalize(String prefix) {
        return prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 批准数据 — JSON 序列化结构。
     */
    public static class ApprovalData {
        public int version = FILE_VERSION;
        public CopyOnWriteArrayList<String> allow;
        public CopyOnWriteArrayList<String> deny;
        public String updatedAt;

        public ApprovalData() {
        }

        static ApprovalData empty() {
            ApprovalData d = new ApprovalData();
            d.allow = new CopyOnWriteArrayList<>();
            d.deny = new CopyOnWriteArrayList<>();
            d.updatedAt = LocalDateTime.now().toString();
            return d;
        }
    }

    /**
     * 从原始命令提取批准 key（前缀）。
     * 例：{@code "git push --force"} → {@code "git push"}，{@code "npm install axios"} → {@code "npm install"}，{@code "mkdir"} → {@code "mkdir"}
     */
    public static String extractPrefix(String command) {
        return CommandLineParser.extractPrefix(command);
    }
}
