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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Shell命令执行工具，提供Bash命令执行、后台进程管理和输出获取功能。
 *
 * @author Christian Tzolov
 */
public class ShellTools {

	private static final Map<String, BackgroundProcess> backgroundProcesses = new ConcurrentHashMap<>();

	private static Charset getConsoleCharset() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			String jnuEncoding = System.getProperty("sun.jnu.encoding");
			if (jnuEncoding != null && Charset.isSupported(jnuEncoding)) {
				return Charset.forName(jnuEncoding);
			}
			if (Charset.isSupported("GBK")) {
				return Charset.forName("GBK");
			}
		}
		return StandardCharsets.UTF_8;
	}

	private static class BackgroundProcess {

		final Process process;

		final StringBuilder stdout;

		final StringBuilder stderr;

		final Thread stdoutReader;

		final Thread stderrReader;

		int lastStdoutPosition = 0;

		int lastStderrPosition = 0;

		BackgroundProcess(Process process) {
			this.process = process;
			this.stdout = new StringBuilder();
			this.stderr = new StringBuilder();

			this.stdoutReader = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), getConsoleCharset()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						synchronized (stdout) {
							stdout.append(line).append("\n");
						}
					}
				}
				catch (IOException e) {
				}
			});
			this.stdoutReader.setDaemon(true);
			this.stdoutReader.start();

			this.stderrReader = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						synchronized (stderr) {
							stderr.append(line).append("\n");
						}
					}
				}
				catch (IOException e) {
				}
			});
			this.stderrReader.setDaemon(true);
			this.stderrReader.start();
		}

		String getNewOutput(String filter) {
			StringBuilder result = new StringBuilder();

			synchronized (stdout) {
				String newStdout = stdout.substring(lastStdoutPosition);
				if (filter != null && !filter.isEmpty()) {
					Pattern pattern = Pattern.compile(filter);
					newStdout = filterOutput(newStdout, pattern);
				}
				if (!newStdout.isEmpty()) {
					result.append("STDOUT:\n").append(newStdout);
				}
				lastStdoutPosition = stdout.length();
			}

			synchronized (stderr) {
				String newStderr = stderr.substring(lastStderrPosition);
				if (filter != null && !filter.isEmpty()) {
					Pattern pattern = Pattern.compile(filter);
					newStderr = filterOutput(newStderr, pattern);
				}
				if (!newStderr.isEmpty()) {
					if (result.length() > 0)
						result.append("\n");
					result.append("STDERR:\n").append(newStderr);
				}
				lastStderrPosition = stderr.length();
			}

			return result.toString();
		}

		private String filterOutput(String output, Pattern pattern) {
			String[] lines = output.split("\n");
			StringBuilder filtered = new StringBuilder();
			for (String line : lines) {
				if (pattern.matcher(line).find()) {
					filtered.append(line).append("\n");
				}
			}
			return filtered.toString();
		}

		boolean isAlive() {
			return process.isAlive();
		}

		void destroy() {
			process.destroy();
			try {
				if (!process.waitFor(5, TimeUnit.SECONDS)) {
					process.destroyForcibly();
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				process.destroyForcibly();
			}
		}

		int getExitCode() {
			return process.exitValue();
		}

	}

	//
	// Shell命令
	//

	// @formatter:off
	@Tool(name = "Bash", description = """
		执行bash命令用于终端操作，如npm、docker、make、mvn、python。
		不要用于文件操作 —— 请使用专用工具：
		- 文件搜索：使用Glob（不要用find或ls）
		- 内容搜索：使用Grep（不要用grep或rg）
		- 读取文件：使用Read（不要用cat/head/tail）
		- 编辑文件：使用Edit（不要用sed/awk）
		- 写入文件：使用Write（不要用echo >/cat <<EOF）

		使用说明：
		- command参数是必需的。
		- 可选超时时间，单位毫秒（最大600000ms / 10分钟）。默认：120000ms（2分钟）。
		- 输出在30000字符处截断。
		- 使用run_in_background运行长时间命令。
		- 包含空格的文件路径请用双引号括起来。
		- 使用&&链接依赖命令。如果前面的失败可以接受，则使用;。
		- 优先使用绝对路径而非cd。

		重要提示：
		- 永远不要运行额外的命令来读取或探索代码，除了git bash命令
		- 永远不要使用TodoWrite或Task工具
		- 除非用户明确要求，否则不要推送到远程仓库
		- 重要：永远不要使用带-i标志的git命令（如git rebase -i或git add -i），因为它们需要交互式输入，不受支持。
		- 如果没有更改需要提交（即没有未跟踪的文件和没有修改），不要创建空提交
		- 为了确保良好的格式，始终通过HEREDOC传递提交消息，如下例所示：
		<example>
		git commit -m "$(cat <<'EOF'
		在此填写提交消息。

		🤶 Generated with [Claude Code](https://claude.com/claude-code)

		Co-Authored-By: Claude <noreply@anthropic.com>
		EOF
		)"
		</example>

		# 创建Pull Request
		使用gh命令通过Bash工具执行所有GitHub相关任务，包括处理issues、pull requests、checks和releases。如果提供了Github URL，请使用gh命令获取所需信息。

		重要：当用户要求你创建pull request时，请仔细遵循以下步骤：

		1. 你可以在单次响应中调用多个工具。当请求多个独立信息且所有命令都可能成功时，并行运行多个工具调用以获得最佳性能。使用Bash工具并行运行以下bash命令，以了解分支自主分支分叉以来的当前状态：
		- 运行git status命令查看所有未跟踪的文件
		- 运行git diff命令查看暂存和未暂存的更改
		- 检查当前分支是否跟踪远程分支并与远程保持同步，以了解是否需要推送到远程
		- 运行git log命令和`git diff [base-branch]...HEAD`来了解当前分支的完整提交历史（从与基础分支分叉时开始）
		2. 分析将包含在pull request中的所有更改，确保查看所有相关提交（不仅仅是最新提交，而是将包含在pull request中的所有提交！！！），并起草pull request摘要
		3. 你可以在单次响应中调用多个工具。当请求多个独立信息且所有命令都可能成功时，并行运行多个工具调用以获得最佳性能。并行运行以下命令：
		- 如需要则创建新分支
		- 如需要则使用-u标志推送到远程
		- 使用gh pr create创建PR，格式如下。使用HEREDOC传递正文以确保正确格式。
		<example>
		gh pr create --title "PR标题" --body "$(cat <<'EOF'

		## 摘要
		<1-3个要点>

		## 测试计划
		[用于测试pull request的待办事项的markdown清单...]

		🤶 Generated with [Claude Code](https://claude.com/claude-code)
		EOF
		)"
		</example>

		重要：
		- 不要使用TodoWrite或Task工具
		- 完成后返回PR URL，以便用户查看

		# 其他常见操作
		- 查看Github PR上的评论：gh api repos/foo/bar/pulls/123/comments
		""")
	public String bash(
		@ToolParam(description = "要执行的命令") String command,
		@ToolParam(description = "可选超时时间，单位毫秒（最大600000）", required = false) Long timeout,
		@ToolParam(description = "对该命令功能的简明描述，5-10个词，使用主动语态。示例：\n输入：ls\n输出：列出当前目录文件\n\n输入：git status\n输出：显示工作树状态\n\n输入：npm install\n输出：安装包依赖\n\n输入：mkdir foo\n输出：创建目录'foo'", required = false) String description,
		@ToolParam(description = "设置为true以在后台运行此命令。使用BashOutput稍后读取输出。", required = false) Boolean runInBackground) { // @formatter:on

		String shellId = "shell_" + System.currentTimeMillis();

		try {
			String[] shellCommand;
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("win")) {
				shellCommand = new String[] { "cmd.exe", "/c", command };
			}
			else {
				shellCommand = new String[] { "/bin/bash", "-c", command };
			}

			ProcessBuilder processBuilder = new ProcessBuilder(shellCommand);
			processBuilder.redirectErrorStream(false);

			Process process = processBuilder.start();

			if (Boolean.TRUE.equals(runInBackground)) {
				BackgroundProcess bgProcess = new BackgroundProcess(process);
				backgroundProcesses.put(shellId, bgProcess);

				return String.format(
						"bash_id: %s\n\n后台Shell已启动，ID: %s\n使用BashOutput工具并传入bash_id='%s'来获取输出。",
						shellId, shellId, shellId);
			}
			else {
				long timeoutMs = timeout != null ? Math.min(timeout, 600000) : 120000;

				StringBuilder stdout = new StringBuilder();
				StringBuilder stderr = new StringBuilder();

				Thread stdoutThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), getConsoleCharset()))) {
						String line;
						while ((line = reader.readLine()) != null) {
							stdout.append(line).append("\n");
						}
					}
					catch (IOException e) {
					}
				});

				Thread stderrThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), getConsoleCharset()))) {
						String line;
						while ((line = reader.readLine()) != null) {
							stderr.append(line).append("\n");
						}
					}
					catch (IOException e) {
					}
				});

				stdoutThread.start();
				stderrThread.start();

				boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

				if (!completed) {
					process.destroy();
					if (!process.waitFor(5, TimeUnit.SECONDS)) {
						process.destroyForcibly();
					}
					return String.format("bash_id: %s\n\n命令在%d毫秒后超时", shellId, timeoutMs);
				}

				stdoutThread.join(1000);
				stderrThread.join(1000);

				int exitCode = process.exitValue();
				StringBuilder result = new StringBuilder();

				result.append("bash_id: ").append(shellId).append("\n\n");

				if (stdout.length() > 0) {
					result.append(stdout.toString());
				}

				if (stderr.length() > 0) {
					if (result.length() > result.indexOf("\n\n") + 2)
						result.append("\n");
					result.append("STDERR:\n").append(stderr.toString());
				}

				if (exitCode != 0) {
					if (result.length() > result.indexOf("\n\n") + 2)
						result.append("\n");
					result.append("退出码: ").append(exitCode);
				}

				String output = result.toString();
				if (output.length() > 30000) {
					String header = output.substring(0, output.indexOf("\n\n") + 2);
					String content = output.substring(output.indexOf("\n\n") + 2);
					output = header + content.substring(0, Math.min(content.length(), 30000 - header.length()))
							+ "\n... (输出已截断)";
				}

				return output;
			}

		}
		catch (IOException e) {
			return "执行命令出错: " + e.getMessage();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "命令执行被中断: " + e.getMessage();
		}
	}

	// @formatter:off
	@Tool(name = "BashOutput", description = """
		- 从正在运行或已完成的后台bash shell获取输出
		- 接受一个标识shell的shell_id参数
		- 始终仅返回自上次检查以来的新输出
		- 返回stdout和stderr输出以及shell状态
		- 支持可选的正则表达式过滤，仅显示匹配模式的行
		- 当需要监控或检查长时间运行的shell输出时使用此工具
		- Shell ID可以通过/bashes命令找到
		""")
	public String bashOutput(
		@ToolParam(description = "要获取输出的后台Shell的ID") String bash_id,
		@ToolParam(description = "可选的正则表达式，用于过滤输出行。仅匹配此正则表达式的行将包含在结果中。不匹配的行将不再可读。", required = false) String filter) { // @formatter:on

		BackgroundProcess bgProcess = backgroundProcesses.get(bash_id);

		if (bgProcess == null) {
			return "错误：未找到ID为 " + bash_id + " 的后台Shell";
		}

		String newOutput = bgProcess.getNewOutput(filter);

		StringBuilder result = new StringBuilder();
		result.append("Shell ID: ").append(bash_id).append("\n");
		result.append("状态: ").append(bgProcess.isAlive() ? "运行中" : "已完成").append("\n");

		if (!bgProcess.isAlive()) {
			try {
				result.append("退出码: ").append(bgProcess.getExitCode()).append("\n");
			}
			catch (IllegalThreadStateException e) {
			}
		}

		if (!newOutput.isEmpty()) {
			result.append("\n新输出:\n").append(newOutput);
		}
		else {
			result.append("\n自上次检查以来没有新输出。");
		}

		return result.toString();
	}

	// @formatter:off
	@Tool(name = "KillShell", description = """
		- 通过ID终止正在运行的后台bash shell
		- 接受一个标识要终止的shell的shell_id参数
		- 返回成功或失败状态
		- 当需要终止长时间运行的shell时使用此工具
		- Shell ID可以通过/bashes命令找到
		""")
	public String killShell(
		@ToolParam(description = "要终止的后台Shell的ID") String bash_id) { // @formatter:on

		BackgroundProcess bgProcess = backgroundProcesses.get(bash_id);

		if (bgProcess == null) {
			return "错误：未找到ID为 " + bash_id + " 的后台Shell";
		}

		if (!bgProcess.isAlive()) {
			backgroundProcesses.remove(bash_id);
			return "Shell " + bash_id + " 已终止。已从活动Shell中移除。";
		}

		bgProcess.destroy();

		try {
			Thread.sleep(500);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		backgroundProcesses.remove(bash_id);

		return "成功终止Shell: " + bash_id;
	}

	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		public ShellTools build() {
			return new ShellTools();
		}
	}

}
