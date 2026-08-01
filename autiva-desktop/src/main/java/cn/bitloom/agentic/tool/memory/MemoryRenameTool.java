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
 * 记忆重命名工具，在记忆存储内重命名或移动文件或目录。
 *
 * @author Christian Tzolov
 */
public class MemoryRenameTool extends AbstractTool<MemoryRenameTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "文件或目录的当前路径，相对于记忆根目录。") String oldPath,
		@ToolParam(description = "文件或目录的新路径，相对于记忆根目录。") String newPath
	) {}

	private MemoryRenameTool(Path memoriesDir) {
		super("MemoryRename",
			  "在记忆存储内重命名或移动文件或目录。源路径必须存在；目标路径必须不存在。",
			  Input.class);
		this.memoriesDir = memoriesDir.normalize();
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			Path source = resolveSafePath(input.oldPath());
			Path destination = resolveSafePath(input.newPath());

			if (!Files.exists(source)) {
				return ToolResult.error("错误：源路径不存在：" + input.oldPath());
			}

			if (Files.exists(destination)) {
				return ToolResult.error("错误：目标路径已存在：" + input.newPath());
			}

			Path destParent = destination.getParent();
			if (destParent != null && !Files.exists(destParent)) {
				Files.createDirectories(destParent);
			}

			Files.move(source, destination);

			return ToolResult.success(String.format("成功将 '%s' 重命名为 '%s'", input.oldPath(), input.newPath()));
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("重命名路径错误：" + e.getMessage());
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

		public MemoryRenameTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryRenameTool(memoriesDir);
		}
	}
}