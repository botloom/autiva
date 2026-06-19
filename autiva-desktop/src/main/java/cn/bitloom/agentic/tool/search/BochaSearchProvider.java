package cn.bitloom.agentic.tool.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 博查搜索实现（{@link SearchProvider} 接口）。
 * <p>
 * API 地址：https://api.bochaai.com/v1/web-search
 * 认证方式：Authorization: Bearer {API_KEY}
 * 中文搜索质量最强，国内备案、安全合规、数据不出海
 * </p>
 */
public class BochaSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(BochaSearchProvider.class);

    private static final String BOCHA_API_URL = "https://api.bochaai.com/v1/web-search";

    private final RestClient restClient;

    private final boolean configured;

    public BochaSearchProvider(String apiKey) {
        this.configured = StringUtils.hasText(apiKey);
        if (!this.configured) {
            logger.warn("博查API密钥未配置，网络搜索功能不可用，请在设置页面配置 app.search.bocha-api-key");
            this.restClient = null;
            return;
        }
        this.restClient = RestClient.builder()
                .baseUrl(BOCHA_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WebSearchTool.SearchResult> search(String query, int count) {
        if (!this.configured) {
            logger.warn("博查API密钥未配置，跳过搜索请求，查询: {}", query);
            return Collections.emptyList();
        }
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("count", count);
            requestBody.put("freshness", "noLimit");
            requestBody.put("summary", true);

            Map<String, Object> response = this.restClient.post()
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, errorResponse) -> {
                        logger.error("博查API客户端错误: {} 查询: {}", errorResponse.getStatusCode(), query);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, errorResponse) -> {
                        logger.error("博查API服务器错误: {} 查询: {}", errorResponse.getStatusCode(), query);
                    })
                    .body(Map.class);

            if (response == null || response.isEmpty()) {
                logger.warn("博查API对查询返回空响应: {}", query);
                return Collections.emptyList();
            }

            return parseBochaResults(response);
        }
        catch (Exception e) {
            logger.error("执行博查搜索请求失败，查询: {}", query, e);
            return Collections.emptyList();
        }
    }

    private List<WebSearchTool.SearchResult> parseBochaResults(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            logger.warn("博查响应中缺少data字段，响应键: {}", response.keySet());
            return Collections.emptyList();
        }

        Object webPagesObj = dataMap.get("webPages");
        if (!(webPagesObj instanceof Map<?, ?> webPagesMap)) {
            logger.warn("博查响应data中缺少webPages字段，data键: {}", dataMap.keySet());
            return Collections.emptyList();
        }

        Object valueObj = webPagesMap.get("value");
        if (!(valueObj instanceof List<?> valueList)) {
            logger.warn("博查响应webPages中缺少value字段，webPages键: {}", webPagesMap.keySet());
            return Collections.emptyList();
        }

        if (valueList.isEmpty()) {
            logger.debug("博查搜索返回0条结果，查询可能无匹配");
            return Collections.emptyList();
        }

        List<WebSearchTool.SearchResult> results = new ArrayList<>();
        for (Object item : valueList) {
            if (!(item instanceof Map<?, ?> entry)) {
                continue;
            }
            String name = (String) entry.get("name");
            String url = (String) entry.get("url");
            if (name == null || url == null) {
                continue;
            }
            String summary = entry.get("summary") != null ? (String) entry.get("summary") : "";
            String snippet = entry.get("snippet") != null ? (String) entry.get("snippet") : "";
            String description = !summary.isEmpty() ? summary : snippet;
            results.add(new WebSearchTool.SearchResult(name, url, description));
        }

        if (results.isEmpty() && !valueList.isEmpty()) {
            logger.warn("博查搜索解析失败：{}条原始结果均无法解析，首条原始数据键: {}",
                    valueList.size(), ((Map<?, ?>) valueList.get(0)).keySet());
        }

        return results;
    }

}
