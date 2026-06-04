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

    /**
     * The type Base.
     */
    public static class Base {
        private Base() {
        }

        /**
         * The constant USER_HOME.
         */
        public static final String USER_HOME = System.getProperty("user.home");
        /**
         * The constant APP_DIR.
         */
        public static final Path APP_DIR = Paths.get(USER_HOME, ".autiva");
        /**
         * The constant LOGS_DIR.
         */
        public static final Path LOGS_DIR = APP_DIR.resolve("logs");
        /**
         * The constant SKILL_DIR.
         */
        public static final Path SKILL_DIR = APP_DIR.resolve("skills");
        /**
         * The constant MCP_DIR.
         */
        public static final Path MCP_DIR = APP_DIR.resolve("mcp");
        /**
         * The constant WORKSPACE_DIR.
         */
        public static final Path WORKSPACE_DIR = APP_DIR.resolve("workspace");
        /**
         * The constant SESSION_DIR.
         */
        public static final Path SESSION_DIR = APP_DIR.resolve("sessions");
        /**
         * The constant MCP_CONFIG_FILE.
         */
        public static final Path MCP_CONFIG_FILE = MCP_DIR.resolve("mcp-servers.json");
        /**
         * The constant CONFIG_FILE.
         */
        public static final Path CONFIG_FILE = APP_DIR.resolve("settings.properties");
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

        /**
         * The constant NAME.
         */
        public static final String NAME = "Autiva";
    }

}
