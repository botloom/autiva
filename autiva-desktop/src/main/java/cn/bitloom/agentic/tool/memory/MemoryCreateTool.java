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

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆创建工具，在持久记忆存储中创建新文件。
 *
 * @author Christian Tzolov
 */
public class MemoryCreateTool extends AbstractTool<MemoryCreateTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "新文件路径，相对于记忆根目录（如 'feedback_testing.md'）。") String path,
		@ToolParam(description = "完整文件内容，包括 YAML frontmatter 块和记忆正文。") String fileText
	) {}

	private MemoryCreateTool(Path memoriesDir) {
		super("MemoryCreate",
			  "在持久记忆存储中创建新文件。文件必须不存在；如果父目录不存在，自动创建。",
			  Input.class);
		this.memoriesDir = memoriesDir.normalize();
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			Path target = resolveSafePath(input.path());

			if (Files.exists(target)) {
				return ToolResult.error("错误：文件已存在：" + input.path() + "。使用 MemoryStrReplace 修改现有文件。");
			}

			Path parent = target.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}

			Files.writeString(target, input.fileText() != null ? input.fileText() : "", StandardCharsets.UTF_8);

			return ToolResult.success("成功创建文件：" + input.path() +
					"（" + (input.fileText() != null ? input.fileText().length() : 0) + " 字节）");
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("创建文件错误：" + e.getMessage());
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

		public MemoryCreateTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryCreateTool(memoriesDir);
		}
	}
}