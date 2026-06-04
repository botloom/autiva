package cn.bitloom.agentic.agent.subagent;

import org.springframework.ai.tool.annotation.ToolParam;

public record TaskCall(
        @ToolParam(description = "任务的简短（3-5个词）描述") String description,
        @ToolParam(description = "代理要执行的任务") String prompt,
        @ToolParam(description = "用于此任务的专门代理类型") String subagentName,
        @ToolParam(description = "要从中恢复的可选代理ID。如果提供，代理将从之前的执行记录继续。", required = false) String resume,
        @ToolParam(description = "设置为true以在后台运行此代理。稍后使用TaskOutput读取输出。", required = false) Boolean runInBackground) {
}