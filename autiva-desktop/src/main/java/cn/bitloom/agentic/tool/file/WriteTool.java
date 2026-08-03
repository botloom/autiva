package cn.bitloom.agentic.tool.file;

import cn.bitloom.agentic.event.DiffEvent;
import cn.bitloom.agentic.event.EventPublisher;
import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件写入工具，将内容写入本地文件系统。
 */
@Slf4j
public class WriteTool extends AbstractTool<WriteTool.Input> {

	private static final String DESCRIPTION = """
			将文件写入磁盘，覆盖已有文件。编辑现有文件前必须先 Read。优先编辑现有文件。不要主动创建文档文件(*.md/README)。
			""";

	private final DiffGenerator diffGenerator;

	private WriteTool(DiffGenerator diffGenerator) {
		super("Write", DESCRIPTION, Input.class);
		this.diffGenerator = diffGenerator;
	}

	public record Input(
		@ToolParam(description = "要写入的文件的绝对路径（必须是绝对路径，不是相对路径）") String filePath,
		@ToolParam(description = "要写入文件的内容") String content
	) {}

	@Override
	public ToolResult execute(Input input, ToolContext context) {
		String filePath = input.filePath();
		String content = input.content() != null ? input.content() : "";

		try {
			Path path = Paths.get(filePath);
			File file = path.toFile();

			boolean fileExists = file.exists();
			String action = fileExists ? "覆盖" : "创建";

			File parentDir = file.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					return ToolResult.error("无法创建父目录: " + filePath);
				}
			}

			String oldContent = fileExists
					? Files.readString(path, StandardCharsets.UTF_8)
					: null;

			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
				writer.write(content);
			}

			// 写入后生成 diff 并通过 EventPublisher 推送 DiffEvent（非阻塞，失败不影响写入结果）
			if (diffGenerator != null) {
				try {
					FileDiff fileDiff = diffGenerator.generateDiff(path, oldContent, content);
					publishDiffEvent(context, fileDiff);
				} catch (Exception e) {
					log.warn("生成 Diff 失败（不影响写入）: {}", filePath, e);
				}
			}

			String message = String.format("已%s %s（%d字节）", action, filePath, content.length());

			Map<String, Object> data = new LinkedHashMap<>();
			data.put("file", filePath);
			data.put("bytes", content.length());
			data.put("action", action);

			return ToolResult.success(message, data);

		}
		catch (IOException e) {
			return ToolResult.error("写入文件时出错: " + e.getMessage());
		}
		catch (Exception e) {
			return ToolResult.error("错误: " + e.getMessage());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private DiffGenerator diffGenerator;

		public Builder diffGenerator(DiffGenerator diffGenerator) {
			this.diffGenerator = diffGenerator;
			return this;
		}

		public WriteTool build() {
			return new WriteTool(diffGenerator);
		}
	}

	/**
	 * 从 ToolContext 获取 EventPublisher 和 sessionId，推送 DiffEvent 到 agent 事件流。
	 * eventSink 或 sessionId 为 null 时跳过（work 模式无 diff 或测试场景）。
	 */
	private static void publishDiffEvent(ToolContext context, FileDiff fileDiff) {
		if (context == null || fileDiff == null) return;
		Object sinkObj = context.getContext().get("eventSink");
		Object sessionIdObj = context.getContext().get("sessionId");
		if (sinkObj instanceof EventPublisher publisher && sessionIdObj instanceof String sessionId) {
			try {
				publisher.publish(DiffEvent.of(sessionId, fileDiff));
			} catch (Exception e) {
				log.warn("推送 DiffEvent 失败（不影响写入）: {}", fileDiff.filePath(), e);
			}
		}
	}

	private static String extractString(ToolContext context, String key) {
		if (context == null || context.getContext() == null) return null;
		Object v = context.getContext().get(key);
		return v instanceof String s ? s : null;
	}

}
