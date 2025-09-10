package cn.bitloom.autiva.agentic.tool;

import cn.bitloom.autiva.agentic.core.AbstractAgent;
import cn.bitloom.autiva.agentic.enums.AgentStateEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The type Base tool.
 *
 * @author bitloom
 */
@Slf4j
public class CommonTools extends AbstractTools<AbstractAgent> {

    /**
     * Instantiates a new Abstract tools.
     *
     * @param AGENT the agent
     */
    public CommonTools(AbstractAgent AGENT) {
        super(AGENT);
    }

    /**
     * Ask human string.
     *
     * @param question the question
     * @return the string
     */
//    @Tool(name = "askHuman", description = "当遇见解答不了的问题或执行不了的操作时，用俏皮的语气使用这个工具寻求人的帮助。")
//    public String askHuman(@ToolParam(description = "提问的问题") String question) {
//        log.info("[CommonTools]-[askHuman],question={}", question);
//        System.out.println("Autiva>"+question);
//        return UserInputUtil.getUserInputFromConsole();
//    }

    /**
     * Terminate string.
     *
     * @param state the state
     * @return the string
     */
    @Tool(name = "terminate", description = "当用户要求停止聊天或聊天异常中断时，使用这个工具改变Agent的状态")
    public String terminate(@ToolParam(description = "停止后Agent的状态，如果是正常停止，则是FINISHED；异常停止就是ERROR") String state) {
        log.info("[CommonTools]-[terminate],state:{}", state);
        this.AGENT.getState().set(AgentStateEnum.getByName(state));
        return "交互已成功完成，状态为：" + state;
    }

}
