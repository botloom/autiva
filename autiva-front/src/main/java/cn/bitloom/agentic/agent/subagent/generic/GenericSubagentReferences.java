package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.AgentIdentityEnum;
import cn.bitloom.agentic.agent.subagent.SubagentReference;
import cn.bitloom.agentic.util.MarkdownParser;
import cn.bitloom.exception.StorageException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GenericSubagentReferences {

    public static List<SubagentReference> fromSubagentDirectories(Path workspaceDir) {
        List<SubagentReference> subagentReferences = new ArrayList<>();

        for (AgentIdentityEnum identity : AgentIdentityEnum.values()) {
            if (!identity.isSubagent() || identity == AgentIdentityEnum.A2A) {
                continue;
            }
            Path dir = workspaceDir.resolve(identity.name());
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .forEach(path -> {
                            String kind = resolveKind(path);
                            subagentReferences.add(new SubagentReference(path.toAbsolutePath().toString(), kind, null));
                        });
            } catch (IOException ex) {
                throw StorageException.readError(dir.toString(), ex);
            }
        }

        return subagentReferences;
    }

    public static List<SubagentReference> fromRootDirectory(Path rootPath) {

        if (!Files.exists(rootPath)) {
            throw StorageException.dirNotFound(rootPath.toString());
        }

        if (!Files.isDirectory(rootPath)) {
            throw StorageException.notADir(rootPath.toString());
        }

        List<SubagentReference> subagentReferences = new ArrayList<>();

        try {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .forEach(path -> {
                            String kind = resolveKind(path);
                            subagentReferences.add(new SubagentReference(path.toAbsolutePath().toString(), kind, null));
                        });
            }
        } catch (IOException ex) {
            throw StorageException.readError(rootPath.toString(), ex);
        }

        return subagentReferences;
    }

    public static List<SubagentReference> fromResource(org.springframework.core.io.Resource agentRootPath) {
        try {
            Path path = agentRootPath.getFile().toPath().toAbsolutePath();
            if (agentRootPath.getFile().isDirectory()) {
                return fromRootDirectory(path);
            }

            String kind = resolveKind(path);
            return List.of(new SubagentReference(path.toAbsolutePath().toString(), kind, null));
        } catch (IOException ex) {
            throw StorageException.readError(agentRootPath.toString(), ex);
        }
    }

    private static String resolveKind(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            MarkdownParser parser = new MarkdownParser(content);
            Object kindValue = parser.getFrontMatter().get("kind");
            if (kindValue != null && !kindValue.toString().isBlank()) {
                return kindValue.toString().trim();
            }
        } catch (IOException e) {
            // ignore, fallback to GENERIC
        }
        return AgentIdentityEnum.GENERIC.name();
    }
}
