package cn.bitloom.agentic.workflow;

import cn.bitloom.agentic.workflow.builtin.CodeReviewWorkflow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Workflow 注册表（宿主侧）：{@code name → WorkflowMeta}。
 *
 * <p>模型只见 name + description + argsSchema，<strong>不见可执行代码</strong> —
 * 编排形状固定在宿主代码里（s16 纪律），模型只负责选择与传参。
 */
@Component
public class WorkflowRegistry {

    /** 工作流元数据：name（模型可见名）、description、argsSchema、编排函数 */
    public record WorkflowMeta(String name, String description, String argsSchema,
            BiFunction<WorkflowContext, Map<String, Object>, Object> function) {
    }

    private final Map<String, WorkflowMeta> workflows = new ConcurrentHashMap<>();

    public WorkflowRegistry() {
        register(new CodeReviewWorkflow().meta());
    }

    public void register(WorkflowMeta meta) {
        workflows.put(meta.name(), meta);
    }

    public Optional<WorkflowMeta> find(String name) {
        return Optional.ofNullable(workflows.get(name));
    }

    public List<WorkflowMeta> list() {
        return workflows.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
    }

    /** 工具描述用的清单文本（name + description + argsSchema） */
    public String describeAll() {
        StringBuilder sb = new StringBuilder();
        for (WorkflowMeta meta : list()) {
            sb.append("- ").append(meta.name()).append(": ").append(meta.description())
                    .append("\n  args: ").append(meta.argsSchema()).append("\n");
        }
        return sb.toString();
    }
}
