package cn.bitloom.autiva.agent.tool;

import cn.bitloom.autiva.common.util.UserInputUtil;

import java.util.function.Function;

/**
 * The type Ask human tool.
 *
 * @author bitloom
 */
public class AskHumanTool implements Function<String,String> {

    @Override
    public String apply(String question) {
        System.out.println("Autiva："+question);
        return UserInputUtil.getUserInputFromConsole();
    }

}
