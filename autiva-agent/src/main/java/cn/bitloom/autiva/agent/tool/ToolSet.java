package cn.bitloom.autiva.agent.tool;

import cn.bitloom.autiva.agent.core.AbstractAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The type Tool config.
 *
 * @author bitloom
 */
@Configuration(proxyBeanMethods = false)
public class ToolSet {

    public static final String ASK_HUMAN_TOOL = "askHuman";
    public static final String TERMINATE_TOOL = "terminate";

    @Bean(ASK_HUMAN_TOOL)
    @Description("当遇见解答不了的问题时，使用这个工具寻求人的帮助。")
    public Function<String, String> askHuman() {
        return new AskHumanTool();
    }

    @Bean(TERMINATE_TOOL)
    @Description("当请求得到满足时或当助手无法继续执行任务时，终止此交互。")
    public BiFunction<String, AbstractAgent, String> terminate() {
        return new TerminateTool();
    }

}
