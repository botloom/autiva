package cn.bitloom.agentic.permission.command;

import java.util.Locale;

/**
 * 命令行解析 — 收敛命令分类与批准前缀提取的公共分词逻辑。
 *
 * <p>原先 {@link CommandClassifier} 内的 tokenize 与 {@link CommandLineParser} 各自实现分词，
 * 处理 {@code @} 前缀、引号、{@code &|/sudo} 的规则存在细微差异且可能漂移。
 * 本类作为公共入口，统一"去修饰 + 分词"的清洗语义，供分类与前缀提取复用。
 */
public final class CommandLineParser {

    private CommandLineParser() {
    }

    /**
     * 命令分词：去除 {@literal @} 前缀修饰、按 {@code &}{@code |} 取第一段、去引号、按空白切分。
     *
     * <p>用于分类决策（{@link CommandClassifier}）。不处理复杂 shell 语法，
     * 仅保留原 {@code tokenize} 语义。
     *
     * @param command 原始命令
     * @return 分词数组；命令为 null/blank 时返回空数组
     */
    public static String[] tokenize(String command) {
        if (command == null || command.isBlank()) {
            return new String[0];
        }
        // 去除 @echo off 这种前缀修饰符的 @
        String trimmed = command.trim().replaceAll("^@\\s*", "");
        // 按 & / | 分割取第一段（主要命令在第一段）
        String firstSegment = trimmed.split("[&|]")[0].trim();
        // 去除引号
        firstSegment = firstSegment.replaceAll("[\"']", "");
        if (firstSegment.isBlank()) {
            return new String[0];
        }
        return firstSegment.split("\\s+");
    }

    /**
     * 提取命令批准 key（前缀）：取前 2 个 token；单 token 命令取 1 个；跳过 {@code sudo}。
     *
     * <p>例：{@code "git push --force"} → {@code "git push"}，
     * {@code "npm install axios"} → {@code "npm install"}，
     * {@code "mkdir"} → {@code "mkdir"}，{@code "sudo rm -rf x"} → {@code "rm -rf"}。
     *
     * <p>语义：引号替换为空格（避免 `"git status"` 去引号后粘连成 {@code gitstatus}），
     * 区分大小写统一小写。
     *
     * @param command 原始命令
     * @return 规范化后的批准前缀；命令为 null/blank 时返回空串
     */
    public static String extractPrefix(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        // 去除前导 @ 修饰符；取 & | ; 分段的第一段（与 tokenize 对齐，避免 @echo off & git push 前缀取错）
        String trimmed = command.trim().replaceAll("^@\\s*", "").split("[&|;]")[0].trim();
        // 引号转空格，防止 token 粘连（已持久化的批准 key 语义不变）
        String clean = trimmed.replaceAll("[\"']", " ");
        String[] tokens = clean.split("\\s+");
        if (tokens.length == 0) {
            return "";
        }
        // 跳过 sudo
        int start = 0;
        if (tokens[0].equalsIgnoreCase("sudo")) {
            start = 1;
        }
        if (start >= tokens.length) {
            return "";
        }
        if (tokens.length - start >= 2) {
            return (tokens[start] + " " + tokens[start + 1]).toLowerCase(Locale.ROOT);
        }
        return tokens[start].toLowerCase(Locale.ROOT);
    }
}
