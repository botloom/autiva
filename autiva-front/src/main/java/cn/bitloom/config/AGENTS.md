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

### SchedulingConfig
任务调度配置，提供 `TaskScheduler` Bean。

**问题背景：**
应用使用 `spring.main.web-application-type=none`，Spring Boot 的 `TaskSchedulingAutoConfiguration` 不会自动创建 `TaskScheduler` Bean，导致 `CronManager` 和 `HeartbeatRunner` 无法注入依赖。

**核心方法：**
- `taskScheduler()`: 创建 `ThreadPoolTaskScheduler` Bean，线程池大小4，守护线程

### ConfigManager
配置管理器，使用 Spring Boot 的 @Value 注解注入配置。

**配置项：**
- `isolation`: 会话隔离模式
- `dingTalkClientId`: 钉钉应用 Client ID
- `dingTalkClientSecret`: 钉钉应用 Client Secret
- `deepseekBaseUrl`: DeepSeek API 基础地址（默认空，未配置时为空字符串）
- `deepseekCompletionsPath`: DeepSeek API 补全路径（默认 `/v1/chat/completions`）
- `deepseekApiKey`: DeepSeek API Key（默认空，未配置时为空字符串）
- `deepseekChatModel`: DeepSeek 聊天模型名称（默认 `deepseek-chat`）
- `zhipuaiBaseUrl`: 智谱 AI API 基础地址（默认空，未配置时为空字符串）
- `zhipuaiCompletionsPath`: 智谱 AI API 补全路径（默认 `/chat/completions`）
- `zhipuaiApiKey`: 智谱 AI API Key（默认空，未配置时为空字符串）
- `zhipuaiChatModel`: 智谱 AI 聊天模型名称（默认 `glm-4-flash`）
- `weixinILinkEnabled`: 是否启用微信 iLink 接入
- `bochaApiKey`: 博查搜索 API Key

**核心方法：**
- `save()`: 保存配置到文件
- `isDeepseekConfigured()`: 判断 DeepSeek 是否已配置（apiKey 和 baseUrl 非空）
- `isZhipuaiConfigured()`: 判断智谱 AI 是否已配置（apiKey 和 baseUrl 非空）

## 配置文件

### application.yml
```yaml
app:
  session:
    isolation: PER_PEER

dingtalk:
  app:
    client-id: your-client-id
    client-secret: your-client-secret

spring:
  ai:
    deepseek:
      chat:
        base-url: https://api.deepseek.com
        completions-path: /v1/chat/completions
        api-key: your-deepseek-api-key
        options:
          model: deepseek-chat
    zhipuai:
      chat:
        base-url: https://open.bigmodel.cn/api/paas/v4
        completions-path: /chat/completions
        options:
          model: glm-4-flash
      api-key: your-zhipuai-api-key
```

### settings.properties
配置保存文件，位于 `${user.home}/.autiva/settings.properties`

```properties
# Autiva Settings
# Application Settings
app.session.isolation=PER_PEER

# DingTalk Configuration
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret

# DeepSeek Configuration
spring.ai.deepseek.chat.base-url=https://api.deepseek.com
spring.ai.deepseek.chat.completions-path=/v1/chat/completions
spring.ai.deepseek.chat.api-key=your-deepseek-api-key
spring.ai.deepseek.chat.options.model=deepseek-chat

# ZhiPu AI Configuration
spring.ai.zhipuai.chat.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.zhipuai.chat.completions-path=/chat/completions
spring.ai.zhipuai.api-key=your-zhipuai-api-key
spring.ai.zhipuai.chat.options.model=glm-4-flash
```

## 使用示例

### 注入配置
```java
@Component
@RequiredArgsConstructor
public class MyComponent {
    private final ConfigManager configManager;
    
    public void useConfig() {
        String dingTalkClientId = configManager.getDingTalkClientId();
    }
}
```

### 保存配置
```java
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
4. 配置变更后通过 HotReloadPublisher.publishConfigChanged() 触发热更新，无需重启应用
5. 敏感信息（如 API Key）使用 PasswordField 在 UI 中显示
6. 所有配置项都支持 null 值，使用默认值处理
7. isDeepseekConfigured()/isZhipuaiConfigured() 可判断 API 是否已正确配置
