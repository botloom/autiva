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
import java.util.Comparator;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;

/**
 * 记忆删除工具，从记忆存储中删除文件或目录。
 *
 * @author Christian Tzolov
 */
public class MemoryDeleteTool extends AbstractTool<MemoryDeleteTool.Input> {

	private final Path memoriesDir;

	/**
	 * 输入参数 record。
	 */
	public record Input(
		@ToolParam(description = "要删除的文件或目录路径，相对于记忆根目录。") String path
	) {}

	private MemoryDeleteTool(Path memoriesDir) {
		super("MemoryDelete",
			  "从记忆存储中删除文件或目录（及其所有内容）。此操作不可逆。",
			  Input.class);
		this.memoriesDir = memoriesDir.normalize();
	}

	@Override
	public @NonNull ToolResult execute(@NonNull Input input, @Nullable ToolContext context) {
		try {
			Path target = resolveSafePath(input.path());

			// 防止删除记忆根目录本身
			if (target.equals(this.memoriesDir)) {
				return ToolResult.error("错误：不能删除记忆根目录。");
			}

			if (!Files.exists(target)) {
				return ToolResult.error("错误：路径不存在：" + input.path());
			}

			if (Files.isDirectory(target)) {
				try (Stream<Path> walk = Files.walk(target)) {
					walk.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						}
						catch (IOException e) {
							throw new RuntimeException("删除失败：" + p, e);
						}
					});
				}
				return ToolResult.success("成功删除目录：" + input.path());
			}
			else {
				Files.delete(target);
				return ToolResult.success("成功删除文件：" + input.path());
			}
		}
		catch (SecurityException e) {
			return ToolResult.error("错误：" + e.getMessage());
		}
		catch (RuntimeException e) {
			return ToolResult.error("删除路径错误：" + e.getMessage());
		}
		catch (IOException e) {
			return ToolResult.error("删除路径错误：" + e.getMessage());
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

		public MemoryDeleteTool build() {
			try {
				Files.createDirectories(memoriesDir);
			}
			catch (IOException e) {
				throw new IllegalStateException("创建记忆目录失败：" + memoriesDir, e);
			}
			return new MemoryDeleteTool(memoriesDir);
		}
	}
}