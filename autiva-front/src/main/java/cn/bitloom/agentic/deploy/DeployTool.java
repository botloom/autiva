package cn.bitloom.agentic.deploy;

import cn.bitloom.constant.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class DeployTool {

    private static final Logger logger = LoggerFactory.getLogger(DeployTool.class);

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "__pycache__", ".idea", ".vscode",
            "target", "build", "dist", ".next", ".nuxt", ".cache"
    );

    private static final Set<String> IGNORED_FILES = Set.of(
            ".DS_Store", "Thumbs.db", ".env.local", ".env.production"
    );

    private static final long MAX_FILE_SIZE = 1024 * 1024;

    private final BackendClient backendClient;
    private final String clientId;

    private DeployTool(BackendClient backendClient, String clientId) {
        this.backendClient = backendClient;
        this.clientId = clientId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Tool(description = "Deploy a project from the ~/.autiva/project/ directory to the cloud sandbox. " +
            "The project must already exist in the project directory. " +
            "This will package all project files, send them to the backend, create a sandbox container, " +
            "provision BaaS resources (MySQL, Redis, MongoDB, MinIO), and return a public URL. " +
            "Runtime is auto-detected from project files (package.json -> node, requirements.txt -> python, pom.xml -> java).")
    public String deploy(
            @ToolParam(description = "Name of the project directory under ~/.autiva/project/. " +
                    "This is the project to deploy.") String projectName,
            @ToolParam(description = "Runtime environment: 'node', 'python', or 'java'. " +
                    "Leave empty for auto-detection based on project files.") String runtime
    ) {
        logger.info("[DeployTool] Deploying project: {}", projectName);

        Path projectDir = AppConstants.Base.CODE_PROJECT_DIR.resolve(projectName);

        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return "Error: Project directory not found: " + projectDir + "\n" +
                    "Please create the project files in ~/.autiva/project/" + projectName + "/ first.";
        }

        List<BackendClient.ProjectFileInfo> files = collectProjectFiles(projectDir);
        if (files.isEmpty()) {
            return "Error: No files found in project directory: " + projectDir;
        }

        String detectedRuntime = runtime;
        if (detectedRuntime == null || detectedRuntime.isBlank()) {
            detectedRuntime = detectRuntime(files);
        }

        logger.info("[DeployTool] Collected {} files, runtime={}", files.size(), detectedRuntime);

        if (!backendClient.isBackendAvailable()) {
            return "Error: Backend service is not available. Please ensure autiva-backend is running at " +
                    backendClient.toString();
        }

        Map<String, String> envVars = loadEnvFile(projectDir);

        BackendClient.DeployResponse response = backendClient.deployProject(
                clientId, projectName, files, detectedRuntime, envVars);

        if (response.success()) {
            return "Deployment successful!\n" +
                    "- Project: " + projectName + "\n" +
                    "- Runtime: " + detectedRuntime + "\n" +
                    "- URL: " + response.url() + "\n" +
                    "- Subdomain: " + response.subdomain() + "\n" +
                    "- Files deployed: " + files.size() + "\n" +
                    "\nThe application is now live. BaaS resources (MySQL, Redis, MongoDB, MinIO) " +
                    "have been provisioned and their connection info is available as environment variables " +
                    "in the sandbox container.";
        } else {
            return "Deployment failed: " + response.message();
        }
    }

    @Tool(description = "Stop a running deployed project. This will kill the sandbox container and free resources.")
    public String stopProject(
            @ToolParam(description = "Name of the project to stop") String projectName
    ) {
        logger.info("[DeployTool] Stopping project: {}", projectName);

        BackendClient.StopResponse response = backendClient.stopProject(clientId, projectName);

        if (response.success()) {
            return "Project '" + projectName + "' stopped successfully.";
        } else {
            return "Failed to stop project: " + response.message();
        }
    }

    @Tool(description = "List all deployed projects and their status.")
    public String listDeployments() {
        logger.info("[DeployTool] Listing deployments");

        BackendClient.StatusResponse response = backendClient.getStatus(clientId);

        if (response.success()) {
            return "Current deployments:\n" + response.data();
        } else {
            return "Failed to get deployment status: " + response.data();
        }
    }

    private List<BackendClient.ProjectFileInfo> collectProjectFiles(Path projectDir) {
        List<BackendClient.ProjectFileInfo> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::shouldIncludeFile)
                    .forEach(path -> {
                        try {
                            if (Files.size(path) > MAX_FILE_SIZE) {
                                logger.warn("[DeployTool] Skipping large file: {} ({} bytes)",
                                        path, Files.size(path));
                                return;
                            }

                            String relativePath = projectDir.relativize(path).toString().replace('\\', '/');
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            files.add(new BackendClient.ProjectFileInfo(relativePath, content));
                        } catch (IOException e) {
                            logger.warn("[DeployTool] Failed to read file {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("[DeployTool] Failed to walk project directory: {}", e.getMessage());
        }

        return files;
    }

    private boolean shouldIncludeFile(Path path) {
        for (Path segment : path) {
            if (IGNORED_DIRS.contains(segment.toString())) {
                return false;
            }
        }
        String fileName = path.getFileName().toString();
        return !IGNORED_FILES.contains(fileName);
    }

    private String detectRuntime(List<BackendClient.ProjectFileInfo> files) {
        boolean hasPackageJson = files.stream().anyMatch(f -> f.path().equals("package.json"));
        boolean hasRequirementsTxt = files.stream().anyMatch(f -> f.path().equals("requirements.txt"));
        boolean hasPomXml = files.stream().anyMatch(f -> f.path().equals("pom.xml"));

        if (hasPackageJson) return "node";
        if (hasRequirementsTxt) return "python";
        if (hasPomXml) return "java";
        return "node";
    }

    private Map<String, String> loadEnvFile(Path projectDir) {
        Path envFile = projectDir.resolve(".env");
        Map<String, String> envVars = new HashMap<>();

        if (Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.startsWith("#") && line.contains("=")) {
                        int eqIndex = line.indexOf('=');
                        String key = line.substring(0, eqIndex).trim();
                        String value = line.substring(eqIndex + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        envVars.put(key, value);
                    }
                }
            } catch (IOException e) {
                logger.warn("[DeployTool] Failed to read .env file: {}", e.getMessage());
            }
        }

        return envVars;
    }

    public static class Builder {
        private String backendUrl = "http://localhost:9527";
        private String clientId = "default";

        public Builder backendUrl(String backendUrl) {
            this.backendUrl = backendUrl;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public DeployTool build() {
            BackendClient client = new BackendClient(backendUrl);
            return new DeployTool(client, clientId);
        }
    }
}
