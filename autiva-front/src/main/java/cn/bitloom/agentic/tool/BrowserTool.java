package cn.bitloom.agentic.tool;

import cn.bitloom.util.BrowserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserTool implements ITool {

    @Tool(name = "navigateBrowser", description = "在已打开的浏览器窗口中导航到指定URL")
    public ToolResult navigate(@ToolParam(description = "窗口名称") String windowName, @ToolParam(description = "要访问的URL地址") String url) {
        log.info("[ToolCall] navigateBrowser - 导航浏览器: windowName={}, url={}", windowName, url);
        try {
            BrowserManager.open(url);
            log.info("[ToolCall] navigateBrowser - 导航成功");
            return ToolResult.success("已导航到: " + url);
        } catch (Exception e) {
            log.error("[ToolCall] navigateBrowser - 导航失败", e);
            return ToolResult.failure("导航失败: " + e.getMessage());
        }
    }

}
