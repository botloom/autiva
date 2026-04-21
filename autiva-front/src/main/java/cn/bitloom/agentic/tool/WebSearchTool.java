/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package cn.bitloom.agentic.tool;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Spring AI的Brave网络搜索工具。
 * <p>
 * 使用Brave Search API提供网络搜索功能。支持域名过滤并返回结构化搜索结果。
 * </p>
 *
 * @author Christian Tzolov
 * @see <a href="https://mikhail.io/2025/10/claude-code-web-tools/">Claude Code Web工具</a>
 * @see <a href="https://brave.com/search/api/">Brave Search API</a>
 */
public class WebSearchTool {

	private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);

	private static final String BRAVE_API_BASE_URL = "https://api.search.brave.com";

	private static final String WEB_SEARCH_PATH = "/res/v1/web/search";

	private final RestClient restClient;

	private final int resultCount;

	/**
	 * 使用指定参数创建新的BraveWebSearchTool。使用{@link #builder(String)}创建自定义配置的实例。
	 * @param apiKey Brave Search API订阅令牌（不能为null或空）
	 * @param resultCount 每次搜索返回的结果数
	 */
	private WebSearchTool(String apiKey, int resultCount) {
		Assert.hasText(apiKey, "API密钥不能为null或空");
		this.restClient = RestClient.builder()
			.baseUrl(BRAVE_API_BASE_URL)
			.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader("Accept-Encoding", "gzip")
			.defaultHeader("X-Subscription-Token", apiKey)
			.build();

		this.resultCount = resultCount;
	}

	/**
	 * 使用Brave Search API执行网络搜索。
	 * <p>
	 * <b>关于域名过滤的说明：</b>Brave Search API不通过查询参数支持原生域名过滤。
	 * {@code allowedDomains}和{@code blockedDomains}参数在获取结果后在客户端应用，
	 * 这意味着被过滤的结果仍然计入API配额。为了更好地使用配额，请考虑在查询中直接使用
	 * 搜索运算符（例如，"Spring AI site:spring.io"或"Java -site:example.com"）。
	 * </p>
	 * @param query 要执行的搜索查询
	 * @param allowedDomains 可选的要包含在结果中的域名列表（客户端过滤，null表示不过滤）
	 * @param blockedDomains 可选的要排除在结果外的域名列表（客户端过滤，null表示不过滤）
	 * @return 包含搜索结果的JSON字符串
	 */
	// @formatter:off
	@Tool(name = "WebSearch", description = """
		- 允许AI搜索网络并使用结果来指导回复
		- 为当前事件和最新数据提供最新信息
		- 返回格式化为搜索结果块的搜索结果信息，包括markdown超链接
		- 使用此工具访问AI知识截止日期之外的信息
		- 搜索在单个API调用中自动执行

		关键要求 - 你必须遵循以下规则：
		- 回答用户问题后，你必须在回复末尾包含"来源："部分
		- 在来源部分，列出搜索结果中所有相关的URL作为markdown超链接：[标题](URL)
		- 这是强制性的 —— 永远不要跳过在回复中包含来源
		- 示例格式：

			[你的回答]

			来源：
			- [来源标题1](https://example.com/1)
			- [来源标题2](https://example.com/2)

		使用说明：
		- 支持域名过滤以包含或阻止特定网站（在获取结果后客户端应用）
		- 为了更好地使用API配额，请考虑在查询中使用搜索运算符（例如，"Spring AI site:spring.io"）
		- 网络搜索仅在美国可用

		重要 - 在搜索查询中使用正确的年份：
		- 搜索最新信息、文档或当前事件时，始终在查询中包含当前年份
		- 例如：搜索最新的React文档时，搜索"React文档2025"而不是更早的年份
		""")
	@SuppressWarnings("unchecked")	
	public String webSearch(
		@ToolParam(description = "要使用的搜索查询") String query,
		@ToolParam(description = "仅包含来自这些域名的搜索结果", required = false) List<String> allowedDomains,
		@ToolParam(description = "永远不包含来自这些域名的搜索结果", required = false) List<String> blockedDomains) {
		// @formatter:on

		if (!StringUtils.hasText(query)) {
			logger.warn("提供了空的搜索查询");
			return JsonParser.toJson(Collections.emptyList());
		}

		try {
			if (!CollectionUtils.isEmpty(allowedDomains) || !CollectionUtils.isEmpty(blockedDomains)) {
				logger.debug("将应用客户端域名过滤。允许的域名: {}, 阻止的域名: {}",
						allowedDomains, blockedDomains);
			}

			Map<String, Object> queryResponse = this.executeSearch(query);

			if (queryResponse == null || queryResponse.isEmpty()) {
				logger.warn("Brave Search API对查询返回空响应: {}", query);
				return JsonParser.toJson(Collections.emptyList());
			}

			List<SearchResult> allResults = new ArrayList<>();

			if (queryResponse.containsKey("web")) {				
				allResults.addAll(this.parseResults((Map<String, Object>) queryResponse.get("web")));
			}

			if (queryResponse.containsKey("videos")) {
				allResults.addAll(this.parseResults((Map<String, Object>) queryResponse.get("videos")));
			}

			List<SearchResult> filteredResults = this.applyDomainFiltering(allResults, allowedDomains,
					blockedDomains);

			if (filteredResults.size() < allResults.size()) {
				int filtered = allResults.size() - filteredResults.size();
				logger.info("搜索'{}'返回{}条结果，{}条被域名规则过滤，剩余{}条",
						query, allResults.size(), filtered, filteredResults.size());
			}
			else {
				logger.debug("搜索'{}'返回{}条结果（未应用过滤）", query, allResults.size());
			}

			return JsonParser.toJson(filteredResults);

		}
		catch (RestClientException e) {
			logger.error("执行Brave Search API请求时出错，查询: {}", query, e);
			return JsonParser.toJson(Collections.emptyList());
		}
	}

	private Map<String, Object> executeSearch(String query) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = this.restClient.get()
				.uri(uriBuilder -> uriBuilder.path(WEB_SEARCH_PATH)
					.queryParam("q", query)
					.queryParam("count", this.resultCount)
					.build())
				.retrieve()
				.onStatus(status -> status.is4xxClientError(), (request, errorResponse) -> {
					logger.error("Brave API客户端错误: {} 查询: {}", errorResponse.getStatusCode(), query);
				})
				.onStatus(status -> status.is5xxServerError(), (request, errorResponse) -> {
					logger.error("Brave API服务器错误: {} 查询: {}", errorResponse.getStatusCode(), query);
				})
				.body(Map.class);
			return response != null ? response : Collections.emptyMap();
		}
		catch (Exception e) {
			logger.error("执行搜索请求失败，查询: {}", query, e);
			return Collections.emptyMap();
		}
	}

	public record SearchResult(String title, String url, String description) {
	}

	private List<SearchResult> parseResults(Map<String, Object> resultSection) {
		if (CollectionUtils.isEmpty(resultSection)) {
			return Collections.emptyList();
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) resultSection.get("results");

		if (CollectionUtils.isEmpty(results)) {
			return Collections.emptyList();
		}

		return results.stream()
			.filter(entry -> entry != null && entry.get("title") != null && entry.get("url") != null)
			.map(entry -> new SearchResult(
				(String) entry.get("title"),
				(String) entry.get("url"),
				entry.get("description") != null ? (String) entry.get("description") : ""))
			.toList();
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

	public static Builder builder(String apiKey) {
		return new Builder(apiKey);
	}

	public static class Builder {

		private final String apiKey;

		private int resultCount = 10;

		private Builder(String apiKey) {
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException("API密钥不能为null或空");
			}
			this.apiKey = apiKey;
		}

		/**
		 * 设置每次查询返回的搜索结果数。
		 * @param resultCount 结果数（必须为正数，Brave API通常最大20）
		 * @return 此Builder实例
		 */
		public Builder resultCount(int resultCount) {
			if (resultCount <= 0) {
				throw new IllegalArgumentException("resultCount必须为正数");
			}
			this.resultCount = resultCount;
			return this;
		}

		public WebSearchTool build() {
			return new WebSearchTool(this.apiKey, this.resultCount);
		}

	}

}
