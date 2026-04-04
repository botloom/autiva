package cn.bitloom.agentic.workflow.graph;

import lombok.Data;

/**
 * The type Arc node.
 *
 * @author ningyu
 */
@Data
public class ArcNode {
    private String tailVexId;
    private String headVexId;
    private ArcNode tailLink;
    private ArcNode headLink;
    private ArcParam parms;
}
