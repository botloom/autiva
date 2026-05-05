# Config 包

## 概述
本包实现了应用配置管理，支持从配置文件读取和保存配置。

## 核心类

### DeepSeekV4CompatConfig
DeepSeek V4 兼容性配置，解决 DeepSeek V4 默认启用思考模式（thinking mode）导致 Spring AI 1.1.4 多轮工具调用 400 错误的问题。

**问题背景：**
- DeepSeek V4（2026年4月发布）将 `deepseek-chat` 路由到 `deepseek-v4-flash`，默认启用思考模式
- 启用思考模式后，模型在工具调用响应中返回 `reasoning_content` 字段
- Spring AI 1.1.4 的 `DeepSeekChatModel.createRequest()` 在重建 assistant 消息时始终传 `null` 给 `reasoningContent` 参数
- 后续请求因缺少 `reasoning_content` 导致 DeepSeek API 返回 400 Bad Request

**问题根因：**
`DeepSeekChatOptions4V4` 上的 `@JsonProperty("thinking")` 注解无效，因为被序列化发送给 DeepSeek API 的类不是 `DeepSeekChatOptions4V4`，而是 Spring AI 内部的 `DeepSeekApi.ChatCompletionRequest` record，后者没有 `thinking` 字段。Spring AI 在 `createRequest()` 中将 options 逐字段拷贝到 `ChatCompletionRequest`，`thinking` 字段在此过程中被丢弃。

**修复方案：**
通过 `Jackson2ObjectMapperBuilderCustomizer` 将自定义 Jackson `BeanSerializerModifier` 注册到 Spring Boot 的全局 `ObjectMapper` 中，在序列化 `ChatCompletionRequest` 时自动注入 `thinking: {"type": "disabled"}` 参数，禁用思考模式。

**核心类：**
- `DeepSeekV4CompatConfig`: Spring `@Configuration` 类，声明 `Jackson2ObjectMapperBuilderCustomizer` Bean
- `ThinkingDisabledBeanSerializerModifier`: 自定义 `BeanSerializerModifier`，通过 `modifySerializer()` 方法精确匹配 `DeepSeekApi$ChatCompletionRequest` 类名
  - 匹配成功后返回 `ThinkingDisabledSerializer` 包装器
  - `ThinkingDisabledSerializer` 使用 `TokenBuffer` 捕获原始序列化输出，然后重放并在末尾注入 `thinking` 字段
  - 仅影响 DeepSeek 的请求，不影响 ZhiPu AI 等其他模型

**注意事项：**
- 此配置为临时兼容方案，待 Spring AI 修复 `reasoning_content` 处理后可移除
- 自定义序列化器仅修改 DeepSeek 的 `ChatCompletionRequest`，不影响其他模型的请求
- 如需启用思考模式，需升级 Spring AI 版本并移除此配置
- `DeepSeekChatOptions4V4.thinking` 字段（`@JsonProperty`）保留用于未来 Spring AI 原生支持的升级，当前通过序列化器方案生效

### ConfigManager
配置管理器，使用 Spring Boot 的 @Value 注解注入配置。

**配置项：**
- `browserPath`: 浏览器可执行文件路径
- `savePath`: 保存目录路径
- `isolation`: 会话隔离模式
- `dingTalkClientId`: 钉钉应用 Client ID
- `dingTalkClientSecret`: 钉钉应用 Client Secret
- `weixinILinkEnabled`: 是否启用微信 iLink 接入
- `deepseekApiKey`: DeepSeek API Key
- `zApiKey`: 智谱 AI API Key

**核心方法：**
- `save()`: 保存配置到文件
- `getBrowserPath()`: 获取浏览器路径
- `getSavePath()`: 获取保存路径

## 配置文件

### application.yml
```yaml
app:
  browser-path: C:\Program Files\Google\Chrome\Application\chrome.exe
  save-path: ${user.home}/.autiva/output
  session:
    isolation: PER_PEER

dingtalk:
  app:
    client-id: your-client-id
    client-secret: your-client-secret

spring:
  ai:
    deepseek:
      api-key: your-deepseek-api-key
    zhipuai:
      api-key: your-zhipuai-api-key
```

### settings.properties
配置保存文件，位于 `${user.home}/.autiva/settings.properties`

```properties
# Autiva Settings
# Application Settings
app.browser-path=C:\Program Files\Google\Chrome\Application\chrome.exe
app.save-path=C:\Users\{user}\.autiva\output
app.session.isolation=PER_PEER

# DingTalk Configuration
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret

# AI API Keys
spring.ai.deepseek.api-key=your-deepseek-api-key
spring.ai.zhipuai.api-key=your-zhipuai-api-key
```

## 使用示例

### 注入配置
```java
@Component
@RequiredArgsConstructor
public class MyComponent {
    private final ConfigManager configManager;
    
    public void useConfig() {
        String browserPath = configManager.getBrowserPath();
        String savePath = configManager.getSavePath();
        String dingTalkClientId = configManager.getDingTalkClientId();
    }
}
```

### 保存配置
```java
configManager.setBrowserPath("new/path/to/browser");
configManager.setSavePath("new/save/path");
configManager.setDingTalkClientId("your-client-id");
configManager.save();
```

## 设计模式
- 配置对象模式：集中管理所有配置
- 属性注入：Spring Boot 自动注入配置

## 注意事项
1. 配置修改后需要调用 save() 持久化
2. 配置文件保存在用户目录下
3. 使用 @Value 注解自动注入配置
4. 配置变更不会自动刷新，需要重启应用
5. 敏感信息（如 API Key）使用 PasswordField 在 UI 中显示
6. 所有配置项都支持 null 值，使用默认值处理
