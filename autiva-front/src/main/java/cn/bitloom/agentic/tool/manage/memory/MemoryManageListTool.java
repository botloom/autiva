package cn.bitloom.agentic.tool.manage.memory;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 列出指定智能体的记忆文件列表。
 */
@Slf4j
public class MemoryManageListTool extends AbstractTool<MemoryManageListTool.Input> {

    private static final String DESCRIPTION = "列出指定智能体的记忆文件列表";

    private MemoryManageListTool() {
        super("memory_manage_list", DESCRIPTION, Input.class);
    }

    public record Input(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName
    ) {}

    @Override
    public ToolResult execute(Input input, ToolContext toolContext) {
        log.info("[ToolCall] memory_manage_list - 列出记忆文件: agent={}", input.agentName());
        Path agentDir = "default".equals(input.agentName())
                ? AppConstants.Base.APP_DIR
                : AppConstants.Base.WORKSPACE_DIR.resolve(input.agentName());
        if (!Files.exists(agentDir)) {
            return ToolResult.error("智能体工作目录不存在: " + input.agentName());
        }
        StringBuilder sb = new StringBuilder("智能体 ").append(input.agentName()).append(" 的文件列表：\n\n");
        try (Stream<Path> paths = Files.list(agentDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        long size = 0;
                        try {
                            size = Files.size(p);
                        } catch (IOException ignored) {
                        }
                        sb.append("- ").append(fileName).append(" (").append(size).append(" bytes)\n");
                    });
        } catch (IOException e) {
            return ToolResult.error("列出文件失败: " + e.getMessage());
        }
        return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                .message("智能体 " + input.agentName() + " 的记忆文件列表")
                .rawOutput(sb.toString())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Builder() {}

        public MemoryManageListTool build() {
            return new MemoryManageListTool();
        }
    }
}
