package cn.bitloom.autiva.web;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * The type Test tool.
 *
 * @author bitloom
 */
@Slf4j
@Component
public class TestTool {

    @McpTool(description = "hello")
    public String test(){
        return "hello";
    }

}
