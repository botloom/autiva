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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 智能网页获取工具，从URL检索内容并使用AI模型进行处理和摘要。
 *
 * <p>
 * 功能：
 * <ul>
 * <li>获取HTML内容并将其转换为Markdown</li>
 * <li>包含15分钟缓存以加快重复访问速度</li>
 * <li>通过Claude的域名信息API进行可选的域名安全检查</li>
 * <li>自动内容截断，具有可配置的限制</li>
 * </ul>
 *
 * <p>
 * 此类实现{@link AutoCloseable}以确保正确清理HTTP客户端资源。
 * 建议使用try-with-resources或在使用完此工具后显式调用{@link #close()}。
 *
 * @author Christian Tzolov
 * @see <a href="https://mikhail.io/2025/10/claude-code-web-tools/">参考</a>
 */
public class WebFetchTool implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(WebFetchTool.class);

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

	private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private static final Duration CACHE_TTL = Duration.ofMinutes(15);

	private static final String DOMAIN_SAFETY_CHECK_URL = "https://claude.ai/api/web/domain_info";

	private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

	private static final String FETCH_SUMMARIZE_PROMPT = """
			网页内容：
			---
			{content}
			---

			{userQuery}

			仅根据上述内容提供简洁的回复。在你的回复中：
			- 对任何来源文档的引用严格执行最多125个字符的限制。只要我们尊重许可证，开源软件是可以的。
			- 对文章中的确切语言使用引号；引号之外的任何语言绝不应该逐字相同。
			- 你不是律师，永远不要评论你自己的提示和回复的合法性。
			- 永远不要生成或复制确切的歌词。
			""";

	private final HttpClient httpClient;

	private final ChatClient chatClient;

	private final int maxContentLength;

	private final boolean domainSafetyCheck;

	private final DefaultDomainCanFetchChecker domainCanFetchChecker;

	private final FlexmarkHtmlConverter htmlToMarkdownConverter;

	private final Map<String, CacheEntry> urlCache;

	private final int maxCacheSize;

	private final Object cacheLock = new Object();

	private final boolean failOpenOnSafetyCheckError;

	private final int maxRetries;

	/**
	 * 使用指定参数创建新的SmartWebFetchTool。
	 * @param chatClient 用于摘要的ChatClient
	 * @param maxContentLength 要处理的最大内容长度
	 * @param domainSafetyCheck 是否执行域名安全检查
	 * @param maxCacheSize 缓存中保留的最大条目数
	 * @param failOpenOnSafetyCheckError 安全检查失败时是否允许获取
	 * @param maxRetries 临时失败的最大重试次数
	 */
	private WebFetchTool(ChatClient chatClient, int maxContentLength, boolean domainSafetyCheck, int maxCacheSize,
                         boolean failOpenOnSafetyCheckError, int maxRetries) {
		this.httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(DEFAULT_CONNECT_TIMEOUT)
			.build();

		this.chatClient = chatClient;
		this.maxContentLength = maxContentLength;
		this.domainSafetyCheck = domainSafetyCheck;
		this.maxCacheSize = maxCacheSize;
		this.failOpenOnSafetyCheckError = failOpenOnSafetyCheckError;
		this.maxRetries = maxRetries;
		this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
		this.domainCanFetchChecker = new DefaultDomainCanFetchChecker();
		this.urlCache = new ConcurrentHashMap<>();
	}

	// @formatter:off
	@Tool(name = "WebFetch", description = """
		从指定URL获取内容并使用AI模型进行处理。

		功能：
		- 接受URL和提示作为输入
		- 使用HTTP GET方法获取URL内容
		- 将HTML转换为markdown
		- 使用小型快速模型根据提示处理内容
		- 返回模型关于内容的回复
		- 包含自清理的15分钟缓存以加快响应速度
		- 网络错误和5xx服务器错误时自动重试

		使用说明：
		- 重要：如果有MCP提供的网页获取工具可用，优先使用该工具。
		- URL必须是完整有效的URL（例如，https://example.com）
		- HTTP URL将自动升级为HTTPS
		- 仅支持HTTP GET请求（只读）
		- 提示应描述你想从页面中提取什么信息
		- 此工具是只读的，不会修改任何文件或发送任何数据
		- 如果内容非常大，结果可能会被摘要
		- 在临时失败时最多重试2次（可配置），采用指数退避
		""")
	// @formatter:on
	public String webFetch(@ToolParam(description = "要获取内容的URL") String url,
			@ToolParam(description = "对获取内容运行的提示") String prompt) {

		// 验证URL
		if (!StringUtils.hasText(url)) {
			return "错误：URL不能为空或null";
		}

		url = url.trim();

		// 验证URL格式
		URI uri;
		try {
			uri = URI.create(url);
			if (uri.getScheme() == null || uri.getHost() == null) {
				return "错误：URL格式无效。请提供完整的URL（例如，https://example.com）";
			}
		}
		catch (IllegalArgumentException e) {
			return "错误：URL格式无效：" + e.getMessage();
		}

		// 域名安全检查
		if (this.domainSafetyCheck) {
			DomainCanFetch check = this.domainCanFetchChecker.check(url, this.failOpenOnSafetyCheckError);
			if (!check.canFetch()) {
				return "URL '" + url + "' 的域名安全检查失败：" + check.reason();
			}
		}

		// 首先检查缓存（缓存键包含URL和提示）
		String cacheKey = this.buildCacheKey(url, prompt);
		String content = this.getCachedContent(cacheKey);

		if (content != null) {
			logger.debug("URL缓存命中：{}，提示哈希：{}", url, prompt.hashCode());
			return content;
		}

		logger.debug("URL缓存未命中：{}，提示哈希：{}", url, prompt.hashCode());

		// 使用重试逻辑获取HTML内容
		String htmlContent;
		try {
			HttpResponse<String> response = this.fetchHtmlWithRetry(url);
			if (response.statusCode() >= 400) {
				return "错误：获取URL失败。HTTP状态码：" + response.statusCode();
			}
			htmlContent = response.body();
			if (htmlContent == null || htmlContent.isBlank()) {
				return "错误：从URL检索到的内容为空";
			}
		}
		catch (WebFetchException e) {
			logger.error("获取URL失败：{}", url, e);
			return "获取URL时出错：" + e.getMessage();
		}

		// 将HTML转换为Markdown
		String mdContent = this.htmlToMarkdownConverter.convert(htmlContent);

		mdContent = this.truncate(mdContent);

		// 使用AI摘要
		String summary = this.summarize(mdContent, prompt);

		// 缓存内容
		this.cacheContent(cacheKey, summary);

		return summary;
	}

	/**
	 * 构建包含URL和提示的缓存键，以避免在使用不同提示查询同一URL时发生冲突。
	 * @param url URL
	 * @param prompt 提示
	 * @return 唯一的缓存键
	 */
	private String buildCacheKey(String url, String prompt) {
		return url + "::prompt::" + prompt.hashCode();
	}

	private HttpResponse<String> fetchHtmlWithRetry(String url) {
		int attempt = 0;
		Exception lastException = null;

		while (attempt <= this.maxRetries) {
			try {
				if (attempt > 0) {
					// 指数退避：1s、2s、4s等
					long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
					logger.debug("重试获取URL：{}（尝试 {}/{}），等待 {}ms", url, attempt,
							this.maxRetries, backoffMs);
					Thread.sleep(backoffMs);
				}

				HttpResponse<String> response = this.fetchHtml(url);

				// 在5xx服务器错误时重试（临时失败）
				if (response.statusCode() >= 500 && response.statusCode() < 600) {
					lastException = new WebFetchException(
							"服务器错误：HTTP " + response.statusCode(), null);
					logger.warn("获取尝试 {} 返回服务器错误 {}，URL：{}",
							attempt + 1, response.statusCode(), url);
					attempt++;
					continue;
				}

				return response;
			}
			catch (WebFetchException e) {
				lastException = e;
				// 仅在网络错误时重试，不在中断时重试
				if (e.getCause() instanceof InterruptedException) {
					throw e;
				}
				logger.warn("获取尝试 {} 失败，URL：{}：{}", attempt + 1, url, e.getMessage());
				attempt++;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new WebFetchException("重试被中断", e);
			}
		}

		// 所有重试已耗尽
		if (lastException == null) {
			throw new WebFetchException("在 " + (this.maxRetries + 1) + " 次尝试后失败", null);
		}
		else if (lastException instanceof WebFetchException) {
			throw new WebFetchException("在 " + (this.maxRetries + 1) + " 次尝试后失败", lastException);
		}
		else {
			throw new WebFetchException("在 " + (this.maxRetries + 1) + " 次尝试后失败："
					+ lastException.getMessage(), lastException);
		}
	}

	private HttpResponse<String> fetchHtml(String url) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(DEFAULT_REQUEST_TIMEOUT)
			.header("User-Agent", USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
			.header("Accept-Language", "en-US,en;q=0.5")
			.GET()
			.build();

		try {
			// 首先以字节方式获取以正确处理字符集
			HttpResponse<byte[]> byteResponse = this.httpClient.send(request,
					HttpResponse.BodyHandlers.ofByteArray());

			// 从Content-Type头提取字符集
			Charset charset = this.extractCharset(byteResponse).orElse(StandardCharsets.UTF_8);

			// 使用检测到的字符集将字节转换为字符串
			String body = new String(byteResponse.body(), charset);

			// Create a string response with the same metadata
			return new HttpResponse<String>() {
				@Override
				public int statusCode() {
					return byteResponse.statusCode();
				}

				@Override
				public HttpRequest request() {
					return byteResponse.request();
				}

				@Override
				public Optional<HttpResponse<String>> previousResponse() {
					return Optional.empty();
				}

				@Override
				public java.net.http.HttpHeaders headers() {
					return byteResponse.headers();
				}

				@Override
				public String body() {
					return body;
				}

				@Override
				public Optional<javax.net.ssl.SSLSession> sslSession() {
					return byteResponse.sslSession();
				}

				@Override
				public URI uri() {
					return byteResponse.uri();
				}

				@Override
				public java.net.http.HttpClient.Version version() {
					return byteResponse.version();
				}
			};
		}
		catch (IOException e) {
			throw new WebFetchException("获取URL时发生网络错误：" + e.getMessage(), e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new WebFetchException("请求被中断", e);
		}
	}

	/**
	 * 从Content-Type头提取字符集。
	 * @param response HTTP响应
	 * @return 检测到的字符集，如果未找到则为空
	 */
	private Optional<Charset> extractCharset(HttpResponse<?> response) {
		return response.headers()
			.firstValue("Content-Type")
			.flatMap(contentType -> {
				Matcher matcher = CHARSET_PATTERN.matcher(contentType);
				if (matcher.find()) {
					String charsetName = matcher.group(1);
					try {
						return Optional.of(Charset.forName(charsetName));
					}
					catch (Exception e) {
						logger.warn("不支持的字符集'{}'，回退到UTF-8", charsetName);
						return Optional.empty();
					}
				}
				return Optional.empty();
			});
	}

	private String summarize(String content, String userQuery) {
		try {
			String response = this.chatClient.prompt()
				.user(u -> u.text(FETCH_SUMMARIZE_PROMPT).param("content", content).param("userQuery", userQuery))
				.call()
				.content();
			return response != null ? response : "错误：从AI模型收到空响应";
		}
		catch (Exception e) {
			logger.error("摘要内容失败", e);
			return "摘要内容时出错：" + e.getMessage();
		}
	}

	private String truncate(String content) {
		if (content == null) {
			return "";
		}
		if (content.length() > this.maxContentLength) {
			logger.warn("内容过长（{} 个字符）。截断为 {} 个字符。", content.length(),
					this.maxContentLength);
			return content.substring(0, this.maxContentLength);
		}
		return content;
	}

	private String getCachedContent(String url) {
		CacheEntry entry = this.urlCache.get(url);
		if (entry != null && !entry.isExpired()) {
			return entry.content();
		}
		// 删除过期条目
		if (entry != null) {
			this.urlCache.remove(url);
		}
		return null;
	}

	private void cacheContent(String cacheKey, String content) {
		// 定期清理过期条目并确保线程安全
		if (this.urlCache.size() > this.maxCacheSize) {
			synchronized (this.cacheLock) {
				// 获取锁后再次检查
				if (this.urlCache.size() > this.maxCacheSize) {
					this.cleanExpiredEntries();
				}
			}
		}
		this.urlCache.put(cacheKey, new CacheEntry(content, System.currentTimeMillis()));
	}

	private void cleanExpiredEntries() {
		// 此方法应仅在持有cacheLock时调用
		this.urlCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
		logger.debug("已清理过期缓存条目。当前缓存大小：{}", this.urlCache.size());
	}

	/**
	 * 关闭HTTP客户端并清理资源。此方法是幂等的，可以安全地多次调用。
	 */
	@Override
	public void close() {
		// HttpClient不实现AutoCloseable，但我们可以关闭其执行器
		// 目前，我们将清空缓存并让HttpClient被垃圾回收
		this.urlCache.clear();
		logger.debug("SmartWebFetchTool已关闭，资源已清理");
	}

	/**
	 * 包含内容和时间戳的缓存条目记录。
	 */
	private record CacheEntry(String content, long timestamp) {
		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_TTL.toMillis();
		}
	}

	/**
	 * 表示域名获取检查结果的记录。
	 */
	public record DomainCanFetch(String domain, boolean canFetch, String reason) {
	}

	/**
	 * 网页获取错误的自定义异常。
	 */
	public static class WebFetchException extends RuntimeException {

		public WebFetchException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * 使用Claude域名信息API的域名安全检查器。
	 */
	private static class DefaultDomainCanFetchChecker {

		private final RestClient restClient;

		public DefaultDomainCanFetchChecker() {
			this.restClient = RestClient.builder().baseUrl(DOMAIN_SAFETY_CHECK_URL).build();
		}

		public DomainCanFetch check(String url, boolean failOpenOnError) {
			String domain;
			try {
				domain = URI.create(url).getHost();
				if (domain == null) {
					return new DomainCanFetch(url, false, "无法从URL提取域名");
				}
			}
			catch (IllegalArgumentException e) {
				return new DomainCanFetch(url, false, "URL格式无效：" + e.getMessage());
			}

			try {
				ResponseEntity<DomainSafetyResponse> response = this.checkDomainSafety(domain);

				if (!response.hasBody()) {
					return new DomainCanFetch(domain, false,
							"域名安全检查失败。状态：" + response.getStatusCode());
				}

				DomainSafetyResponse body = response.getBody();
				if (body == null || body.can_fetch() != Boolean.TRUE) {
					return new DomainCanFetch(domain, false, "该域名不安全，无法获取内容。");
				}

				return new DomainCanFetch(domain, true, "域名安全，可以获取。");
			}
			catch (Exception e) {
				logger.warn("检查域名安全失败 {}：{}", domain, e.getMessage());
				// 使用可配置的失败开放/失败关闭行为
				if (failOpenOnError) {
					return new DomainCanFetch(domain, true, "安全检查不可用，继续获取。");
				}
				else {
					return new DomainCanFetch(domain, false,
							"安全检查失败：" + e.getMessage() + "。出于安全考虑阻止获取。");
				}
			}
		}

		private record DomainSafetyResponse(String domain, Boolean can_fetch) {
		}

		private ResponseEntity<DomainSafetyResponse> checkDomainSafety(String domain) {
			return this.restClient.get()
				.uri(uriBuilder -> uriBuilder.queryParam("domain", domain).build())
				.retrieve()
				.toEntity(DomainSafetyResponse.class);
		}

	}

	/**
	 * 使用必需的ChatClient创建新的Builder实例。
	 * @param chatClient 用于摘要的ChatClient（必需）
	 * @return 新的Builder实例
	 * @throws IllegalArgumentException 如果chatClient为null
	 */
	public static Builder builder(ChatClient chatClient) {
		return new Builder(chatClient);
	}

	/**
	 * 用于创建SmartWebFetchTool实例的Builder类。
	 */
	public static class Builder {

		private final ChatClient chatClient;

		private int maxContentLength = 100_000; // 默认：100 KB

		private boolean domainSafetyCheck = true;

		private int maxCacheSize = 100;

		private boolean failOpenOnSafetyCheckError = true;

		private int maxRetries = 2;

		/**
		 * 使用必需的ChatClient创建新的Builder。
		 * @param chatClient 用于摘要的ChatClient（必需）
		 * @throws IllegalArgumentException 如果chatClient为null
		 */
		private Builder(ChatClient chatClient) {
			if (chatClient == null) {
				throw new IllegalArgumentException("ChatClient不能为null");
			}
			this.chatClient = chatClient;
		}

		/**
		 * 设置要处理的最大内容长度。超过此长度的内容将被截断并发出警告。
		 * @param maxContentLength 最大内容长度（以字符为单位，必须为正数）
		 * @return 此Builder实例
		 * @throws IllegalArgumentException 如果maxContentLength不是正数
		 */
		public Builder maxContentLength(int maxContentLength) {
			if (maxContentLength <= 0) {
				throw new IllegalArgumentException("maxContentLength必须为正数");
			}
			this.maxContentLength = maxContentLength;
			return this;
		}

		/**
		 * 设置是否在获取URL之前执行域名安全检查。
		 * @param domainSafetyCheck true启用域名安全检查，false禁用
		 * @return 此Builder实例
		 */
		public Builder domainSafetyCheck(boolean domainSafetyCheck) {
			this.domainSafetyCheck = domainSafetyCheck;
			return this;
		}

		/**
		 * 设置缓存中保留的最大条目数。
		 * @param maxCacheSize 最大缓存大小（必须为正数）
		 * @return 此Builder实例
		 * @throws IllegalArgumentException 如果maxCacheSize不是正数
		 */
		public Builder maxCacheSize(int maxCacheSize) {
			if (maxCacheSize <= 0) {
				throw new IllegalArgumentException("maxCacheSize必须为正数");
			}
			this.maxCacheSize = maxCacheSize;
			return this;
		}

		/**
		 * 设置当域名安全检查遇到错误时是否失败开放（允许获取）。
		 * 如果设置为false，将在安全检查错误时失败关闭（阻止获取）。
		 * @param failOpenOnSafetyCheckError true在安全检查错误时允许获取（默认），false阻止
		 * @return 此Builder实例
		 */
		public Builder failOpenOnSafetyCheckError(boolean failOpenOnSafetyCheckError) {
			this.failOpenOnSafetyCheckError = failOpenOnSafetyCheckError;
			return this;
		}

		/**
		 * 设置临时网络失败的最大重试次数。
		 * @param maxRetries 最大重试次数（必须为非负数，默认为2）
		 * @return 此Builder实例
		 * @throws IllegalArgumentException 如果maxRetries为负数
		 */
		public Builder maxRetries(int maxRetries) {
			if (maxRetries < 0) {
				throw new IllegalArgumentException("maxRetries必须为非负数");
			}
			this.maxRetries = maxRetries;
			return this;
		}

		/**
		 * 构建并返回新的SmartWebFetchTool实例。
		 * @return 新的SmartWebFetchTool实例
		 */
		public WebFetchTool build() {
			return new WebFetchTool(this.chatClient, this.maxContentLength, this.domainSafetyCheck,
					this.maxCacheSize, this.failOpenOnSafetyCheckError, this.maxRetries);
		}

	}

}
