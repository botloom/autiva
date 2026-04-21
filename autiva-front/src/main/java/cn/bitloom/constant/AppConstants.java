package cn.bitloom.constant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The type App constants.
 *
 * @author bitloom
 */
public class AppConstants {

    private AppConstants() {
    }

    public static class Base {
        private Base() {
        }

        public static final String USER_HOME = System.getProperty("user.home");
        public static final Path APP_DIR = Paths.get(USER_HOME, ".autiva");
        public static final Path LOGS_DIR = APP_DIR.resolve("logs");
        public static final Path SKILL_DIR = APP_DIR.resolve("skills");
        public static final Path MCP_DIR = APP_DIR.resolve("mcp");
        public static final Path WORKSPACE_DIR = APP_DIR.resolve("workspace");
        public static final Path SUBAGENT_DIR = WORKSPACE_DIR.resolve("subagents");
        public static final Path SESSION_DIR = APP_DIR.resolve("sessions");
        public static final Path MCP_CONFIG_FILE = MCP_DIR.resolve("mcp-servers.json");
        public static final Path CONFIG_FILE = APP_DIR.resolve("settings.properties");
        public static final Path CODE_PROJECT_DIR = APP_DIR.resolve("project");
    }

    /**
     * The type Window.
     */
    public static class Stage {
        private Stage() {
        }

        /**
         * The constant WIDTH.
         */
        public static final double WIDTH = 800;
        /**
         * The constant HEIGHT.
         */
        public static final double HEIGHT = 500;
        /**
         * The constant FXML.
         */
        public static final String FXML = "/cn/bitloom/index.fxml";
        /**
         * The constant ICON.
         */
        public static final String ICON = "/cn/bitloom/images/icon.png";
    }

}
