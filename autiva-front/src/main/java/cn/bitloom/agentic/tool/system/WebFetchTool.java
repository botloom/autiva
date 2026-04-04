package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebFetchTool implements cn.bitloom.agentic.tool.ITool {

    private static final int MAX_CONTENT_LENGTH = 50000;
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Tool(name = "web_fetch", description = "抓取 URL 并提取可读的网页内容（HTML 转换为 Markdown/Text）")
    public ToolResult fetch(@ToolParam(description = "要抓取的网页 URL") String url,
                       @ToolParam(description = "最大获取内容长度（字符数），默认 50000", required = false) Integer maxLength) {
        log.info("[ToolCall] web_fetch - 抓取网页: url={}", url);
        if (StringUtils.isBlank(url)) {
            return ToolResult.failure("错误：URL 不能为空");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        int maxChars = maxLength != null && maxLength > 0 ? Math.min(maxLength, MAX_CONTENT_LENGTH) : MAX_CONTENT_LENGTH;

        try {
            String content = fetchUrlContent(url);

            String text = htmlToText(content);

            if (text.length() > maxChars) {
                text = text.substring(0, maxChars) + "\n\n... [内容已截断] ...";
            }

            log.info("[ToolCall] web_fetch - 抓取成功: url={}, length={}", url, text.length());
            String result = "URL: " + url + "\n" +
                   "内容长度: " + text.length() + " 字符\n\n" +
                   "内容:\n" +
                   "─".repeat(50) + "\n" +
                   text;
            return ToolResult.success("抓取网页成功", result);

        } catch (Exception e) {
            log.error("[ToolCall] web_fetch - 抓取失败: url={}", url, e);
            return ToolResult.failure("抓取网页失败: " + e.getMessage());
        }
    }

    private String fetchUrlContent(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        StringBuilder content = new StringBuilder();

        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }

    private String htmlToText(String html) {
        String text = html;

        text = SCRIPT_PATTERN.matcher(text).replaceAll("");
        text = STYLE_PATTERN.matcher(text).replaceAll("");

        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");

        text = decodeHtmlEntities(text);

        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");

        text = text.trim();

        return text;
    }

    private String decodeHtmlEntities(String text) {
        return text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&apos;", "'")
                   .replace("&ndash;", "-")
                   .replace("&mdash;", "-")
                   .replace("&hellip;", "...")
                   .replace("&copy;", "(c)")
                   .replace("&reg;", "(R)")
                   .replace("&trade;", "(TM)")
                   .replace("&lsquo;", "'")
                   .replace("&rsquo;", "'")
                   .replace("&ldquo;", "\"")
                   .replace("&rdquo;", "\"");
    }
}