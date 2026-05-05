package cn.bitloom.agentic.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ShellTools {

	private static final Map<String, BackgroundProcess> backgroundProcesses = new ConcurrentHashMap<>();

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	private static String[] buildShellCommand(String command) {
		if (isWindows()) {
			String cwd = System.getProperty("user.dir");
			return new String[] { "wsl", "--cd", cwd, "-e", "bash", "-c", command };
		}
		return new String[] { "/bin/bash", "-c", command };
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
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						synchronized (stdout) {
							stdout.append(line).append("\n");
						}
					}
				}
				catch (IOException ignored) {
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
				catch (IOException ignored) {
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
					if (!result.isEmpty())
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

	@Tool(name = "Bash", description = """
		执行终端命令用于构建、测试、包管理等操作（如npm、docker、make、mvn、python、git）。
		不要用于文件操作 —— 请使用专用工具：
		- 文件搜索：使用Glob（不要用find或ls）
		- 内容搜索：使用Grep（不要用grep或rg）
		- 读取文件：使用Read（不要用cat/head/tail）
		- 编辑文件：使用Edit（不要用sed/awk）
		- 写入文件：使用Write（不要用echo >/cat <<EOF）

		平台说明：
		- 所有平台统一通过bash执行命令。Windows上通过WSL运行bash（需安装WSL），Unix/Mac直接使用bash
		- 使用&&链接依赖命令，使用;链接可独立失败的命令
		- Windows上通过WSL执行时，路径需使用Linux格式：C:\\path → /mnt/c/path，D:\\project → /mnt/d/project

		使用说明：
		- command参数是必需的。
		- 可选超时时间，单位毫秒（最大600000ms / 10分钟）。默认：120000ms（2分钟）。
		- 输出在30000字符处截断。
		- 使用run_in_background运行长时间命令。
		- 包含空格的文件路径请用双引号括起来。
		- 优先使用绝对路径而非cd。

		重要提示：
		- 永远不要运行额外的命令来读取或探索代码，除了git命令
		- 永远不要使用TodoWrite或Task工具
		- 除非用户明确要求，否则不要推送到远程仓库
		- 重要：永远不要使用带-i标志的git命令（如git rebase -i或git add -i），因为它们需要交互式输入，不受支持。
		- 如果没有更改需要提交（即没有未跟踪的文件和没有修改），不要创建空提交
		- 要确保良好的格式，通过引号或直接参数传递提交消息：

		# 创建Pull Request
		使用gh命令通过Bash工具执行所有GitHub相关任务，包括处理issues、pull requests、checks和releases。如果提供了Github URL，请使用gh命令获取所需信息。

		重要：当用户要求你创建pull request时，请仔细遵循以下步骤：

		1. 你可以在单次响应中调用多个工具。当请求多个独立信息且所有命令都可能成功时，并行运行多个工具调用以获得最佳性能。使用Bash工具并行运行以下命令，以了解分支自主分支分叉以来的当前状态：
		- 运行git status命令查看所有未跟踪的文件
		- 运行git diff命令查看暂存和未暂存的更改
		- 检查当前分支是否跟踪远程分支并与远程保持同步，以了解是否需要推送到远程
		- 运行git log命令和`git diff [base-branch]...HEAD`来了解当前分支的完整提交历史（从与基础分支分叉时开始）
		2. 分析将包含在pull request中的所有更改，确保查看所有相关提交（不仅仅是最新提交，而是将包含在pull request中的所有提交！！！），并起草pull request摘要
		3. 你可以在单次响应中调用多个工具。当请求多个独立信息且所有命令都可能成功时，并行运行多个工具调用以获得最佳性能。并行运行以下命令：
		- 如需要则创建新分支
		- 如需要则使用-u标志推送到远程
		- 使用gh pr create创建PR，使用--title和--body参数。

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
		@ToolParam(description = "设置为true以在后台运行此命令。使用BashOutput稍后读取输出。", required = false) Boolean runInBackground) {

		String shellId = "shell_" + System.currentTimeMillis();

		try {
			String[] shellCommand = buildShellCommand(command);

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
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = reader.readLine()) != null) {
							stdout.append(line).append("\n");
						}
					}
					catch (IOException ignored) {
					}
				});

				Thread stderrThread = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = reader.readLine()) != null) {
							stderr.append(line).append("\n");
						}
					}
					catch (IOException ignored) {
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

				if (!stdout.isEmpty()) {
					result.append(stdout.toString());
				}

				if (!stderr.isEmpty()) {
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
		@ToolParam(description = "可选的正则表达式，用于过滤输出行。仅匹配此正则表达式的行将包含在结果中。不匹配的行将不再可读。", required = false) String filter) {

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
			catch (IllegalThreadStateException ignored) {
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

	@Tool(name = "KillShell", description = """
		- 通过ID终止正在运行的后台bash shell
		- 接受一个标识要终止的shell的shell_id参数
		- 返回成功或失败状态
		- 当需要终止长时间运行的shell时使用此工具
		- Shell ID可以通过/bashes命令找到
		""")
	public String killShell(
		@ToolParam(description = "要终止的后台Shell的ID") String bash_id) {

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
