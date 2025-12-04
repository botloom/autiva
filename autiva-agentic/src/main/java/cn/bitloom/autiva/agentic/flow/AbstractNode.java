package cn.bitloom.autiva.agentic.flow;


import cn.bitloom.autiva.agentic.flow.graph.VertexParam;
import reactor.core.publisher.Mono;

/**
 * The type Abstract workflow node.
 *
 * @author ningyu
 */
public abstract class AbstractNode {

    /**
     * 根据输入执行该步骤，返回该步骤的输出（用作下一个步骤的输入），注意：如果没有结果返回一个空Map，不要返回null
     *
     * @param params the node params
     * @param ctx    the ctx
     * @return the mono
     */
    public abstract Mono<WorkFlowEvent> run(VertexParam params, WorkFlowContext ctx);

}
