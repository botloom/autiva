package cn.bitloom.agentic.tool.web;

import cn.bitloom.agentic.tool.AbstractTool;
import cn.bitloom.agentic.tool.ToolResult;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页获取工具，从URL检索内容并转换为Markdown格式返回。
 *
 * <p>
 * 功能：
 * <ul>
 * <li>获取HTML内容并将其转换为Markdown</li>
 * <li>包含15分钟缓存以加快重复访问速度</li>
 * <li>通过robots.txt解析进行可选的域名安全检查</li>
 * <li>自动内容截断，具有可配置的限制</li>
 * </ul>
 *
 * <p>
 * 此类实现{@link AutoCloseable}以确保正确清理HTTP客户端资源。
 * 建议使用try-with-resources或在使用完此工具后显式调用{@link #close()}。
 */
@Slf4j
public class WebFetchTool extends AbstractTool<WebFetchTool.Input> implements AutoCloseable {

    private static final String DESCRIPTION = """
            获取 URL 内容并转为 Markdown。只读。HTTP 自动升级为 HTTPS。15 分钟缓存。5xx 错误自动重试(指数退避)。
            """;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private static final String ROBOTS_TXT_USER_AGENT = "AutivaBot";

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    private final int maxContentLength;

    private final boolean domainSafetyCheck;

    private final RobotsTxtDomainCanFetchChecker domainCanFetchChecker;

    private final FlexmarkHtmlConverter htmlToMarkdownConverter;

    private final Map<String, CacheEntry> urlCache;

    private final int maxCacheSize;

    private final Object cacheLock = new Object();

    private final boolean failOpenOnSafetyCheckError;

    private final int maxRetries;

    public record Input(
            @ToolParam(description = "要获取内容的URL") String url
    ) {}

    private WebFetchTool(Builder builder) {
        super("WebFetch", DESCRIPTION, Input.class);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();

        this.maxContentLength = builder.maxContentLength;
        this.domainSafetyCheck = builder.domainSafetyCheck;
        this.maxCacheSize = builder.maxCacheSize;
        this.failOpenOnSafetyCheckError = builder.failOpenOnSafetyCheckError;
        this.maxRetries = builder.maxRetries;
        this.htmlToMarkdownConverter = FlexmarkHtmlConverter.builder().build();
        this.domainCanFetchChecker = new RobotsTxtDomainCanFetchChecker(this.httpClient);
        this.urlCache = new ConcurrentHashMap<>();
    }

    @Override
    public ToolResult execute(Input input, ToolContext context) {
        String url = input.url();

        if (!StringUtils.hasText(url)) {
            return ToolResult.error("URL不能为空或null", "错误：URL不能为空或null");
        }

        url = url.trim();

        URI uri;
        try {
            uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return ToolResult.error("URL格式无效", "错误：URL格式无效。请提供完整的URL（例如，https://example.com）");
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error("URL格式无效：" + e.getMessage(), "错误：URL格式无效：" + e.getMessage());
        }

        if (this.domainSafetyCheck) {
            DomainCanFetch check = this.domainCanFetchChecker.check(url, this.failOpenOnSafetyCheckError);
            if (!check.canFetch()) {
                return ToolResult.error("域名安全检查失败：" + check.reason(), "URL '" + url + "' 的域名安全检查失败：" + check.reason());
            }
        }

        String cacheKey = url;
        String content = this.getCachedContent(cacheKey);

        if (content != null) {
            return ToolResult.builder()
                    .status(ToolResult.Status.SUCCESS)
                    .message("已获取 " + url)
                    .data(Map.of("url", url, "content_length", content.length()))
                    .rawOutput(content)
                    .build();
        }

        String htmlContent;
        try {
            HttpResponse<String> response = this.fetchHtmlWithRetry(url);
            if (response.statusCode() >= 400) {
                return ToolResult.error("获取URL失败。HTTP状态码：" + response.statusCode(), "错误：获取URL失败。HTTP状态码：" + response.statusCode());
            }
            htmlContent = response.body();
            if (htmlContent == null || htmlContent.isBlank()) {
                return ToolResult.error("从URL检索到的内容为空", "错误：从URL检索到的内容为空");
            }
        } catch (WebFetchException e) {
            log.error("获取URL失败：{}", url, e);
            return ToolResult.error("获取URL时出错：" + e.getMessage(), "获取URL时出错：" + e.getMessage());
        }

        String mdContent = this.htmlToMarkdownConverter.convert(htmlContent);

        mdContent = this.truncate(mdContent);

        this.cacheContent(cacheKey, mdContent);

        return ToolResult.builder()
                .status(ToolResult.Status.SUCCESS)
                .message("已获取 " + url)
                .data(Map.of("url", url, "content_length", mdContent.length()))
                .rawOutput(mdContent)
                .build();
    }

    private HttpResponse<String> fetchHtmlWithRetry(String url) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= this.maxRetries) {
            try {
                if (attempt > 0) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
                    Thread.sleep(backoffMs);
                }

                HttpResponse<String> response = this.fetchHtml(url);

                if (response.statusCode() >= 500 && response.statusCode() < 600) {
                    lastException = new WebFetchException(
                            "服务器错误：HTTP " + response.statusCode(), null);
                    log.warn("获取尝试 {} 返回服务器错误 {}，URL：{}",
                            attempt + 1, response.statusCode(), url);
                    attempt++;
                    continue;
                }

                return response;
            } catch (WebFetchException e) {
                lastException = e;
                if (e.getCause() instanceof InterruptedException) {
                    throw e;
                }
                log.warn("获取尝试 {} 失败，URL：{}：{}", attempt + 1, url, e.getMessage());
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WebFetchException("重试被中断", e);
            }
        }

        if (lastException == null) {
            throw new WebFetchException("在 " + (this.maxRetries + 1) + " 次尝试后失败", null);
        } else {
            throw new WebFetchException("在 " + (this.maxRetries + 1) + " 次尝试后失败", lastException);
        }
    }

    private HttpResponse<String> fetchHtml(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.5")
                .GET()
                .build();

        try {
            HttpResponse<byte[]> byteResponse = this.httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            Charset charset = this.extractCharset(byteResponse).orElse(StandardCharsets.UTF_8);

            String body = new String(byteResponse.body(), charset);

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
        } catch (IOException e) {
            throw new WebFetchException("获取URL时发生网络错误：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("请求被中断", e);
        }
    }

    private Optional<Charset> extractCharset(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .flatMap(contentType -> {
                    Matcher matcher = CHARSET_PATTERN.matcher(contentType);
                    if (matcher.find()) {
                        String charsetName = matcher.group(1);
                        try {
                            return Optional.of(Charset.forName(charsetName));
                        } catch (Exception e) {
                            log.warn("不支持的字符集'{}'，回退到UTF-8", charsetName);
                            return Optional.empty();
                        }
                    }
                    return Optional.empty();
                });
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() > this.maxContentLength) {
            log.warn("内容过长（{} 个字符）。截断为 {} 个字符。", content.length(),
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
        if (entry != null) {
            this.urlCache.remove(url);
        }
        return null;
    }

    private void cacheContent(String cacheKey, String content) {
        if (this.urlCache.size() > this.maxCacheSize) {
            synchronized (this.cacheLock) {
                if (this.urlCache.size() > this.maxCacheSize) {
                    this.cleanExpiredEntries();
                }
            }
        }
        this.urlCache.put(cacheKey, new CacheEntry(content, System.currentTimeMillis()));
    }

    private void cleanExpiredEntries() {
        this.urlCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("已清理过期缓存条目。当前缓存大小：{}", this.urlCache.size());
    }

    @Override
    public void close() {
        this.urlCache.clear();
        log.debug("WebFetchTool已关闭，资源已清理");
    }

    private record CacheEntry(String content, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL.toMillis();
        }
    }

    public record DomainCanFetch(String domain, boolean canFetch, String reason) {
    }

    public static class WebFetchException extends RuntimeException {

        public WebFetchException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    private static class RobotsTxtDomainCanFetchChecker {

        private static final Duration ROBOTS_TXT_CACHE_TTL = Duration.ofMinutes(30);

        private final HttpClient httpClient;

        private final Map<String, RobotsTxtCacheEntry> robotsTxtCache;

        public RobotsTxtDomainCanFetchChecker(HttpClient httpClient) {
            this.httpClient = httpClient;
            this.robotsTxtCache = new ConcurrentHashMap<>();
        }

        public DomainCanFetch check(String url, boolean failOpenOnError) {
            String domain;
            String path;
            try {
                URI uri = URI.create(url);
                domain = uri.getHost();
                path = uri.getPath();
                if (path == null || path.isEmpty()) {
                    path = "/";
                }
                if (domain == null) {
                    return new DomainCanFetch(url, false, "无法从URL提取域名");
                }
            } catch (IllegalArgumentException e) {
                return new DomainCanFetch(url, false, "URL格式无效：" + e.getMessage());
            }

            try {
                String robotsTxt = this.getRobotsTxt(domain);
                if (robotsTxt == null) {
                    return new DomainCanFetch(domain, true, "该域名未提供robots.txt，默认允许获取。");
                }

                if (this.isPathAllowed(robotsTxt, path)) {
                    return new DomainCanFetch(domain, true, "域名安全，robots.txt允许获取。");
                } else {
                    return new DomainCanFetch(domain, false, "该域名的robots.txt禁止获取路径：" + path);
                }
            } catch (Exception e) {
                log.warn("检查域名安全失败 {}：{}", domain, e.getMessage());
                if (failOpenOnError) {
                    return new DomainCanFetch(domain, true, "安全检查不可用，继续获取。");
                } else {
                    return new DomainCanFetch(domain, false,
                            "安全检查失败：" + e.getMessage() + "。出于安全考虑阻止获取。");
                }
            }
        }

        private String getRobotsTxt(String domain) {
            RobotsTxtCacheEntry cached = this.robotsTxtCache.get(domain);
            if (cached != null && !cached.isExpired()) {
                return cached.content();
            }

            String robotsTxt = this.fetchRobotsTxt(domain);
            if (robotsTxt != null) {
                this.robotsTxtCache.put(domain, new RobotsTxtCacheEntry(robotsTxt, System.currentTimeMillis()));
            }
            return robotsTxt;
        }

        private String fetchRobotsTxt(String domain) {
            String robotsUrl = "https://" + domain + "/robots.txt";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", ROBOTS_TXT_USER_AGENT)
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = this.httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body != null && !body.isBlank()) {
                        return body;
                    }
                }
                log.debug("获取robots.txt失败，状态码：{}，域名：{}", response.statusCode(), domain);
                return null;
            } catch (Exception e) {
                log.debug("获取robots.txt异常，域名：{}：{}", domain, e.getMessage());
                return null;
            }
        }

        boolean isPathAllowed(String robotsTxt, String path) {
            List<Rule> rules = this.parseRobotsTxt(robotsTxt);
            if (rules.isEmpty()) {
                return true;
            }

            Rule bestMatch = null;
            int bestMatchLength = -1;

            for (Rule rule : rules) {
                if (this.matchesPath(rule.pattern(), path)) {
                    if (rule.pattern().length() > bestMatchLength) {
                        bestMatchLength = rule.pattern().length();
                        bestMatch = rule;
                    }
                }
            }

            if (bestMatch == null) {
                return true;
            }

            return bestMatch.allowed();
        }

        private List<Rule> parseRobotsTxt(String robotsTxt) {
            List<Rule> rules = new ArrayList<>();
            String[] lines = robotsTxt.split("\n");

            boolean relevantAgent = false;

            for (String line : lines) {
                line = line.trim();
                int commentIdx = line.indexOf('#');
                if (commentIdx >= 0) {
                    line = line.substring(0, commentIdx).trim();
                }
                if (line.isEmpty()) {
                    continue;
                }

                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("user-agent:")) {
                    String agent = line.substring("user-agent:".length()).trim();
                    relevantAgent = "*".equals(agent) || ROBOTS_TXT_USER_AGENT.equalsIgnoreCase(agent);
                } else if (relevantAgent && lowerLine.startsWith("disallow:")) {
                    String pattern = line.substring("disallow:".length()).trim();
                    if (!pattern.isEmpty()) {
                        rules.add(new Rule(pattern, false));
                    }
                } else if (relevantAgent && lowerLine.startsWith("allow:")) {
                    String pattern = line.substring("allow:".length()).trim();
                    if (!pattern.isEmpty()) {
                        rules.add(new Rule(pattern, true));
                    }
                }
            }

            return rules;
        }

        private boolean matchesPath(String pattern, String path) {
            String normalizedPattern = pattern;
            if (normalizedPattern.endsWith("$")) {
                normalizedPattern = normalizedPattern.substring(0, normalizedPattern.length() - 1);
                return path.equals(normalizedPattern);
            }
            if (normalizedPattern.endsWith("*")) {
                normalizedPattern = normalizedPattern.substring(0, normalizedPattern.length() - 1);
                return path.startsWith(normalizedPattern);
            }
            return path.startsWith(normalizedPattern);
        }

        private record Rule(String pattern, boolean allowed) {
        }

        private record RobotsTxtCacheEntry(String content, long timestamp) {
            boolean isExpired() {
                return System.currentTimeMillis() - timestamp > ROBOTS_TXT_CACHE_TTL.toMillis();
            }
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int maxContentLength = 100_000;

        private boolean domainSafetyCheck = true;

        private int maxCacheSize = 100;

        private boolean failOpenOnSafetyCheckError = true;

        private int maxRetries = 2;

        private Builder() {
        }

        public Builder maxContentLength(int maxContentLength) {
            if (maxContentLength <= 0) {
                throw new IllegalArgumentException("maxContentLength必须为正数");
            }
            this.maxContentLength = maxContentLength;
            return this;
        }

        public Builder domainSafetyCheck(boolean domainSafetyCheck) {
            this.domainSafetyCheck = domainSafetyCheck;
            return this;
        }

        public Builder maxCacheSize(int maxCacheSize) {
            if (maxCacheSize <= 0) {
                throw new IllegalArgumentException("maxCacheSize必须为正数");
            }
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public Builder failOpenOnSafetyCheckError(boolean failOpenOnSafetyCheckError) {
            this.failOpenOnSafetyCheckError = failOpenOnSafetyCheckError;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries必须为非负数");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public WebFetchTool build() {
            return new WebFetchTool(this);
        }

    }

}
