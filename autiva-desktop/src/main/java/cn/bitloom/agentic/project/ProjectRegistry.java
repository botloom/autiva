package cn.bitloom.agentic.project;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 项目注册表管理器
 * 管理已注册的项目列表，持久化到 ~/.autiva/projects/registry.json
 */
@Slf4j
@Component
public class ProjectRegistry {

    private final List<ProjectInfo> projects = new CopyOnWriteArrayList<>();
    private final GitService gitService;

    public ProjectRegistry(GitService gitService) {
        this.gitService = gitService;
        load();
    }

    /**
     * 列出所有已注册项目
     */
    public List<ProjectInfo> listProjects() {
        return new ArrayList<>(projects);
    }

    /**
     * 根据 ID 查找项目
     */
    public Optional<ProjectInfo> findById(String id) {
        return projects.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /**
     * 创建新项目（创建空目录并注册）
     */
    public ProjectInfo createProject(String name) throws IOException {
        String id = UUID.randomUUID().toString();
        Path projectDir = AppConstants.Base.PROJECTS_DIR.resolve(name);
        Files.createDirectories(projectDir);

        String branch = gitService.getCurrentBranch(projectDir).orElse(null);
        ProjectInfo project = new ProjectInfo(id, name, projectDir.toString(), branch, Instant.now());
        projects.add(project);
        persist();
        log.info("创建项目: {} ({})", name, projectDir);
        return project;
    }

    /**
     * 注册本地文件夹为项目
     */
    public ProjectInfo registerLocal(String path, String name) throws IOException {
        Path projectPath = Paths.get(path);
        if (!Files.isDirectory(projectPath)) {
            throw new IOException("路径不是有效目录: " + path);
        }

        String id = UUID.randomUUID().toString();
        String branch = gitService.getCurrentBranch(projectPath).orElse(null);
        ProjectInfo project = new ProjectInfo(id, name, projectPath.toString(), branch, Instant.now());
        projects.add(project);
        persist();
        log.info("注册本地项目: {} ({})", name, projectPath);
        return project;
    }

    /**
     * 移除项目（仅从注册表移除，不删除文件）
     */
    public void removeProject(String id) {
        projects.removeIf(p -> p.id().equals(id));
        persist();
        log.info("移除项目: {}", id);
    }

    /**
     * 刷新项目的 Git 分支信息
     */
    public ProjectInfo refreshBranch(String id) {
        Optional<ProjectInfo> opt = findById(id);
        if (opt.isEmpty()) {
            return null;
        }
        ProjectInfo old = opt.get();
        String branch = gitService.getCurrentBranch(Paths.get(old.path())).orElse(null);
        ProjectInfo updated = new ProjectInfo(old.id(), old.name(), old.path(), branch, old.createdAt());
        projects.removeIf(p -> p.id().equals(id));
        projects.add(updated);
        persist();
        return updated;
    }

    /**
     * 从磁盘加载项目列表
     */
    private void load() {
        try {
            Path registryFile = AppConstants.Base.PROJECTS_REGISTRY_FILE;
            if (!Files.exists(registryFile)) {
                return;
            }
            String json = Files.readString(registryFile);
            List<ProjectInfo> loaded = JsonUtils.fromJson(json, new TypeReference<List<ProjectInfo>>() {});
            projects.clear();
            projects.addAll(loaded);
            log.info("加载项目注册表: {} 个项目", projects.size());
        } catch (Exception e) {
            log.warn("加载项目注册表失败", e);
        }
    }

    /**
     * 持久化项目列表到磁盘
     */
    private void persist() {
        try {
            Path registryFile = AppConstants.Base.PROJECTS_REGISTRY_FILE;
            Files.createDirectories(registryFile.getParent());
            String json = JsonUtils.toJson(projects);
            Files.writeString(registryFile, json);
        } catch (Exception e) {
            log.error("持久化项目注册表失败", e);
        }
    }
}
