package cn.bitloom.agentic.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

/**
 * 基于 {@link AgentMemoryStore} 的文件系统实现。
 * <p>
 * 统一路径解析与安全校验（防绝对路径、防 {@code ..} 遍历），6 个记忆工具共享此实例。
 *
 * @see AgentMemoryStore
 */
public class FileSystemAgentMemoryStore implements AgentMemoryStore {

    private final Path memoriesDir;

    public FileSystemAgentMemoryStore(String memoriesDir) {
        this(Paths.get(memoriesDir));
    }

    public FileSystemAgentMemoryStore(Path memoriesDir) {
        this.memoriesDir = memoriesDir.normalize();
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(memoriesDir);
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath);
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    @Override
    public List<String> readLines(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath);
        return Files.readAllLines(target, StandardCharsets.UTF_8);
    }

    @Override
    public void writeFile(String relativePath, String content) throws IOException {
        Path target = resolveSafePath(relativePath);
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void createFile(String relativePath, String content) throws IOException {
        Path target = resolveSafePath(relativePath);
        if (Files.exists(target)) {
            throw new IOException("文件已存在：" + relativePath);
        }
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void delete(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath);
        if (target.equals(this.memoriesDir)) {
            throw new SecurityException("不能删除记忆根目录");
        }
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException("删除失败：" + p, e);
                    }
                });
            }
        } else {
            Files.delete(target);
        }
    }

    @Override
    public void move(String oldPath, String newPath) throws IOException {
        Path source = resolveSafePath(oldPath);
        Path destination = resolveSafePath(newPath);
        Path destParent = destination.getParent();
        if (destParent != null && !Files.exists(destParent)) {
            Files.createDirectories(destParent);
        }
        Files.move(source, destination);
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return Files.exists(resolveSafePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public boolean isDirectory(String relativePath) {
        try {
            return Files.isDirectory(resolveSafePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    @Override
    public long size(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath);
        if (Files.isDirectory(target)) {
            return -1;
        }
        return Files.size(target);
    }

    @Override
    public List<Entry> list(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath);
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target)) {
            List<Path> sorted = stream
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path p : sorted) {
                String name = p.getFileName().toString();
                boolean isDir = Files.isDirectory(p);
                long sz = isDir ? -1 : Files.size(p);
                entries.add(new Entry(name, isDir, sz));
            }
        }
        return entries;
    }

    /**
     * 解析相对路径为安全绝对路径。
     * <ul>
     *   <li>空或 "/" → 记忆根目录</li>
     *   <li>绝对路径 → 拒绝</li>
     *   <li>{@code ..} 遍历 → 拒绝</li>
     * </ul>
     */
    private Path resolveSafePath(String relativePath) {
        if (!StringUtils.hasText(relativePath) || relativePath.equals("/")) {
            return this.memoriesDir;
        }
        Path userPath = Paths.get(relativePath);
        if (userPath.isAbsolute()) {
            throw new SecurityException("不允许绝对路径：'" + relativePath + "'");
        }
        Path resolved = this.memoriesDir.resolve(userPath).normalize();
        if (!resolved.startsWith(this.memoriesDir)) {
            throw new SecurityException("检测到路径遍历尝试：'" + relativePath + "' 逃离记忆目录");
        }
        return resolved;
    }
}
