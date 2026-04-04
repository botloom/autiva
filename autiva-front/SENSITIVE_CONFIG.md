# 敏感信息配置说明

## 配置文件位置

敏感信息配置文件位于：`${user.home}/.autiva/settings.properties`

- Windows: `C:\Users\你的用户名\.autiva\settings.properties`
- macOS/Linux: `/home/你的用户名/.autiva/settings.properties`

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
# 钉钉配置
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret

# DeepSeek API Key
spring.ai.deepseek.api-key=your-deepseek-api-key

# 智谱 AI API Key
spring.ai.zhipuai.api-key=your-zhipuai-api-key
```

### 3. 填写实际配置

将配置文件中的占位符替换为实际的密钥：

- `your-client-id`: 钉钉应用的 Client ID
- `your-client-secret`: 钉钉应用的 Client Secret
- `your-deepseek-api-key`: DeepSeek 的 API Key
- `your-zhipuai-api-key`: 智谱 AI 的 API Key

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
4. 开通 Stream 模式权限

## API Key 获取

- DeepSeek: https://platform.deepseek.com/
- 智谱 AI: https://open.bigmodel.cn/
