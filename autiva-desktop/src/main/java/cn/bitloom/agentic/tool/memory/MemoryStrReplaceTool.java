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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆字符串替换工具，替换现有记忆文件中的精确字符串。
 *
 * @author Christian Tzolov
 */
public class MemoryStrReplaceTool extends AbstractTool<MemoryStrReplaceTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "要编辑的文件路径，相对于记忆根目录。") String path,
		@ToolParam(description = "要查找和替换的精确文本。必须在文件中恰好出现一次。") String oldStr,
		@ToolParam(description = "替换文本。使用空字符串删除匹配文本。") String newStr
	) {}

	private MemoryStrReplaceTool(Path memoriesDir) {
		super("MemoryStrReplace",
			  "替换现有记忆文件中的精确字符串。old_str 必须完全匹配且恰好出现一次。",
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

			String content = Files.readString(target, StandardCharsets.UTF_8);
			int occurrences = countOccurrences(content, input.oldStr());

			if (occurrences == 0) {
				return ToolResult.error("错误：文件中未找到 old_str：" + input.path());
			}

			if (occurrences > 1) {
				return ToolResult.error(String.format(
						"错误：old_str 在文件中出现 %d 次。提供更多周围上下文使其唯一。", occurrences));
			}

			String replacement = input.newStr() != null ? input.newStr() : "";
			String updated = replaceFirst(content, input.oldStr(), replacement);

			Files.writeString(target, updated, StandardCharsets.UTF_8);

			if (!StringUtils.hasText(replacement)) {
				return ToolResult.success(String.format("成功从 %s 中删除匹配文本。", input.path()));
			}

			String snippet = generateEditSnippet(updated, replacement);
			return ToolResult.success(String.format("成功编辑 %s。结果片段：\n%s", input.path(), snippet));
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("编辑文件错误：" + e.getMessage());
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

	private int countOccurrences(String text, String substring) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(substring, index)) != -1) {
			count++;
			index += substring.length();
		}
		return count;
	}

	private String replaceFirst(String text, String oldStr, String newStr) {
		int index = text.indexOf(oldStr);
		if (index == -1) {
			return text;
		}
		return text.substring(0, index) + newStr + text.substring(index + oldStr.length());
	}

	private String generateEditSnippet(String fileContent, String newStr) {
		String[] lines = fileContent.split("\n", -1);
		String[] newLines = newStr.split("\n", -1);

		int editStartLine = -1;
		int editEndLine = -1;

		for (int i = 0; i < lines.length; i++) {
			if (newLines.length > 0 && lines[i].contains(newLines[0])) {
				boolean matches = true;
				for (int j = 1; j < newLines.length && i + j < lines.length; j++) {
					if (!lines[i + j].contains(newLines[j])) {
						matches = false;
						break;
					}
				}
				if (matches) {
					editStartLine = i;
					editEndLine = i + newLines.length - 1;
					break;
				}
			}
		}

		if (editStartLine == -1) {
			editStartLine = 0;
			editEndLine = Math.min(10, lines.length - 1);
		}

		int startLine = Math.max(0, editStartLine - 5);
		int endLine = Math.min(lines.length - 1, editEndLine + 5);

		StringBuilder snippet = new StringBuilder();
		for (int i = startLine; i <= endLine; i++) {
			snippet.append(String.format("%6d→%s", i + 1, lines[i]));
			if (i < endLine) {
				snippet.append("\n");
			}
		}
		return snippet.toString();
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

		public MemoryStrReplaceTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryStrReplaceTool(memoriesDir);
		}
	}
}