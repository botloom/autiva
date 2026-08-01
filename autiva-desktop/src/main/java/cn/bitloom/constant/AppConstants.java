package cn.bitloom.constant;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

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
        public static final Path SUBAGENTS_DIR = APP_DIR.resolve("subagents");
        public static final Path SKILLS_DIR = APP_DIR.resolve("skills");
        public static final Path SETTINGS_FILE = APP_DIR.resolve("settings.yaml");
        public static final Path PROJECTS_DIR = APP_DIR.resolve("projects");
        public static final Path PROJECTS_REGISTRY_FILE = PROJECTS_DIR.resolve("registry.json");

    }

    public static class Agents {
        private Agents() {
        }

        public static final String WORK_AGENT = "work";
        public static final String CODE_AGENT = "code";

        public static Path agentDir(String agentId) {
            return Base.AGENTS_DIR.resolve(agentId);
        }

        public static Path subagentDir(String name) {
            return Base.SUBAGENTS_DIR.resolve(name);
        }
    }

    public static class MainAgent {
        private MainAgent() {
        }

        public static Path agentFile(String agentId) {
            return Agents.agentDir(agentId).resolve("agent.md");
        }

        public static Path configFile(String agentId) {
            return Agents.agentDir(agentId).resolve("config.json");
        }

    }

    public static class Session {
        private Session() {
        }

        /** code 目录下的保留名（非项目），sessionDir 解析时拒绝 */
        private static final Set<String> CODE_RESERVED_NAMES = Set.of("sessions", "memory");

        /**
         * 解析 sessionId 确定存储路径。
         * <p>
         * sessionId 格式：
         * <ul>
         *   <li>work 模式：work-{type}-{source}-{userId}-{timestamp}</li>
         *   <li>code 模式：code-{projectName}-{type}-{source}-{userId}-{timestamp}</li>
         * </ul>
         * code 模式必须包含 projectName，且不能是保留名（sessions/memory）。
         */
        public static Path sessionDir(String sessionId) {
            String[] parts = sessionId.split("-", 3);
            String mode = parts[0];
            if ("work".equals(mode)) {
                return Base.WORKSPACE_DIR.resolve("work").resolve("sessions").resolve(sessionId);
            }
            if ("code".equals(mode)) {
                if (parts.length < 2 || parts[1].isBlank()) {
                    throw new IllegalStateException("code 模式 sessionId 必须包含 projectName: " + sessionId);
                }
                String projectName = parts[1];
                if (CODE_RESERVED_NAMES.contains(projectName)) {
                    throw new IllegalStateException("非法 projectName（保留名）: " + projectName);
                }
                return Base.WORKSPACE_DIR.resolve("code").resolve(projectName).resolve("sessions").resolve(sessionId);
            }
            throw new IllegalArgumentException("未知 session mode: " + mode);
        }

        public static Path metadataFile(String sessionId) {
            return sessionDir(sessionId).resolve("metadata.json");
        }

        public static Path eventsFile(String sessionId) {
            return sessionDir(sessionId).resolve("events.jsonl");
        }

    }

    public static class Memory {
        private Memory() {
        }

        public static Path workMemoryDir() {
            return Base.WORKSPACE_DIR.resolve("work").resolve("memory");
        }

        public static Path projectMemoryDir(String projectName) {
            return Base.WORKSPACE_DIR.resolve("code").resolve(projectName).resolve("memory");
        }
    }

    public static class Rules {
        private Rules() {
        }

        public static Path codeGlobalRulesFile() {
            return Base.WORKSPACE_DIR.resolve("code").resolve("AUTIVA.md");
        }
    }

    public static class Context {
        private Context() {}
    }

    public static class Evolve {
        private Evolve() {}

        public static final Path EVOLVE_DIR = APP_DIR.resolve("evolve");
        public static final Path GENES_DIR = EVOLVE_DIR.resolve("genes");
        public static final Path ROUTING_FILE = EVOLVE_DIR.resolve("routing.json");
        public static final Path MEMORY_DIR = EVOLVE_DIR.resolve("memory");
        public static final Path MEMORY_RULES_FILE = MEMORY_DIR.resolve("rules.jsonl");
        public static final Path EXECUTIONS_DIR = APP_DIR.resolve("logs").resolve("executions");

        public static Path geneDir(String geneId) {
            return GENES_DIR.resolve(geneId);
        }

        public static Path geneMetaFile(String geneId) {
            return geneDir(geneId).resolve("gene.json");
        }

        public static Path geneCodeFile(String geneId) {
            return geneDir(geneId).resolve("impl.java");
        }

        public static Path geneVersionsDir(String geneId) {
            return geneDir(geneId).resolve("versions");
        }

        public static Path executionLogFile(String date) {
            return EXECUTIONS_DIR.resolve(date + ".jsonl");
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
