package cn.bitloom.agentic.workflow.builtin;

import cn.bitloom.agentic.workflow.WorkflowContext;
import cn.bitloom.agentic.workflow.WorkflowRegistry;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 内置首个工作流：code-review（对标 learn-claude-code s16 示例）。
 *
 * <p>编排形状固定（宿主代码，不靠模型逐轮决策）：
 * 并行审计（安全/性能/风格）→ 逐条验证命中（top N）→ 合并排序输出。
 * 同时验证 parallel + schema + journal 全链路。
 */
public class CodeReviewWorkflow {

    /** 单条审计发现 */
    public record Finding(String dimension, String file, String line, String severity, String description) {
    }

    private static final int VERIFY_LIMIT = 10;
    private static final String FINDINGS_SCHEMA = """
            {"type":"object","required":["findings"],"properties":{"findings":{"type":"array","items":{
              "type":"object","required":["file","severity","description"],"properties":{
              "file":{"type":"string"},"line":{"type":"string"},"severity":{"type":"string"},
              "description":{"type":"string"}}}}}}""";
    private static final String VERIFY_SCHEMA = """
            {"type":"object","required":["valid"],"properties":{"valid":{"type":"boolean"},
              "reason":{"type":"string"}}}""";

    public WorkflowRegistry.WorkflowMeta meta() {
        return new WorkflowRegistry.WorkflowMeta(
                "code-review",
                "代码审查：并行审计安全/性能/风格，逐条验证命中，合并排序输出报告",
                "{\"type\":\"object\",\"properties\":{\"focus\":{\"type\":\"string\",\"description\":\"审查重点或文件范围（可选）\"}}}",
                this::run);
    }

    private String run(WorkflowContext ctx, Map<String, Object> args) {
        String focus = args != null && args.get("focus") instanceof String f && !f.isBlank() ? f : "全项目最近改动";

        ctx.phase("并行审计：安全 / 性能 / 风格");
        String dimensionsPromptBase = "审查范围：" + focus
                + "。只报告确实存在的问题，每条注明文件（与行号），不要臆造。";

        List<String> audits = ctx.parallel(List.of(
                () -> ctx.agent("Explore",
                        dimensionsPromptBase + "\n你负责【安全】维度：注入、越权、敏感信息泄漏、不安全依赖。",
                        FINDINGS_SCHEMA, "audit-security"),
                () -> ctx.agent("Explore",
                        dimensionsPromptBase + "\n你负责【性能】维度：N+1 查询、重复计算、内存泄漏、阻塞调用。",
                        FINDINGS_SCHEMA, "audit-performance"),
                () -> ctx.agent("Explore",
                        dimensionsPromptBase + "\n你负责【风格】维度：与项目约定不一致、死代码、误导性命名。",
                        FINDINGS_SCHEMA, "audit-style")));

        List<Finding> findings = new ArrayList<>();
        String[] dimensionNames = { "安全", "性能", "风格" };
        for (int i = 0; i < audits.size(); i++) {
            findings.addAll(parseFindings(audits.get(i), dimensionNames[i]));
        }
        ctx.log("审计完成：共 {} 条候选发现", findings.size());
        if (findings.isEmpty()) {
            return "code-review 结果：未发现问题（范围：" + focus + "）";
        }

        ctx.phase("逐条验证命中");
        List<Finding> top = findings.stream()
                .sorted(Comparator.comparingInt(f -> severityRank(f.severity())))
                .limit(VERIFY_LIMIT)
                .toList();
        List<Finding> verified = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Finding finding = top.get(i);
            String verdict = ctx.agent("Explore",
                    "验证以下代码审查发现是否真实命中（读文件核实，不要臆测）：\n"
                            + "文件：" + finding.file() + " 行：" + finding.line() + "\n"
                            + "描述：" + finding.description(),
                    VERIFY_SCHEMA, "verify-" + finding.file() + "-" + i);
            if (parseValid(verdict)) {
                verified.add(finding);
            }
            else {
                ctx.log("剔除误报：{} {}", finding.file(), finding.description());
            }
        }
        ctx.log("验证完成：{} / {} 条确认命中", verified.size(), top.size());

        ctx.phase("合并排序输出");
        Map<String, List<Finding>> byDimension = new TreeMap<>();
        for (Finding finding : verified) {
            byDimension.computeIfAbsent(finding.dimension(), k -> new ArrayList<>()).add(finding);
        }
        StringBuilder report = new StringBuilder("code-review 报告（范围：" + focus + "）\n");
        report.append("候选 ").append(findings.size()).append(" 条，验证确认 ").append(verified.size())
                .append(" 条（严重度：critical > high > medium > low）\n");
        if (byDimension.isEmpty()) {
            report.append("所有候选均未通过验证（误报）。\n");
        }
        for (Map.Entry<String, List<Finding>> entry : byDimension.entrySet()) {
            report.append("\n## ").append(entry.getKey()).append("（").append(entry.getValue().size()).append("）\n");
            for (Finding finding : entry.getValue()) {
                report.append("- [").append(finding.severity()).append("] ")
                        .append(finding.file()).append(finding.line() != null && !finding.line().isBlank()
                                ? ":" + finding.line() : "")
                        .append(" — ").append(finding.description()).append("\n");
            }
        }
        return report.toString();
    }

    private static List<Finding> parseFindings(String json, String dimension) {
        try {
            String trimmed = stripCodeFence(json);
            Map<String, Object> parsed = JsonUtils.fromJson(trimmed, new TypeReference<Map<String, Object>>() {});
            Object items = parsed != null ? parsed.get("findings") : null;
            if (!(items instanceof List<?> list)) {
                return List.of();
            }
            List<Finding> findings = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    findings.add(new Finding(dimension,
                            String.valueOf(map.get("file")),
                            map.get("line") != null ? String.valueOf(map.get("line")) : "",
                            String.valueOf(map.get("severity")),
                            String.valueOf(map.get("description"))));
                }
            }
            return findings;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    private static boolean parseValid(String json) {
        try {
            String trimmed = stripCodeFence(json);
            JsonNode node = JsonUtils.parse(trimmed);
            return node != null && node.has("valid") && node.get("valid").asBoolean(false);
        }
        catch (Exception e) {
            return false;
        }
    }

    private static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toLowerCase()) {
            case "critical" -> 0;
            case "high" -> 1;
            case "medium" -> 2;
            default -> 3;
        };
    }
}
