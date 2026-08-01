package cn.bitloom.agentic.tool.search;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import cn.bitloom.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 网络搜索工具，通过 SearchProvider 策略接口委托搜索操作。
 * <p>
 * 通过 {@link SearchProvider} 策略接口委托实际搜索操作，
 * 支持域名过滤并返回结构化搜索结果。
 * </p>
 */
public class WebSearchTool extends AbstractTool<WebSearchTool.Input> {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);

    private static final String DESCRIPTION = """
            网络搜索。返回包含标题和 URL 的结果块。支持域名过滤。回答后必须在末尾列出"来源："，将所有引用 URL 作为 markdown 链接。
            """;

    private final SearchProvider searchProvider;

    private final int resultCount;

    public record Input(
            @ToolParam(description = "要使用的搜索查询") String query,
            @ToolParam(description = "仅包含来自这些域名的搜索结果", required = false) List<String> allowedDomains,
            @ToolParam(description = "永远不包含来自这些域名的搜索结果", required = false) List<String> blockedDomains
    ) {}

    private WebSearchTool(Builder builder) {
        super("WebSearch", DESCRIPTION, Input.class);
        Assert.notNull(builder.searchProvider, "SearchProvider不能为null");
        this.searchProvider = builder.searchProvider;
        this.resultCount = builder.resultCount;
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        if (!StringUtils.hasText(input.query())) {
            logger.warn("提供了空的搜索查询");
            return ToolResult.error("搜索查询不能为空");
        }

        try {
            if (!CollectionUtils.isEmpty(input.allowedDomains()) || !CollectionUtils.isEmpty(input.blockedDomains())) {
                logger.debug("将应用客户端域名过滤。允许的域名: {}, 阻止的域名: {}",
                        input.allowedDomains(), input.blockedDomains());
            }

            List<SearchResult> allResults = this.searchProvider.search(input.query(), this.resultCount);

            if (allResults.isEmpty()) {
                logger.warn("搜索API对查询返回空响应: {}", input.query());
                return ToolResult.success("未找到搜索结果", Map.of("query", input.query(), "count", 0));
            }

            List<SearchResult> filteredResults = this.applyDomainFiltering(allResults, input.allowedDomains(),
                    input.blockedDomains());

            if (filteredResults.size() < allResults.size()) {
                int filtered = allResults.size() - filteredResults.size();
                logger.info("搜索'{}'返回{}条结果，{}条被域名规则过滤，剩余{}条",
                        input.query(), allResults.size(), filtered, filteredResults.size());
            }
            else {
                logger.debug("搜索'{}'返回{}条结果（未应用过滤）", input.query(), allResults.size());
            }

            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .message(filteredResults.size() + " 条搜索结果")
                    .data(Map.of("query", input.query(), "count", filteredResults.size()))
                    .rawOutput(JsonUtils.toJson(filteredResults))
                    .build();

        }
        catch (Exception e) {
            logger.error("执行搜索请求时出错，查询: {}", input.query(), e);
            return ToolResult.error("执行搜索请求时出错: " + e.getMessage());
        }
    }

    public record SearchResult(String title, String url, String description) {
    }

    private List<SearchResult> applyDomainFiltering(List<SearchResult> results,
            List<String> allowedDomains, List<String> blockedDomains) {

        if (CollectionUtils.isEmpty(allowedDomains) && CollectionUtils.isEmpty(blockedDomains)) {
            return results;
        }

        Set<String> allowedSet = toNormalizedDomainSet(allowedDomains);
        Set<String> blockedSet = toNormalizedDomainSet(blockedDomains);

        return results.stream()
                .filter(result -> filterByDomain(result, allowedSet, blockedSet))
                .toList();
    }

    private Set<String> toNormalizedDomainSet(List<String> domains) {
        return CollectionUtils.isEmpty(domains) ? Collections.emptySet()
                : domains.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    private boolean filterByDomain(SearchResult result, Set<String> allowedSet, Set<String> blockedSet) {
        String url = result.url();
        if (url == null) {
            return false;
        }
        String domain = extractDomain(url);

        if (!allowedSet.isEmpty() && !matchesDomain(domain, allowedSet)) {
            return false;
        }

        if (!blockedSet.isEmpty() && matchesDomain(domain, blockedSet)) {
            return false;
        }

        return true;
    }

    private String extractDomain(String url) {
        try {
            String normalizedUrl = url;
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                normalizedUrl = "https://" + url;
            }

            URI uri = new URI(normalizedUrl);
            String host = uri.getHost();

            if (host != null) {
                return host.toLowerCase();
            }

            logger.warn("URI解析未能从URL提取主机: {}，使用回退方法", url);
            return extractDomainFallback(url);
        }
        catch (URISyntaxException e) {
            logger.warn("解析URL失败: {}，使用回退提取", url);
            return extractDomainFallback(url);
        }
    }

    private String extractDomainFallback(String url) {
        try {
            String domain = url.toLowerCase();
            if (domain.contains("://")) {
                domain = domain.substring(domain.indexOf("://") + 3);
            }
            if (domain.contains("/")) {
                domain = domain.substring(0, domain.indexOf("/"));
            }
            if (domain.contains(":")) {
                domain = domain.substring(0, domain.indexOf(":"));
            }
            return domain;
        }
        catch (Exception e) {
            logger.warn("回退域名提取也失败，URL: {}", url);
            return url.toLowerCase();
        }
    }

    private boolean matchesDomain(String domain, Set<String> domainSet) {
        for (String filter : domainSet) {
            if (domain.equals(filter) || domain.endsWith("." + filter)) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder(SearchProvider searchProvider) {
        return new Builder(searchProvider);
    }

    public static class Builder {

        private final SearchProvider searchProvider;

        private int resultCount = 10;

        private Builder(SearchProvider searchProvider) {
            Assert.notNull(searchProvider, "SearchProvider不能为null");
            this.searchProvider = searchProvider;
        }

        public Builder resultCount(int resultCount) {
            if (resultCount <= 0) {
                throw new IllegalArgumentException("resultCount必须为正数");
            }
            this.resultCount = resultCount;
            return this;
        }

        public WebSearchTool build() {
            return new WebSearchTool(this);
        }

    }

}
