package cn.bitloom.agentic.workflow.graph;

import lombok.Data;

/**
 * The type Vertex node.
 *
 * @author ningyu
 */
@Data
public class VertexNode {
    private String id;
    private String type;
    private String name;
    private VertexParam params;
    private ArcNode firstOut;
    private ArcNode firstIn;
}
