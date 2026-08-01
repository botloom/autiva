/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package cn.bitloom.agentic.tool.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆插入工具，在现有记忆文件的特定行号插入文本。
 *
 * @author Christian Tzolov
 */
public class MemoryInsertTool extends AbstractTool<MemoryInsertTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "要修改的文件路径，相对于记忆根目录。") String path,
		@ToolParam(description = "插入文本后的行号。使用 0 在第一行前插入。") Integer insertLine,
		@ToolParam(description = "要插入的文本。") String insertText
	) {}

	private MemoryInsertTool(Path memoriesDir) {
		super("MemoryInsert",
			  "在现有记忆文件的特定行号插入文本。行号从 1 开始，传递总行数在末尾追加。",
			  Input.class);
		this.memoriesDir = memoriesDir.normalize();
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			Path target = resolveSafePath(input.path());

			if (!Files.exists(target)) {
				return ToolResult.error("错误：文件不存在：" + input.path());
			}

			if (Files.isDirectory(target)) {
				return ToolResult.error("错误：路径是目录，不是文件：" + input.path());
			}

			if (input.insertLine() == null || input.insertLine() < 0) {
				return ToolResult.error("错误：insert_line 必须是非负整数");
			}

			List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);

			if (input.insertLine() > lines.size()) {
				return ToolResult.error(String.format("错误：insert_line %d 超过文件长度 %d 行",
						input.insertLine(), lines.size()));
			}

			// 检测原始文件是否以换行符结尾，以便恢复它
			String originalContent = Files.readString(target, StandardCharsets.UTF_8);
			boolean trailingNewline = originalContent.endsWith("\n");

			lines.add(input.insertLine(), input.insertText() != null ? input.insertText() : "");

			String updated = String.join("\n", lines) + (trailingNewline ? "\n" : "");
			Files.writeString(target, updated, StandardCharsets.UTF_8);

			return ToolResult.success("成功在 " + input.path() + " 的第 " + input.insertLine() + " 行插入文本");
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("插入文件错误：" + e.getMessage());
		}
	}

	private Path resolveSafePath(String relativePath) {
		if (relativePath == null || relativePath.isBlank() || relativePath.equals("/")) {
			throw new SecurityException("路径不能为空");
		}
		Path userPath = Paths.get(relativePath);
		if (userPath.isAbsolute()) {
			throw new SecurityException("不允许绝对路径：'" + relativePath + "'");
		}
		Path resolved = this.memoriesDir.resolve(userPath).normalize();
		if (!resolved.startsWith(this.memoriesDir)) {
			throw new SecurityException("检测到路径遍历尝试：'" + relativePath + "' 逃离记忆目录");
		}
		return resolved;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path memoriesDir = Paths.get("/memories");

		private Builder() {
		}

		public Builder memoriesDir(Path memoriesDir) {
			this.memoriesDir = memoriesDir;
			return this;
		}

		public Builder memoriesDir(String memoriesDir) {
			this.memoriesDir = memoriesDir != null ? Paths.get(memoriesDir) : Paths.get("/memories");
			return this;
		}

		public MemoryInsertTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryInsertTool(memoriesDir);
		}
	}
}