# Config 包

## 概述
本包实现了应用配置管理，支持从配置文件读取和保存配置。

## 核心类

### ConfigManager
配置管理器，使用 Spring Boot 的 @Value 注解注入配置。

**配置项：**
- `browserPath`: 浏览器可执行文件路径
- `savePath`: 保存目录路径
- `isolation`: 会话隔离模式
- `dingTalkClientId`: 钉钉应用 Client ID
- `dingTalkClientSecret`: 钉钉应用 Client Secret
- `deepseekApiKey`: DeepSeek API Key
- `zApiKey`: 智谱 AI API Key
- `mainAgentTools`: 主智能体工具列表（逗号分隔）
- `agentToolsMap`: 每个智能体的工具配置映射

**核心方法：**
- `save()`: 保存配置到文件
- `getBrowserPath()`: 获取浏览器路径
- `getSavePath()`: 获取保存路径
- `getMainAgentToolList()`: 获取主智能体工具列表
- `getAgentToolList(agentName)`: 获取指定智能体的工具列表
- `setAgentTools(agentName, tools)`: 设置指定智能体的工具列表

## 配置文件

### application.yml
```yaml
app:
  browser-path: C:\Program Files\Google\Chrome\Application\chrome.exe
  save-path: ${user.home}/.autiva/output
  session:
    isolation: PER_PEER
  agent:
    main:
      tools: read,write,edit,exec,web_search,web_fetch,cron_create,cron_list,cron_delete,cron_trigger

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

# Agent Configuration
app.agent.main.tools=read,write,edit,exec,web_search,web_fetch,cron_create,cron_list,cron_delete,cron_trigger
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
configManager.setMainAgentTools("read,write,edit,exec");
configManager.save();
```

### 获取智能体工具列表
```java
List<String> tools = configManager.getMainAgentToolList();
// 返回: ["read", "write", "edit", "exec", ...]
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
