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
package cn.bitloom.agentic.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AgentEnvironment {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

	public static final String ENVIRONMENT_INFO_KEY = "ENVIRONMENT_INFO";

	public static final String GIT_STATUS_KEY = "GIT_STATUS";

	public static final String AGENT_MODEL_KEY = "AGENT_MODEL";

	public static final String AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY = "AGENT_MODEL_KNOWLEDGE_CUTOFF";

	public static String info() {

		String workingDirectory = System.getProperty("user.dir");
		boolean isGitRepo = new File(workingDirectory, ".git").exists();
		String platform = System.getProperty("os.name").toLowerCase();
		String osVersion = System.getProperty("os.name") + " " + System.getProperty("os.version");
		String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

		StringBuilder sb = new StringBuilder();
		sb.append("工作目录: ").append(workingDirectory).append("\n");
		sb.append("是否为git仓库: ").append(isGitRepo ? "是" : "否").append("\n");
		sb.append("平台: ").append(platform).append("\n");
		sb.append("操作系统版本: ").append(osVersion).append("\n");
		sb.append("今日日期: ").append(todayDate).append("\n");

		return sb.toString();
	}

	public static String gitStatus() {

		// Check if git is available
		if (!isGitAvailable()) {
			System.out.println("Git不可用或不在PATH中。\n");
			return "";
		}

		String gitCheck = runGitCommand("rev-parse", "--is-inside-work-tree");
		if (!"true".equals(gitCheck)) {
			System.out.println("不在git仓库中。\n");
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("git状态: 这是对话开始时的git状态快照。");
		sb.append("注意，此状态是时间快照，在对话过程中不会更新。\n");

		String currentBranch = runGitCommand("rev-parse", "--abbrev-ref", "HEAD");
		sb.append("当前分支: ").append(currentBranch).append("\n\n");

		String mainBranch = getMainBranch();
		sb.append("主分支（通常用于PR）: ").append(mainBranch).append("\n\n");

		String status = runGitCommand("status", "--short");
		sb.append("状态:\n").append(status.isEmpty() ? "工作区干净\n\n" : status).append("\n\n");

		String recentCommits = runGitCommand("log", "--oneline", "-n", "5");
		sb.append("最近提交:\n").append(recentCommits);

		return sb.toString();
	}

	private static boolean isGitAvailable() {
		try {
			String result = runGitCommand("--version");
			return result.contains("git version");
		}
		catch (Exception e) {
			return false;
		}
	}

	private static String getMainBranch() {
		String[] possibleMains = { "main", "master" };
		for (String branch : possibleMains) {
			String result = runGitCommand("rev-parse", "--verify", "--quiet", branch);
			if (!result.isEmpty() && !result.toLowerCase().contains("fatal")) {
				return branch;
			}
		}
		String remoteBranch = runGitCommand("symbolic-ref", "refs/remotes/origin/HEAD", "--short");
		if (!remoteBranch.isEmpty()) {
			return remoteBranch.replace("origin/", "");
		}
		return "main";
	}

	/**
	 * 以跨平台方式运行git命令。在Windows上使用cmd.exe /c确保命令正确执行。
	 * 在Unix/Mac上直接运行git。
	 */
	private static String runGitCommand(String... gitArgs) {
		try {
			List<String> command = new ArrayList<>();

			if (IS_WINDOWS) {
				command.add("cmd.exe");
				command.add("/c");
				command.add("git");
			}
			else {
				command.add("git");
			}

            Collections.addAll(command, gitArgs);

			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(System.getProperty("user.dir")));
			pb.redirectErrorStream(true);

			pb.environment().put("LC_ALL", "C");
			pb.environment().put("LANG", "C");

			Process process = pb.start();

			String result;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				result = reader.lines().collect(Collectors.joining("\n"));
			}

			boolean finished = process.waitFor(30, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return "";
			}

			return result.trim();
		}
		catch (Exception e) {
			return "";
		}
	}

}
