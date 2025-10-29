package cn.bitloom.autiva.agentic.flow;


import cn.bitloom.autiva.agentic.flow.graph.Graph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 工作流工厂类，用于创建不同类型的工作流实例
 *
 * @author ningyu
 */
@Component
@RequiredArgsConstructor
public class WorkflowFactory {

    private final WorkflowConfigLoader workflowConfigLoader;

    /**
     * 代码创建工作流
     *
     * @param id      the id
     * @param name    the name
     * @param graph   the graph
     * @param context the context
     * @return the workflow
     */
    public IWorkflow createWorkFlow(String id, String name, Graph graph, WorkFlowContext context) {
        WorkflowConfig workflowConfig = WorkflowConfig.builder()
                .id(id)
                .name(name)
                .graph(graph)
                .build();
        return new GraphWorkflow(workflowConfig, context);
    }

    /**
     * 从配置文件创建工作流实例
     *
     * @param configPath 配置文件路径
     * @param context    the context
     * @return 工作流实例
     */
    public IWorkflow createWorkflowFromConfig(String configPath, WorkFlowContext context) {
        WorkflowConfig workflowConfig = workflowConfigLoader.loadWorkflowConfig(configPath);
        return new GraphWorkflow(workflowConfig, context);
    }

}