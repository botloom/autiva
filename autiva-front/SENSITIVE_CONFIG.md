# 敏感信息配置说明

## 配置文件位置

敏感信息配置文件位于：`${user.home}/.autiva/settings.properties`

- Windows: `C:\Users\你的用户名\.autiva\settings.properties`
- macOS/Linux: `/home/你的用户名\.autiva\settings.properties`

## 配置步骤

### 1. 创建配置目录

```bash
# Windows (PowerShell)
mkdir $env:USERPROFILE\.autiva

# macOS/Linux
mkdir -p ~/.autiva
```

### 2. 创建配置文件

参考项目根目录下的 `settings.properties.example` 文件，创建 `settings.properties` 文件：

```properties
# Application Settings
app.session.isolation=PER_PEER

# DingTalk Configuration
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret
dingtalk.app.agent-id=your-agent-id

# DeepSeek Configuration
spring.ai.deepseek.chat.base-url=https://api.deepseek.com
spring.ai.deepseek.chat.api-key=your-deepseek-api-key
spring.ai.deepseek.chat.options.model=deepseek-chat

# ZhiPu AI Configuration
spring.ai.zhipuai.chat.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.zhipuai.api-key=your-zhipuai-api-key
spring.ai.zhipuai.chat.options.model=glm-4-flash

# WeChat iLink Configuration
weixin.ilink.enabled=false

# Search Configuration
app.search.bocha-api-key=your-bocha-api-key
```

### 3. 填写实际配置

将配置文件中的占位符替换为实际的密钥：

- `your-client-id`: 钉钉应用的 Client ID
- `your-client-secret`: 钉钉应用的 Client Secret
- `your-agent-id`: 钉钉应用的 Agent ID（工作通知必需）
- `your-deepseek-api-key`: DeepSeek 的 API Key
- `your-zhipuai-api-key`: 智谱 AI 的 API Key
- `your-bocha-api-key`: 博查搜索的 API Key

## 安全说明

1. **不要提交敏感信息到代码库**
   - `settings.properties` 已添加到 `.gitignore`
   - 只有 `settings.properties.example` 会被提交

2. **配置文件权限**
   - 确保配置文件只有当前用户可读
   - Windows: 右键 → 属性 → 安全
   - macOS/Linux: `chmod 600 ~/.autiva/settings.properties`

3. **配置文件位置**
   - 配置文件位于用户目录下，不会被项目代码污染
   - 多个项目可以共享同一个配置文件

## 钉钉配置获取

1. 登录钉钉开放平台：https://open.dingtalk.com/
2. 创建企业内部应用
3. 获取 Client ID 和 Client Secret
4. 获取 Agent ID（应用详情页）
5. 开通 Stream 模式权限

## 微信 iLink 配置

微信 iLink 接入基于微信 iLink 协议自行实现，无需额外 API Key。

1. 在配置文件中设置 `weixin.ilink.enabled=true`
2. 启动应用后，在设置页点击"扫码登录"
3. 使用微信扫描二维码登录
4. 登录成功后即可在微信中与智能体对话

**注意事项：**
- 只能向曾经给 bot 发过消息的用户发送消息
- 登录状态失效后需重新扫码登录

## API Key 获取

- DeepSeek: https://platform.deepseek.com/
- 智谱 AI: https://open.bigmodel.cn/
- 博查搜索: https://bochaai.com/
