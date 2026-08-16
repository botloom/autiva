package cn.bitloom.agentic.permission;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 宿主策略 — 宿主维护的 (server, tool) → 决策 表（对标 learn-claude-code s14）。
 *
 * <p>纪律：不信任外部声明。MCP server 自述的 readOnlyHint 等不作为授权依据，
 * 一切 MCP 工具调用的授权决策只看本策略表；未登记的默认 confirm（安全默认）。
 *
 * <p>策略文件：{@code {project}/.autiva/mcp-policy.json}
 * <pre>
 * {
 *   "rules": [
 *     {"server": "filesystem", "tool": "*", "decision": "allow"},
 *     {"server": "git", "tool": "git_push", "decision": "deny"}
 *   ]
 * }
 * </pre>
 *
 * <p>匹配优先级：精确 (server, tool) &gt; (server, "*") &gt; 默认 CONFIRM。
 */
@Slf4j
@Component
public class McpHostPolicy {

    private static final String AUTIVA_DIR = ".autiva";
    private static final String POLICY_FILE = "mcp-policy.json";

    /** 授权决策 */
    public enum Decision {
        /** 直接放行 */
        ALLOW,
        /** 弹窗确认（经 ApprovalService） */
        CONFIRM,
        /** 硬拒绝（不可审批） */
        DENY
    }

    private record Rule(String server, String tool, Decision decision) {
        boolean matches(String s, String t) {
            return server.equals(s) && ("*".equals(tool) || tool.equals(t));
        }
    }

    /**
     * 查询 (server, tool) 的授权决策。项目无策略文件或未命中规则时默认 CONFIRM。
     */
    public Decision decide(String projectDir, String server, String tool) {
        if (projectDir == null || projectDir.isBlank()) {
            return Decision.CONFIRM;
        }
        List<Rule> rules = loadRules(projectDir);
        // 精确 tool 规则优先于通配规则
        Rule exact = null;
        Rule wildcard = null;
        for (Rule rule : rules) {
            if (rule.matches(server, tool)) {
                if ("*".equals(rule.tool())) {
                    wildcard = rule;
                } else {
                    exact = rule;
                    break;
                }
            }
        }
        Rule hit = exact != null ? exact : wildcard;
        if (hit != null) {
            log.debug("[McpHostPolicy] 命中规则: server={}, tool={}, decision={}",
                    hit.server(), hit.tool(), hit.decision());
            return hit.decision();
        }
        return Decision.CONFIRM;
    }

    /**
     * 加载项目策略文件。文件不存在或解析失败时返回空表（走默认 CONFIRM）。
     */
    private List<Rule> loadRules(String projectDir) {
        Path file = Path.of(projectDir).resolve(AUTIVA_DIR).resolve(POLICY_FILE);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtils.parse(Files.readString(file));
            JsonNode rulesNode = root.path("rules");
            if (!rulesNode.isArray()) {
                return List.of();
            }
            List<Rule> rules = new ArrayList<>();
            for (JsonNode node : rulesNode) {
                String server = node.path("server").asText(null);
                String tool = node.path("tool").asText("*");
                Decision decision = parseDecision(node.path("decision").asText(null));
                if (server == null || server.isBlank() || decision == null) {
                    log.warn("[McpHostPolicy] 忽略非法规则: {}", node);
                    continue;
                }
                rules.add(new Rule(server, tool, decision));
            }
            return rules;
        } catch (IOException | IllegalStateException e) {
            log.warn("[McpHostPolicy] 解析策略文件失败（按默认 CONFIRM 处理）: file={}, error={}",
                    file, e.getMessage());
            return List.of();
        }
    }

    private Decision parseDecision(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Decision.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
