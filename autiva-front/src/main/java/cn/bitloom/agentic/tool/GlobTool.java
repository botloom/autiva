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
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 纯Java glob实现，不需要外部工具。使用Java NIO.2进行文件模式匹配和遍历。
 *
 * 由Claude Code AI助手生成。
 *
 * @author Christian Tzolov
 * @author Claude Code
 */
public class GlobTool {

	private final int maxDepth;

	private final int maxResults;

	private final Path workingDirectory;

	/**
	 * 带可配置参数的构造函数。
	 * @param maxDepth 最大目录遍历深度，防止无限递归（默认：100）
	 * @param maxResults 返回的最大结果数（默认：1000）
	 * @param workingDirectory 未指定路径时使用的工作目录。如果为null，默认为当前JVM工作目录。
	 */
	protected GlobTool(int maxDepth, int maxResults, Path workingDirectory) {
		this.maxDepth = maxDepth;
		this.maxResults = maxResults;
		this.workingDirectory = workingDirectory;
	}

	// @formatter:off
	@Tool(name = "Glob", description = """
			- 快速文件模式匹配工具，适用于任何大小的代码库
			- 支持glob模式，如"**/*.js"或"src/**/*.ts"
			- 返回按修改时间排序的匹配文件路径
			- 当你需要按名称模式查找文件时使用此工具
			- 当你进行可能需要多轮glob和grep的开放式搜索时，请使用Agent工具代替
			- 你可以在一次响应中调用多个工具。如果多个搜索可能有用，推测性地并行执行多个搜索总是更好的做法。
		""")
	public String glob(
		@ToolParam(description = "用于匹配文件的glob模式") String pattern,
		@ToolParam(description = "要搜索的目录。如果未指定，将使用当前工作目录。重要提示：省略此字段以使用默认目录。不要输入\\\"undefined\\\"或\\\"null\\\" —— 直接省略即可使用默认行为。如果提供，必须是有效的目录路径。", required = false) String path) { // @formatter:on

		Assert.hasText(pattern, "	glob模式不能为空");

		try {
			Path searchPath = ToolUtils.resolveWorkingDirectory(path, this.workingDirectory);

			if (!Files.exists(searchPath)) {
				return "错误：路径不存在: " + searchPath.toAbsolutePath();
			}

			if (!Files.isDirectory(searchPath)) {
				return "错误：路径不是目录: " + searchPath.toAbsolutePath();
			}

			PathMatcher matcher = this.buildGlobMatcher(pattern);

			List<FileInfo> matchingFiles = new ArrayList<>();

			try (Stream<Path> paths = Files.walk(searchPath, this.maxDepth, FileVisitOption.FOLLOW_LINKS)) {
				paths.filter(Files::isRegularFile)
					.filter(p -> !this.isIgnoredPath(p))
					.filter(p -> this.matchesPattern(p, searchPath, matcher))
					.limit(this.maxResults)
					.forEach(file -> {
						try {
							BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
							matchingFiles.add(new FileInfo(file, attrs.lastModifiedTime().toMillis()));
						}
						catch (IOException e) {
							matchingFiles.add(new FileInfo(file, 0));
						}
					});
			}

			if (matchingFiles.isEmpty()) {
				return "未找到匹配模式的文件: " + pattern;
			}

			matchingFiles.sort(Comparator.comparingLong(FileInfo::modificationTime).reversed());

			StringBuilder result = new StringBuilder();
			for (FileInfo fileInfo : matchingFiles) {
				result.append(fileInfo.path().toString()).append("\n");
			}

			return result.toString().trim();

		}
		catch (Exception e) {
			return "执行glob时出错: " + e.getMessage();
		}
	}

	private PathMatcher buildGlobMatcher(String pattern) {
		String globPattern = pattern.startsWith("**/") ? pattern : "**/" + pattern;
		return FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
	}

	private boolean matchesPattern(Path file, Path searchPath, PathMatcher matcher) {
		if (matcher.matches(file)) {
			return true;
		}

		try {
			Path relativePath = searchPath.relativize(file);
			return matcher.matches(relativePath);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private boolean isIgnoredPath(Path path) {
		return ToolUtils.isIgnoredPath(path);
	}

	private record FileInfo(Path path, long modificationTime) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private int maxDepth = 100;

		private int maxResults = 1000;

		private Path workingDirectory = null;

		private Builder() {
		}

		public Builder maxDepth(int maxDepth) {
			this.maxDepth = maxDepth;
			return this;
		}

		public Builder maxResults(int maxResults) {
			this.maxResults = maxResults;
			return this;
		}

		/**
		 * 设置当智能体未指定路径时使用的工作目录。
		 * 这允许工具在沙箱/工作区上下文中操作。
		 * @param workingDirectory 工作目录路径
		 * @return 此构建器
		 */
		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		/**
		 * 使用字符串路径设置工作目录。
		 * @param workingDirectory 工作目录路径字符串
		 * @return 此构建器
		 */
		public Builder workingDirectory(String workingDirectory) {
			this.workingDirectory = workingDirectory != null ? Paths.get(workingDirectory) : null;
			return this;
		}

		public GlobTool build() {
			return new GlobTool(maxDepth, maxResults, workingDirectory);
		}

	}

}
