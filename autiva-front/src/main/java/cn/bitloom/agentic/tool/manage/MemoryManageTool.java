package cn.bitloom.agentic.tool.manage;

import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.agentic.agent.AgentManager;
import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
public class MemoryManageTool {

    private final AgentManager agentManager;

    private MemoryManageTool(AgentManager agentManager) {
        Assert.notNull(agentManager, "agentManager不能为null");
        this.agentManager = agentManager;
    }

    @Tool(name = "memory_manage_list", description = "列出指定智能体的记忆文件列表")
    public ToolResult listMemoryFiles(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName
    ) {
        log.info("[ToolCall] memory_manage_list - 列出记忆文件: agent={}", agentName);
        Path workspaceDir = AppConstants.Base.WORKSPACE_DIR.resolve(agentName);
        if (!Files.exists(workspaceDir)) {
            return ToolResult.error("智能体工作目录不存在: " + agentName);
        }
        StringBuilder sb = new StringBuilder("智能体 ").append(agentName).append(" 的文件列表：\n\n");
        try (Stream<Path> paths = Files.list(workspaceDir)) {
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
                .message("智能体 " + agentName + " 的记忆文件列表")
                .rawOutput(sb.toString())
                .build();
    }

    @Tool(name = "memory_manage_read", description = "读取指定智能体的记忆文件内容")
    public ToolResult readMemoryFile(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "文件名（如MEMORY.md、USER.md）") String fileName
    ) {
        log.info("[ToolCall] memory_manage_read - 读取记忆文件: agent={}, file={}", agentName, fileName);
        Path filePath = AppConstants.Base.WORKSPACE_DIR.resolve(agentName).resolve(fileName);
        if (!Files.exists(filePath)) {
            return ToolResult.error("文件不存在: " + filePath);
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return ToolResult.builder().status(ToolResult.Status.SUCCESS)
                    .message("读取文件: " + fileName)
                    .rawOutput(content)
                    .build();
        } catch (IOException e) {
            return ToolResult.error("读取文件失败: " + e.getMessage());
        }
    }

    @Tool(name = "memory_manage_write", description = "写入或更新指定智能体的记忆文件内容")
    public ToolResult writeMemoryFile(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "文件名（如MEMORY.md、USER.md）") String fileName,
            @ToolParam(description = "文件内容") String content
    ) {
        log.info("[ToolCall] memory_manage_write - 写入记忆文件: agent={}, file={}", agentName, fileName);
        Path filePath = AppConstants.Base.WORKSPACE_DIR.resolve(agentName).resolve(fileName);
        try {
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            return ToolResult.success("文件已更新: " + fileName);
        } catch (IOException e) {
            return ToolResult.error("写入文件失败: " + e.getMessage());
        }
    }

    @Tool(name = "memory_manage_delete", description = "删除指定智能体的记忆文件（破坏性操作，需确认）")
    public ToolResult deleteMemoryFile(
            @ToolParam(description = "智能体名称（MAIN或DOCTOR）") String agentName,
            @ToolParam(description = "要删除的文件名") String fileName
    ) {
        log.info("[ToolCall] memory_manage_delete - 删除记忆文件: agent={}, file={}", agentName, fileName);
        Path filePath = AppConstants.Base.WORKSPACE_DIR.resolve(agentName).resolve(fileName);
        try {
            if (Files.deleteIfExists(filePath)) {
                return ToolResult.success("文件已删除: " + fileName);
            } else {
                return ToolResult.error("文件不存在: " + fileName);
            }
        } catch (IOException e) {
            return ToolResult.error("删除文件失败: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentManager agentManager;

        public Builder agentManager(AgentManager agentManager) {
            this.agentManager = agentManager;
            return this;
        }

        public MemoryManageTool build() {
            return new MemoryManageTool(agentManager);
        }
    }
}
