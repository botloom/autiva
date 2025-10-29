package cn.bitloom.autiva.agentic.flow;

import reactor.core.publisher.Flux;

/**
 * 工作流接口，定义工作流的基本操作
 *
 * @author  ningyu
 */
public interface IWorkflow {

    /**
     * 使用Flux启动工作流
     *
     * @return  Flux<WorkFlowEvent> 工作流事件流
     */
    Flux<WorkFlowEvent> start();

    /**
     * 获取工作流名称
     *
     * @return  工作流名称
     */
    String getName();
}
