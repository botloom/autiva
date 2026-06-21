package cn.bitloom.constant;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConstants {

    private AppConstants() {
    }

    public static final String USER_HOME = System.getProperty("user.home");
    public static final Path APP_DIR = Paths.get(USER_HOME, ".autiva");

    public static class Base {
        private Base() {
        }

        public static final Path LOGS_DIR = APP_DIR.resolve("logs");
        public static final Path WORKSPACE_DIR = APP_DIR.resolve("workspace");
        public static final Path AGENTS_DIR = APP_DIR.resolve("agents");
        public static final Path SKILLS_DIR = APP_DIR.resolve("skills");
        public static final Path SETTINGS_FILE = APP_DIR.resolve("settings.properties");

    }

    public static class Agents {
        private Agents() {
        }

        public static final String DEFAULT_AGENT_NAME = "default";

        public static Path agentDir(String agentId) {
            return Base.AGENTS_DIR.resolve(agentId);
        }
    }

    public static class Workspace {
        private Workspace() {
        }

        public static Path workspaceDir(String agentId) {
            return Base.WORKSPACE_DIR.resolve(agentId);
        }

    }

    public static class MainAgent {
        private MainAgent() {
        }

        public static Path agentFile(String agentId) {
            return Agents.agentDir(agentId).resolve("agent.md");
        }

        public static Path memoryFile(String agentId) {
            return Agents.agentDir(agentId).resolve("memory.md");
        }

        public static Path memoryDir(String agentId) {
            return Agents.agentDir(agentId).resolve("memory");
        }

        public static Path configFile(String agentId) {
            return Agents.agentDir(agentId).resolve("config.json");
        }

        public static Path contextDir(String agentId) {
            return Workspace.workspaceDir(agentId).resolve("context");
        }

        public static Path sessionsDir(String agentId) {
            return Workspace.workspaceDir(agentId).resolve("sessions");
        }

    }

    public static class Session {
        private Session() {
        }

        public static Path metadataFile(String agentId, String sessionId) {
            return MainAgent.sessionsDir(agentId).resolve(sessionId).resolve("metadata.json");
        }

        public static Path messagesFile(String agentId, String sessionId) {
            return MainAgent.sessionsDir(agentId).resolve(sessionId).resolve("messages.jsonl");
        }

    }

    public static class Context {
        private Context() {}
    }

    public static class Stage {
        private Stage() {
        }

        public static final double WIDTH = 800;
        public static final double HEIGHT = 500;
        public static final String FXML = "/cn/bitloom/index.fxml";
        public static final String ICON = "/cn/bitloom/images/icon.png";
        public static final String ICON_SVG = "/cn/bitloom/images/icon.svg";
        public static final String NAME = "Autiva";
    }

}
