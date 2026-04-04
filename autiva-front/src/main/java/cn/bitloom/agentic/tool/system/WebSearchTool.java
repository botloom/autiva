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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WebSearchTool implements cn.bitloom.agentic.tool.ITool {

    private static final String DUCKDUCKGO_URL = "https://duckduckgo.com/html/";
    private static final int MAX_RESULTS = 10;

    @Tool(name = "web_search", description = "通过 DuckDuckGo 搜索网页，无需 API 密钥")
    public ToolResult search(@ToolParam(description = "搜索关键词") String query,
                        @ToolParam(description = "返回结果数量，默认 10", required = false) Integer limit) {
        log.info("[ToolCall] web_search - 搜索网页: query={}, limit={}", query, limit);
        if (StringUtils.isBlank(query)) {
            return ToolResult.failure("错误：搜索关键词不能为空");
        }

        if (limit == null || limit <= 0) {
            limit = MAX_RESULTS;
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + "?q=" + encodedQuery;

            String html = fetchUrl(searchUrl);

            String results = parseSearchResults(html, limit);

            if (results.isEmpty()) {
                log.info("[ToolCall] web_search - 无结果: query={}", query);
                return ToolResult.success("未找到与 '" + query + "' 相关的搜索结果");
            }

            log.info("[ToolCall] web_search - 搜索成功: query={}, 结果数={}", query, limit);
            String result = "搜索结果（关键词: " + query + "）：\n\n" + results;
            return ToolResult.success("搜索成功", result);

        } catch (Exception e) {
            log.error("[ToolCall] web_search - 搜索失败: query={}", query, e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private String fetchUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }

    private String parseSearchResults(String html, int limit) {
        StringBuilder results = new StringBuilder();

        String[] lines = html.split("\n");
        int count = 0;
        boolean inResult = false;
        StringBuilder currentResult = new StringBuilder();

        for (String line : lines) {
            if (line.contains("result__snippet") || line.contains("search Engine_result")) {
                inResult = true;
                currentResult = new StringBuilder();
            }

            if (inResult) {
                currentResult.append(line).append("\n");
            }

            if (inResult && line.contains("</a>")) {
                inResult = false;
                String result = currentResult.toString();

                int snippetStart = result.indexOf("result__snippet");
                if (snippetStart == -1) {
                    snippetStart = result.indexOf("search-provider");
                }

                if (snippetStart != -1) {
                    int linkStart = result.lastIndexOf("href=\"", snippetStart);
                    int linkEnd = result.indexOf("\"", linkStart + 6);

                    int textStart = result.indexOf(">", linkEnd);
                    int textEnd = result.indexOf("<", textStart);

                    if (linkStart != -1 && linkEnd != -1 && textStart != -1 && textEnd != -1) {
                        String link = result.substring(linkStart + 6, linkEnd);
                        String title = result.substring(textStart + 1, textEnd).trim();

                        if (!title.isEmpty() && !link.isEmpty()) {
                            count++;
                            results.append(count).append(". ").append(title).append("\n");
                            results.append("   链接: ").append(link).append("\n\n");

                            if (count >= limit) {
                                break;
                            }
                        }
                    }
                }

                currentResult = new StringBuilder();
            }
        }

        return results.toString();
    }
}