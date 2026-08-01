package cn.bitloom.project;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Git 服务
 * 仅查询 Git 信息（如当前分支），不支持修改操作
 */
@Slf4j
@Component
public class GitService {

    private static final String GIT_DIR = ".git";

    /**
     * 获取指定路径的当前 Git 分支
     *
     * @param projectPath 项目路径
     * @return 分支名称，非 Git 仓库或查询失败返回 empty
     */
    public Optional<String> getCurrentBranch(Path projectPath) {
        if (projectPath == null || !Files.isDirectory(projectPath)) {
            return Optional.empty();
        }

        // 检查是否是 Git 仓库
        if (!isGitRepository(projectPath)) {
            return Optional.empty();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Git 命令超时: {}", projectPath);
                return Optional.empty();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String branch = reader.readLine();
                if (branch != null && !branch.isEmpty() && process.exitValue() == 0) {
                    return Optional.of(branch.trim());
                }
            }
        } catch (IOException | InterruptedException e) {
            log.warn("获取 Git 分支失败: {}", projectPath, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    /**
     * 检查路径是否是 Git 仓库
     */
    public boolean isGitRepository(Path projectPath) {
        if (projectPath == null || !Files.isDirectory(projectPath)) {
            return false;
        }
        // 检查当前目录或父目录是否存在 .git
        Path current = projectPath;
        while (current != null) {
            if (Files.exists(current.resolve(GIT_DIR))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
