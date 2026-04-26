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
package cn.bitloom.agentic.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 列出目录内容，支持可选的深度和结果限制。跳过常见的噪音目录
 * （.git、node_modules、target、build等）。
 */
public class ListDirectoryTool {

	private final Path workingDirectory;

	protected ListDirectoryTool(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	@Tool(name = "ListDirectory", description = """
		列出目录的内容。
		- 返回文件和子目录，排序方式：目录优先，然后是文件，均按字母顺序排列
		- 跳过常见的噪音目录：.git、node_modules、target、build、.idea、dist、__pycache__
		- 使用 `depth` 递归进入子目录（默认1 = 仅直接子项）
		- 使用 `limit` 限制返回的条目数量（默认50）
		- 对于简单的目录列表，优先使用此工具而不是 `bash ls` —— 输出更清晰，没有时间戳或权限位
		""")
	public String listDirectory(
		@ToolParam(description = "要列出的目录的绝对路径。如果省略，则列出工作目录。", required = false) String path,
		@ToolParam(description = "递归深度（1 = 仅直接子项，2 = 一级子目录，等等）。默认：1。", required = false) Integer depth,
		@ToolParam(description = "返回的最大条目数。默认：50。", required = false) Integer limit) { // @formatter:on

		int maxDepth = (depth != null && depth > 0) ? depth : 1;
		int maxResults = (limit != null && limit > 0) ? limit : 50;

		Path targetDir = ToolUtils.resolveWorkingDirectory(path, this.workingDirectory);

		if (!Files.exists(targetDir)) {
			return "错误：路径不存在: " + targetDir.toAbsolutePath();
		}
		if (!Files.isDirectory(targetDir)) {
			return "错误：路径不是目录: " + targetDir.toAbsolutePath();
		}

		List<Entry> entries = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(targetDir, maxDepth)) {
			stream.filter(p -> !p.equals(targetDir))
				.filter(p -> !isIgnored(p))
				.limit(maxResults)
				.forEach(p -> entries.add(new Entry(p, Files.isDirectory(p))));
		}
		catch (IOException e) {
			return "列出目录时出错: " + e.getMessage();
		}

		if (entries.isEmpty()) {
			return "目录为空: " + targetDir.toAbsolutePath();
		}

		entries.sort(Comparator.<Entry, Integer>comparing(e -> e.isDir() ? 0 : 1)
			.thenComparing(e -> e.path().getFileName().toString()));

		StringBuilder sb = new StringBuilder();
		sb.append(targetDir.toAbsolutePath()).append("\n");
		for (Entry e : entries) {
			Path rel = targetDir.relativize(e.path());
			sb.append(e.isDir() ? "  [dir]  " : "  [file] ").append(rel).append("\n");
		}
		if (entries.size() == maxResults) {
			sb.append("  ... （已达到 ").append(maxResults).append(" 条的限制 —— 请使用更大的limit或缩小路径范围）");
		}
		return sb.toString().stripTrailing();
	}

	private boolean isIgnored(Path path) {
		return ToolUtils.isIgnoredPath(path);
	}

	private record Entry(Path path, boolean isDir) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private Path workingDirectory;

		private Builder() {
		}

		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		public Builder workingDirectory(String workingDirectory) {
			this.workingDirectory = workingDirectory != null ? Paths.get(workingDirectory) : null;
			return this;
		}

		public ListDirectoryTool build() {
			return new ListDirectoryTool(workingDirectory);
		}

	}

}
