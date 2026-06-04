package cn.bitloom.agentic.tool.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputSanitizer {

    /** Maximum output size in characters (approx 1MB) */
    public static final int MAX_OUTPUT_CHARS = 1_000_000;

    private static final Pattern ANSI_CSI = Pattern.compile(
            "\\x1B\\[[0-?]*[ -/]*[@-~]");

    private static final Pattern ANSI_OSC = Pattern.compile(
            "\\x1B\\][^\\x07\\x1B]*(?:\\x07|\\x1B\\\\)");

    // CLIXML text nodes: <S S="Error|Warning|Verbose|Debug|stdout|Output|Success">text</S>
    private static final Pattern CLIXML_TEXT_NODE = Pattern.compile(
            "<S S=\"(?:stdout|Output|Success|Error|Warning|Verbose|Debug)[^\"]*\">([\\s\\S]*?)</S>");

    private static final Pattern ANSI_OTHER = Pattern.compile(
            "\\x1B[@-_]");

    private static final Pattern SCREEN_CLEAR = Pattern.compile(
            "\\x1B\\[(?:2|3)?J|\\x1B\\[H");

    private OutputSanitizer() {
    }

    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw;
        // If CLIXML detected, extract text content instead of deleting the whole block
        if (s.contains("#< CLIXML")) {
            s = extractClixmlContent(s);
        }
        s = SCREEN_CLEAR.matcher(s).replaceAll("");
        s = ANSI_OSC.matcher(s).replaceAll("");
        s = ANSI_CSI.matcher(s).replaceAll("");
        s = ANSI_OTHER.matcher(s).replaceAll("");
        s = s.replace("\r\n", "\n");
        s = stripProgressCarriageReturns(s);
        s = s.replace('\r', '\n');
        s = collapseBlankLines(s);
        s = s.strip();
        // Truncate if output exceeds max character limit
        if (s.length() > MAX_OUTPUT_CHARS) {
            s = s.substring(0, MAX_OUTPUT_CHARS)
                    + "\n... [output truncated: exceeded " + MAX_OUTPUT_CHARS + " characters]";
        }
        return s;
    }

    /**
     * Extract readable text from CLIXML output.
     *
     * PowerShell 5.1 with -EncodedCommand wraps output in CLIXML format:
     * <pre>
     * #&lt; CLIXML\r\n
     * hello\r\n              &lt;-- actual output as plain text lines
     * &lt;Objs Version="1.1.0.1" ...&gt;   &lt;-- XML envelope starts
     *   &lt;S S="Error"&gt;...&lt;/S&gt;         &lt;-- error/warning text in XML nodes
     * &lt;/Objs&gt;
     * </pre>
     *
     * Strategy:
     * 1. Extract plain text lines between "#&lt; CLIXML" and "&lt;Objs" start tag
     * 2. Extract text from &lt;S&gt; nodes (for error/warning streams)
     * 3. Discard the CLIXML XML envelope
     */
    private static String extractClixmlContent(String s) {
        List<String> textParts = new ArrayList<>();

        // Strategy 1: Extract plain text lines between CLIXML header and <Objs> start
        // The format is: "#< CLIXML\n<plain text lines>\n<Objs ...>...</Objs>"
        // We split on the <Objs tag to get the text before it
        int clixmlHeaderEnd = s.indexOf("#< CLIXML");
        if (clixmlHeaderEnd >= 0) {
            // Skip the "#< CLIXML" line itself
            int afterHeader = s.indexOf('\n', clixmlHeaderEnd);
            if (afterHeader < 0) afterHeader = s.length();

            // Find where <Objs starts
            int objsStart = s.indexOf("<Objs", afterHeader);
            if (objsStart > afterHeader) {
                String plainText = s.substring(afterHeader, objsStart);
                if (!plainText.isBlank()) {
                    textParts.add(plainText.strip());
                }
            }
        }

        // Strategy 2: Extract text from <S> nodes (error/warning streams)
        Matcher textMatcher = CLIXML_TEXT_NODE.matcher(s);
        while (textMatcher.find()) {
            String text = textMatcher.group(1);
            // Decode CLIXML escape sequences
            text = text.replace("_x000D__x000A_", "\n")
                       .replace("_x000A_", "\n")
                       .replace("_x000D_", "\r")
                       .replace("_x003C_", "<")
                       .replace("_x003E_", ">")
                       .replace("_x0026_", "&")
                       .replace("_x0027_", "'")
                       .replace("_x0022_", "\"");
            if (!text.isBlank()) {
                textParts.add(text.strip());
            }
        }

        // Strategy 3: Check for any non-CLIXML content after the </Objs> closing tag
        int objsClose = s.indexOf("</Objs>");
        if (objsClose >= 0 && objsClose + 7 < s.length()) {
            String afterObjs = s.substring(objsClose + 7);
            if (!afterObjs.isBlank()) {
                textParts.add(afterObjs.strip());
            }
        }

        // Also check for content after "#>" closing (alternative CLIXML terminator)
        // Only check if CLIXML header was found, otherwise "#>" could be part of normal output
        if (clixmlHeaderEnd >= 0) {
            int hashClose = s.indexOf("#>", clixmlHeaderEnd);
            if (hashClose >= 0 && hashClose + 2 < s.length()) {
                String afterHash = s.substring(hashClose + 2);
                if (!afterHash.isBlank()) {
                    textParts.add(afterHash.strip());
                }
            }
        }

        if (textParts.isEmpty()) {
            return "";
        }
        return String.join("\n", textParts);
    }

    public static String truncate(String cleaned, int maxLines) {
        if (cleaned == null || cleaned.isEmpty() || maxLines <= 0) {
            return cleaned == null ? "" : cleaned;
        }
        // Count newlines without creating a full array
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
                    String lastFragment = line.substring(lastCr + 1);
                    out.append(lastFragment);
                }
                if (c == '\n') {
                    out.append('\n');
                }
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
