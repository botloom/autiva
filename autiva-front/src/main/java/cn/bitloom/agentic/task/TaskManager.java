package cn.bitloom.agentic.task;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class TaskManager {

    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public String create(String subject, String description) {
        Task task = Task.builder()
                .id(idGenerator.getAndIncrement())
                .subject(subject)
                .description(description)
                .status("pending")
                .blockedBy(new ArrayList<>())
                .blocks(new ArrayList<>())
                .owner("")
                .build();
        tasks.put(task.getId(), task);
        return JSON.toJSONString(task);
    }

    public String update(Long taskId, String status, List<Long> addBlockedBy, List<Long> addBlocks) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }

        if (StringUtils.isNoneEmpty(status)) {
            task.setStatus(status);
            if ("completed".equals(status)) {
                this.clearDependency(taskId);
            }
        }

        if (CollectionUtils.isNotEmpty(addBlockedBy)) {
            List<Long> blockedBy = task.getBlockedBy();
            for (Long depId : addBlockedBy) {
                if (!blockedBy.contains(depId)) {
                    blockedBy.add(depId);
                    this.addBlocksEdge(depId, taskId);
                }
            }
            task.setBlockedBy(blockedBy);
        }

        if (CollectionUtils.isNotEmpty(addBlocks)) {
            List<Long> blocks = task.getBlocks();
            for (Long depId : addBlocks) {
                if (!blocks.contains(depId)) {
                    blocks.add(depId);
                    this.addBlockedByEdge(depId, taskId);
                }
            }
            task.setBlocks(blocks);
        }

        tasks.put(taskId, task);
        return JSON.toJSONString(task);
    }

    protected void addBlocksEdge(Long fromTaskId, Long toTaskId) {
        Task task = tasks.get(toTaskId);
        if (task != null) {
            List<Long> blocks = task.getBlocks();
            if (!blocks.contains(fromTaskId)) {
                blocks.add(fromTaskId);
                task.setBlocks(blocks);
                tasks.put(toTaskId, task);
            }
        }
    }

    protected void addBlockedByEdge(Long toTaskId, Long fromTaskId) {
        Task task = tasks.get(toTaskId);
        if (task != null) {
            List<Long> blockedBy = task.getBlockedBy();
            if (!blockedBy.contains(fromTaskId)) {
                blockedBy.add(fromTaskId);
                task.setBlockedBy(blockedBy);
                tasks.put(toTaskId, task);
            }
        }
    }

    protected void clearDependency(Long completedId) {
        for (Task task : tasks.values()) {
            List<Long> blockedBy = task.getBlockedBy();
            if (blockedBy != null && blockedBy.contains(completedId)) {
                blockedBy.remove(completedId);
                task.setBlockedBy(blockedBy);
                tasks.put(task.getId(), task);
            }
        }
    }

    public String getTask(Long taskId) {
        Task task = tasks.get(taskId);
        return task != null ? JSON.toJSONString(task) : null;
    }

    public String list() {
        StringBuilder sb = new StringBuilder();
        for (Task task : tasks.values()) {
            sb.append(task.getStatus()).append(" ");
            sb.append(task.getId()).append(" ");
            sb.append(task.getSubject()).append(" ");
            if (!task.getBlockedBy().isEmpty()) {
                sb.append("blocked by ").append(task.getBlockedBy());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public List<Task> findAll() {
        return new ArrayList<>(tasks.values());
    }

    public Optional<Task> findById(Long taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public List<Task> findByStatus(String status) {
        return tasks.values().stream()
                .filter(task -> status.equals(task.getStatus()))
                .toList();
    }

    public List<Task> findByStatusAndBlockedByIsEmpty(String status) {
        return tasks.values().stream()
                .filter(task -> status.equals(task.getStatus()))
                .filter(task -> task.getBlockedBy() == null || task.getBlockedBy().isEmpty())
                .toList();
    }

    public List<Task> findByStatusAndBlockedByNotEmpty(String status) {
        return tasks.values().stream()
                .filter(task -> status.equals(task.getStatus()))
                .filter(task -> task.getBlockedBy() != null && !task.getBlockedBy().isEmpty())
                .toList();
    }

    public void save(Task task) {
        if (task.getId() == null) {
            task.setId(idGenerator.getAndIncrement());
        }
        tasks.put(task.getId(), task);
    }

    public String scanUnclaimed() {
        List<Task> unclaimed = findByStatusAndBlockedByIsEmpty("pending");
        if (unclaimed.isEmpty()) {
            return "暂无未认领的任务";
        }
        StringBuilder sb = new StringBuilder("未认领任务列表：\n");
        for (Task task : unclaimed) {
            sb.append("- ID: ").append(task.getId())
              .append(", 主题: ").append(task.getSubject())
              .append(", 描述: ").append(task.getDescription())
              .append("\n");
        }
        return sb.toString();
    }

    public String claim(Long taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return "任务不存在: " + taskId;
        }
        if (!"pending".equals(task.getStatus())) {
            return "任务状态不是pending，无法认领";
        }
        if (task.getOwner() != null && !task.getOwner().isEmpty()) {
            return "任务已被其他人认领";
        }
        task.setOwner("Leader");
        tasks.put(taskId, task);
        return "已认领任务: " + taskId;
    }

}
