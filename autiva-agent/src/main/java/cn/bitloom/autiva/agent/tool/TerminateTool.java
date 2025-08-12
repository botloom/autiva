package cn.bitloom.autiva.agent.tool;

import cn.bitloom.autiva.agent.core.AbstractAgent;
import cn.bitloom.autiva.common.enums.AgentStateEnum;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * The type Terminate tool.
 *
 * @author bitloom
 */
public class TerminateTool implements BiFunction<String, AbstractAgent, String> {

    @Override
    public String apply(@ToolParam(description = "停止后Agent的状态，如果是正常停止，则是FINISHED；异常停止就是ERROR") String state, AbstractAgent agent) {
        agent.getSTATE().set(AgentStateEnum.getByName(state));
        return "交互已成功完成，状态为：" + state;
    }

}
