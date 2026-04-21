package cn.bitloom.agentic.agent.subagent.code;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import cn.bitloom.agentic.agent.subagent.SubagentReference;

import org.springframework.core.io.Resource;

public class CodeSubagentReferences {

	public static List<SubagentReference> fromRootDirectories(List<String> taskRootDirectories) {

		List<SubagentReference> subagentReferences = new ArrayList<>();

		for (String taskRootDirectory : taskRootDirectories) {
			subagentReferences.addAll(fromRootDirectory(taskRootDirectory));
		}

		return subagentReferences;
	}

	public static List<SubagentReference> fromRootDirectory(String rootDirectory) {

		Path rootPath = Paths.get(rootDirectory);

		if (!Files.exists(rootPath)) {
			throw new RuntimeException("根目录不存在: " + rootDirectory);
		}

		if (!Files.isDirectory(rootPath)) {
			throw new RuntimeException("路径不是目录: " + rootDirectory);
		}

		List<SubagentReference> subagentReferences = new ArrayList<>();

		try {
			try (Stream<Path> paths = Files.walk(rootPath)) {
				paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".md"))
					.forEach(path -> {
						subagentReferences.add(new SubagentReference(path.toAbsolutePath().toString(),
								CodeSubagentDefinition.KIND, null));
					});
			}
		}
		catch (IOException ex) {
			throw new RuntimeException("从目录读取任务失败: " + rootDirectory, ex);
		}

		return subagentReferences;
	}

	public static List<SubagentReference> fromResources(Resource... resources) {
		return Arrays.stream(resources).map(CodeSubagentReferences::fromResource).flatMap(List::stream).toList();
	}

	public static List<SubagentReference> fromResources(List<Resource> resources) {
		return resources.stream().map(CodeSubagentReferences::fromResource).flatMap(List::stream).toList();
	}

	public static List<SubagentReference> fromResource(Resource agentRootPath) {
		try {
			String path = agentRootPath.getFile().toPath().toAbsolutePath().toString();
			if (agentRootPath.getFile().isDirectory()) {
				return fromRootDirectory(path);
			}

			return List.of(new SubagentReference(path, CodeSubagentDefinition.KIND, null));
		}
		catch (IOException ex) {
			throw new RuntimeException("从目录加载任务失败: " + agentRootPath, ex);
		}
	}

}
