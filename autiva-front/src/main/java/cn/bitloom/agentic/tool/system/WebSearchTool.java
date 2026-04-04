package cn.bitloom.agentic.tool.system;

import cn.bitloom.agentic.tool.ITool;
import cn.bitloom.agentic.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebSearchTool implements ITool {

    private static final String DUCKDUCKGO_URL = "https://duckduckgo.com/html/";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int TIMEOUT = 30000;

    private static final Pattern RESULT_PATTERN = Pattern.compile(
        "<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?" +
        "<a[^>]+class=\"result__snippet\"[^>]*>([^<]*)</a>",
        Pattern.DOTALL
    );

    private static final Pattern URL_PATTERN = Pattern.compile("uddg=([^&]+)");

    @Tool(name = "web_search", description = "通过 DuckDuckGo 搜索网页。支持域名过滤、结果数量限制。无需 API 密钥。")
    public ToolResult search(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "返回结果数量，默认 10，最大 50", required = false) Integer limit,
            @ToolParam(description = "只搜索这些域名的内容，多个域名用逗号分隔", required = false) String allowedDomains,
            @ToolParam(description = "排除这些域名的结果，多个域名用逗号分隔", required = false) String blockedDomains) {
        
        long startTime = System.currentTimeMillis();
        log.info("[ToolCall] web_search - 搜索网页: query={}, limit={}", query, limit);
        
        if (StringUtils.isBlank(query)) {
            return ToolResult.failure("错误：搜索关键词不能为空");
        }

        int resultLimit = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;

        try {
            String searchQuery = buildSearchQuery(query, allowedDomains, blockedDomains);
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + "?q=" + encodedQuery;

            String html = fetchUrl(searchUrl);
            List<SearchResult> results = parseSearchResults(html, resultLimit);
            long duration = System.currentTimeMillis() - startTime;

            if (results.isEmpty()) {
                log.info("[ToolCall] web_search - 无结果: query={}", query);
                return ToolResult.success("未找到与 '" + query + "' 相关的搜索结果");
            }

            String output = formatResults(query, results, duration, allowedDomains, blockedDomains);
            log.info("[ToolCall] web_search - 搜索成功: query={}, 结果数={}, 耗时={}ms", query, results.size(), duration);
            return ToolResult.success("搜索成功", output);

        } catch (Exception e) {
            log.error("[ToolCall] web_search - 搜索失败: query={}", query, e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private String buildSearchQuery(String query, String allowedDomains, String blockedDomains) {
        StringBuilder searchQuery = new StringBuilder(query);

        if (StringUtils.isNotBlank(allowedDomains)) {
            String[] domains = allowedDomains.split(",");
            for (String domain : domains) {
                domain = domain.trim();
                if (!domain.isEmpty()) {
                    searchQuery.append(" site:").append(domain);
                }
            }
        }

        if (StringUtils.isNotBlank(blockedDomains)) {
            String[] domains = blockedDomains.split(",");
            for (String domain : domains) {
                domain = domain.trim();
                if (!domain.isEmpty()) {
                    searchQuery.append(" -site:").append(domain);
                }
            }
        }

        return searchQuery.toString();
    }

    private String fetchUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }

    private List<SearchResult> parseSearchResults(String html, int limit) {
        List<SearchResult> results = new ArrayList<>();

        String[] resultBlocks = html.split("<div class=\"result");

        for (String block : resultBlocks) {
            if (results.size() >= limit) break;

            String title = extractTitle(block);
            String url = extractUrl(block);
            String snippet = extractSnippet(block);

            if (title != null && url != null && !url.isEmpty()) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        return results;
    }

    private String extractTitle(String block) {
        Pattern titlePattern = Pattern.compile("<a[^>]+class=\"result__a\"[^>]*>([^<]+)</a>");
        Matcher matcher = titlePattern.matcher(block);
        if (matcher.find()) {
            return cleanHtmlEntities(matcher.group(1).trim());
        }
        return null;
    }

    private String extractUrl(String block) {
        Pattern urlPattern = Pattern.compile("uddg=([^&\"\\s]+)");
        Matcher matcher = urlPattern.matcher(block);
        if (matcher.find()) {
            try {
                return java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String extractSnippet(String block) {
        Pattern snippetPattern = Pattern.compile("<a[^>]+class=\"result__snippet\"[^>]*>([^<]*)</a>");
        Matcher matcher = snippetPattern.matcher(block);
        if (matcher.find()) {
            String snippet = matcher.group(1).trim();
            if (!snippet.isEmpty()) {
                return cleanHtmlEntities(snippet);
            }
        }
        return null;
    }

    private String cleanHtmlEntities(String text) {
        return text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private String formatResults(String query, List<SearchResult> results, long duration,
                                 String allowedDomains, String blockedDomains) {
        StringBuilder output = new StringBuilder();
        
        output.append("搜索关键词: ").append(query).append("\n");
        output.append("结果数量: ").append(results.size()).append("\n");
        output.append("搜索耗时: ").append(duration).append("ms\n");
        
        if (StringUtils.isNotBlank(allowedDomains)) {
            output.append("限定域名: ").append(allowedDomains).append("\n");
        }
        if (StringUtils.isNotBlank(blockedDomains)) {
            output.append("排除域名: ").append(blockedDomains).append("\n");
        }
        
        output.append("\n").append("─".repeat(50)).append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            output.append(String.format("%d. %s\n", i + 1, result.title));
            output.append(String.format("   URL: %s\n", result.url));
            if (result.snippet != null && !result.snippet.isEmpty()) {
                output.append(String.format("   摘要: %s\n", result.snippet));
            }
            output.append("\n");
        }

        output.append("提示: 使用 web_fetch 工具可以获取网页的详细内容");

        return output.toString();
    }

    private record SearchResult(String title, String url, String snippet) {
    }
}
