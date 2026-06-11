package cn.bitloom.constant;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConstants {

    private AppConstants() {
    }

    public static class Base {
        private Base() {
        }

        public static final String USER_HOME = System.getProperty("user.home");
        public static final Path APP_DIR = Paths.get(USER_HOME, ".autiva");
        public static final Path LOGS_DIR = APP_DIR.resolve("logs");
        public static final Path WORKSPACE_DIR = APP_DIR.resolve("workspace");
        public static final Path AGENTS_DIR = APP_DIR.resolve("agents");
        public static final Path AGENT_CONFIG_FILE = APP_DIR.resolve("config.json");
        public static final Path SETTINGS_FILE = APP_DIR.resolve("settings.properties");
        public static final Path SKILLS_DIR = APP_DIR.resolve("skills");
        public static final Path AGENTS_MD = APP_DIR.resolve("AGENTS.md");
        public static final Path MEMORY_MD = APP_DIR.resolve("MEMORY.md");
        public static final Path BOOTSTRAP_MD = APP_DIR.resolve("BOOTSTRAP.md");
        public static final Path MEMORY_DIR = APP_DIR.resolve("memory");

        public static final String DEFAULT_USER = "default";

        /**
         * 获取指定 agent 的定义目录（agents/{agentId}/）
         */
        public static Path agentDir(String agentId) {
            return AGENTS_DIR.resolve(agentId);
        }

        /**
         * 获取指定 agent 的 agent.md 路径（agents/{agentId}/agent.md）
         */
        public static Path agentDefinitionFile(String agentId) {
            return agentDir(agentId).resolve("agent.md");
        }

        /**
         * 获取指定 agent 的工作空间目录（workspace/{agentId}/，仅存 session 运行时数据）
         */
        public static Path agentWorkspaceDir(String agentId) {
            return WORKSPACE_DIR.resolve(agentId);
        }

        /**
         * 获取指定 agent 的 config.json 路径（agents/{agentId}/config.json）
         */
        public static Path agentConfigFile(String agentId) {
            return agentDir(agentId).resolve("config.json");
        }

        /**
         * 获取指定 agent 的 context 目录（workspace/{agentId}/context/{sessionId}/）
         */
        public static Path agentContextDir(String agentId, String sessionId) {
            return agentWorkspaceDir(agentId).resolve("context").resolve(sessionId);
        }


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
