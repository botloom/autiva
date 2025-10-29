package cn.bitloom.autiva.agentic.flow;


import cn.bitloom.autiva.agentic.flow.graph.VertexParam;

import java.util.Map;

/**
 * The type Abstract workflow node.
 *
 * @author ningyu
 */
public abstract class AbstractNode {

    /**
     * 根据输入执行该步骤，返回该步骤的输出（用作下一个步骤的输入）
     *
     * @param params the node params
     * @param ctx    the ctx
     * @return the result
     * @throws Exception the exception
     */
    public abstract Map<String, Object> run(VertexParam params, WorkFlowContext ctx) throws Exception;

}
