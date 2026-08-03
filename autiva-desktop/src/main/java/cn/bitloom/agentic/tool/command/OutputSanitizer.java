package cn.bitloom.agentic.tool.command;

import java.util.regex.Pattern;

public final class OutputSanitizer {

    /** Maximum output size in characters (approx 1MB) */
    public static final int MAX_OUTPUT_CHARS = 1_000_000;

    private static final Pattern ANSI_CSI = Pattern.compile(
            "\\x1B\\[[0-?]*[ -/]*[@-~]");

    private static final Pattern ANSI_OSC = Pattern.compile(
            "\\x1B\\][^\\x07\\x1B]*(?:\\x07|\\x1B\\\\)");

    private static final Pattern ANSI_OTHER = Pattern.compile(
            "\\x1B[@-_]");

    private static final Pattern SCREEN_CLEAR = Pattern.compile(
            "\\x1B\\[(?:2|3)?J|\\x1B\\[H");

    private OutputSanitizer() {}

    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw;
        s = SCREEN_CLEAR.matcher(s).replaceAll("");
        s = ANSI_OSC.matcher(s).replaceAll("");
        s = ANSI_CSI.matcher(s).replaceAll("");
        s = ANSI_OTHER.matcher(s).replaceAll("");
        s = s.replace("\r\n", "\n");
        s = stripProgressCarriageReturns(s);
        s = s.replace('\r', '\n');
        s = collapseBlankLines(s);
        s = s.strip();
        if (s.length() > MAX_OUTPUT_CHARS) {
            s = s.substring(0, MAX_OUTPUT_CHARS)
                    + "\n... [output truncated: exceeded " + MAX_OUTPUT_CHARS + " characters]";
        }
        return s;
    }

    public static String truncate(String cleaned, int maxLines) {
        if (cleaned == null || cleaned.isEmpty() || maxLines <= 0) {
            return cleaned == null ? "" : cleaned;
        }
        int lineCount = 1;
        int cutPos = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) == '\n') {
                lineCount++;
                if (lineCount > maxLines && cutPos < 0) {
                    cutPos = i;
                }
            }
        }
        if (lineCount <= maxLines) {
            return cleaned;
        }
        return cleaned.substring(0, cutPos)
                + "\n... [output truncated: " + lineCount
                + " total lines, showing first " + maxLines + "]\n";
    }

    /** 头部保留行数 */
    private static final int HEAD_LINES = 200;
    /** 尾部保留行数 */
    private static final int TAIL_LINES = 300;

    /**
     * 头+尾截断（v13）：当输出行数超过 maxLines 时，保留前 {@value #HEAD_LINES} 行 + 后 {@value #TAIL_LINES} 行，
     * 中间插入 "[... X lines truncated ...]" 标记。对标 Claude Code 的输出截断策略，
     * 保留头部（错误上下文，如编译错误的根因）和尾部（最终结果）。
     *
     * @param cleaned 已清理的输出
     * @param maxLines 触发截断的行数阈值
     * @return 头尾保留的输出
     */
    public static String truncateHeadTail(String cleaned, int maxLines) {
        if (cleaned == null || cleaned.isEmpty() || maxLines <= 0) {
            return cleaned == null ? "" : cleaned;
        }
        // 收集所有换行符位置（行边界）
        java.util.List<Integer> newlines = new java.util.ArrayList<>();
        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) == '\n') {
                newlines.add(i);
            }
        }
        int totalLines = newlines.size() + 1;
        if (totalLines <= maxLines) {
            return cleaned;
        }
        int preserve = HEAD_LINES + TAIL_LINES;
        if (totalLines <= preserve) {
            return cleaned;
        }
        // 头部结束位置：第 HEAD_LINES 行之后（即第 HEAD_LINES 个换行符之后）
        int headEndIdx = newlines.get(HEAD_LINES - 1) + 1;
        // 尾部起始位置：倒数第 TAIL_LINES 行的起点（即倒数第 TAIL_LINES 个换行符 + 1）
        int tailStartIdx = newlines.get(newlines.size() - TAIL_LINES) + 1;
        int truncated = totalLines - preserve;
        return cleaned.substring(0, headEndIdx)
                + "\n[... " + truncated + " lines truncated ...]\n"
                + cleaned.substring(tailStartIdx);
    }

    private static String stripProgressCarriageReturns(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int lineStart = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || i == s.length() - 1) {
                int end = (c == '\n') ? i : i + 1;
                String line = s.substring(lineStart, end);
                if (line.indexOf('\r') < 0) {
                    out.append(line);
                } else {
                    int lastCr = line.lastIndexOf('\r');
                    out.append(line.substring(lastCr + 1));
                }
                if (c == '\n') out.append('\n');
                lineStart = i + 1;
            }
        }
        return out.toString();
    }

    private static String collapseBlankLines(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevBlank = false;
        for (int i = 0; i < s.length(); i++) {
            int nlIdx = s.indexOf('\n', i);
            if (nlIdx < 0) {
                sb.append(s, i, s.length());
                break;
            }
            String line = s.substring(i, nlIdx);
            boolean blank = line.trim().isEmpty();
            if (!(blank && prevBlank)) {
                sb.append(line).append('\n');
            }
            prevBlank = blank;
            i = nlIdx;
        }
        return sb.toString();
    }
}
