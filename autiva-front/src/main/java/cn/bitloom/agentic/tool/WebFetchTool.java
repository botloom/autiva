package cn.bitloom.agentic.tool;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebFetchTool implements ITool {

    private static final int DEFAULT_TIMEOUT = 30000;
    private static final int MAX_CONTENT_LENGTH = 100 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Set<String> BINARY_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "application/pdf", "application/zip", "application/x-rar-compressed",
        "application/x-7z-compressed", "application/octet-stream",
        "image/", "video/", "audio/"
    ));

    private static final List<String> PREAPPROVED_DOMAINS = Arrays.asList(
        "github.com", "stackoverflow.com", "stackoverflow.blog",
        "docs.python.org", "docs.oracle.com", "developer.mozilla.org",
        "npmjs.com", "pypi.org", "mvnrepository.com",
        "wikipedia.org", "medium.com"
    );

    @Tool(name = "web_fetch", description = "抓取 URL 并提取可读的网页内容。支持重定向检测、超时设置、二进制文件检测。")
    public ToolResult fetch(
            @ToolParam(description = "要抓取的网页 URL") String url,
            @ToolParam(description = "最大获取内容长度（字符数），默认 50000", required = false) Integer maxLength,
            @ToolParam(description = "超时时间（毫秒），默认 30000", required = false) Integer timeout) {
        
        long startTime = System.currentTimeMillis();
        log.info("[ToolCall] web_fetch - 抓取网页: url={}", url);
        
        if (StringUtils.isBlank(url)) {
            return ToolResult.failure("错误：URL 不能为空");
        }

        String normalizedUrl = normalizeUrl(url);
        int maxChars = maxLength != null && maxLength > 0 ? Math.min(maxLength, MAX_CONTENT_LENGTH) : MAX_CONTENT_LENGTH;
        int timeoutMs = timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT;

        try {
            URL parsedUrl = new URL(normalizedUrl);
            String hostname = parsedUrl.getHost();
            boolean isPreapproved = isPreapprovedDomain(hostname);

            FetchResult result = fetchWithRedirects(normalizedUrl, timeoutMs, maxChars);
            long duration = System.currentTimeMillis() - startTime;

            if (result.redirected && !result.finalUrl.equals(normalizedUrl)) {
                String redirectMsg = String.format(
                    "检测到重定向:\n- 原始 URL: %s\n- 最终 URL: %s\n- 状态码: %d\n\n" +
                    "如需直接访问重定向后的 URL，请使用新 URL 重新调用。",
                    normalizedUrl, result.finalUrl, result.statusCode);
                log.info("[ToolCall] web_fetch - 检测到重定向: {} -> {}", normalizedUrl, result.finalUrl);
                return ToolResult.success("检测到重定向", redirectMsg);
            }

            if (result.isBinary) {
                String binaryMsg = String.format(
                    "URL 指向二进制文件:\n- URL: %s\n- Content-Type: %s\n- 大小: %s\n\n" +
                    "提示：此工具无法处理二进制文件，请使用专门的工具。",
                    result.finalUrl, result.contentType, formatSize(result.contentLength));
                return ToolResult.failure(binaryMsg);
            }

            String text = htmlToText(result.content);
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars) + "\n\n... [内容已截断，共 " + text.length() + " 字符] ...";
            }

            log.info("[ToolCall] web_fetch - 抓取成功: url={}, length={}, duration={}ms", 
                     result.finalUrl, text.length(), duration);

            String output = buildOutput(result.finalUrl, text, result.statusCode, 
                                        result.contentType, result.contentLength, duration, isPreapproved);
            return ToolResult.success("抓取网页成功", output);

        } catch (Exception e) {
            log.error("[ToolCall] web_fetch - 抓取失败: url={}", url, e);
            return ToolResult.failure("抓取网页失败: " + e.getMessage());
        }
    }

    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    private boolean isPreapprovedDomain(String hostname) {
        String lowerHostname = hostname.toLowerCase();
        return PREAPPROVED_DOMAINS.stream()
                .anyMatch(domain -> lowerHostname.equals(domain) || lowerHostname.endsWith("." + domain));
    }

    private FetchResult fetchWithRedirects(String urlStr, int timeout, int maxChars) throws Exception {
        String currentUrl = urlStr;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            int statusCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            long contentLength = connection.getContentLengthLong();

            if (isRedirect(statusCode)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                
                if (location == null) {
                    throw new Exception("重定向响应缺少 Location 头");
                }

                if (location.startsWith("/")) {
                    URL baseUrl = new URL(currentUrl);
                    location = baseUrl.getProtocol() + "://" + baseUrl.getHost() + 
                              (baseUrl.getPort() != -1 ? ":" + baseUrl.getPort() : "") + location;
                }

                currentUrl = location;
                redirectCount++;
                continue;
            }

            boolean isBinary = isBinaryContent(contentType);

            String content = "";
            if (!isBinary && statusCode == 200) {
                content = readContent(connection, maxChars);
            }

            connection.disconnect();

            return new FetchResult(
                currentUrl,
                urlStr,
                content,
                statusCode,
                contentType != null ? contentType : "unknown",
                contentLength,
                isBinary,
                redirectCount > 0
            );
        }

        throw new Exception("重定向次数过多 (>" + MAX_REDIRECTS + ")");
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || 
               statusCode == 307 || statusCode == 308;
    }

    private boolean isBinaryContent(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return BINARY_CONTENT_TYPES.stream().anyMatch(lower::startsWith);
    }

    private String readContent(HttpURLConnection connection, int maxChars) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int totalChars = 0;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                totalChars += line.length();
                if (totalChars > maxChars * 2) {
                    break;
                }
            }
        }
        return content.toString();
    }

    private String htmlToText(String html) {
        String text = html;

        text = COMMENT_PATTERN.matcher(text).replaceAll("");
        text = SCRIPT_PATTERN.matcher(text).replaceAll("");
        text = STYLE_PATTERN.matcher(text).replaceAll("");

        text = text.replaceAll("<br\\s*/?>", "\n");
        text = text.replaceAll("<p\\s*>", "\n\n");
        text = text.replaceAll("</p>", "\n");
        text = text.replaceAll("<h[1-6][^>]*>", "\n\n");
        text = text.replaceAll("</h[1-6]>", "\n");
        text = text.replaceAll("<li[^>]*>", "\n- ");
        text = text.replaceAll("</li>", "");
        text = text.replaceAll("<div[^>]*>", "\n");
        text = text.replaceAll("</div>", "");

        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");

        text = decodeHtmlEntities(text);

        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");

        text = text.replaceAll(" \\n", "\n");
        text = text.replaceAll("\\n ", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim();
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

    private String buildOutput(String url, String content, int statusCode, 
                               String contentType, long contentLength, 
                               long duration, boolean isPreapproved) {
        StringBuilder output = new StringBuilder();
        output.append("URL: ").append(url).append("\n");
        output.append("状态码: ").append(statusCode).append("\n");
        output.append("Content-Type: ").append(contentType).append("\n");
        output.append("内容大小: ").append(formatSize(contentLength > 0 ? contentLength : content.length())).append("\n");
        output.append("耗时: ").append(duration).append("ms\n");
        if (isPreapproved) {
            output.append("预批准域名: 是\n");
        }
        output.append("\n").append("─".repeat(50)).append("\n\n");
        output.append(content);
        return output.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static class FetchResult {
        final String finalUrl;
        final String originalUrl;
        final String content;
        final int statusCode;
        final String contentType;
        final long contentLength;
        final boolean isBinary;
        final boolean redirected;

        FetchResult(String finalUrl, String originalUrl, String content, 
                   int statusCode, String contentType, long contentLength,
                   boolean isBinary, boolean redirected) {
            this.finalUrl = finalUrl;
            this.originalUrl = originalUrl;
            this.content = content;
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.isBinary = isBinary;
            this.redirected = redirected;
        }
    }
}
