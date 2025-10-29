package cn.bitloom.autiva.agentic.flow.config;

import cn.bitloom.autiva.agentic.flow.graph.Graph;
import lombok.Builder;
import lombok.Data;

/**
 * 工作流配置类，表示整个工作流的配置
 *
 * @author ningyu
 */
@Data
@Builder
public class WorkflowConfig {
    /**
     * 工作流ID
     */
    private String id;

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 十字链表图
     */
    private Graph graph;
}