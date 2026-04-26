# Bridge 包

## 概述
本包实现了与第三方平台的连接和通信，提供消息推送、用户管理、审批流程等能力。支持钉钉 Stream 模式和微信 iLink 协议接入，可以在钉钉和微信中与智能体进行对话。

## 钉钉接入

### 架构设计
使用钉钉官方 SDK 实现 Stream 模式接入，支持单聊和群聊消息。

**核心组件：**
- `DingTalkProperties`: 钉钉配置属性类
- `BotEchoTextListener`: 钉钉 Stream 客户端启动器
- `BotMessageConsumer`: 消息消费者，处理钉钉消息并与智能体交互

**条件注册：**
所有钉钉相关组件都使用 `@ConditionalOnProperty` 注解，只有当配置了 `dingtalk.app.client-id` 和 `dingtalk.app.client-secret` 时才会注册到 Spring 容器中。

**工作流程：**
```
钉钉消息 → BotMessageConsumer → EventBus.inBox → MainAgent → EventBus.outBox → BotMessageConsumer → BotReplier → 钉钉
```

### DingTalkProperties
钉钉配置属性类，统一管理钉钉相关配置。

**配置属性：**
- `clientId`: 钉钉应用 Client ID
- `clientSecret`: 钉钉应用 Client Secret

**配置方式：**
```yaml
dingtalk:
  app:
    client-id: your-client-id
    client-secret: your-client-secret
```

**注意：** 只有当这两个配置都存在时，钉钉相关的 Bean 才会被注册。

### BotEchoTextListener
钉钉 Stream 客户端启动器，负责建立与钉钉服务器的长连接。

**核心功能：**
- 使用 `OpenDingTalkStreamClientBuilder` 创建客户端
- 使用 `AuthClientCredential` 进行认证
- 注册 `BOT_MESSAGE_TOPIC` 消息监听器
- 启动 Stream 客户端
- 添加日志记录，便于调试

### BotMessageConsumer
消息消费者，实现 `OpenDingTalkCallbackListener<ChatbotMessage, Void>` 接口。

**核心功能：**
- 接收钉钉消息
- 管理钉钉会话与系统会话的映射
- 将消息转发到 EventBus
- 订阅智能体回复并发送回钉钉

**会话管理：**
- 使用 `conversationId` 作为钉钉会话标识
- 通过 `SessionManager` 创建系统会话
- 维护 `conversationId` 到 `Session` 的映射关系

**消息处理流程：**
1. 接收钉钉消息
2. 检查是否已有对应会话
3. 如果没有，创建新会话并订阅回复
4. 将消息发布到 EventBus（使用 `inBoxPublishBlocked` 非流式模式）
5. 智能体处理消息后，通过 `BotReplier` 发送回复

## 配置说明

### 1. 创建钉钉应用
1. 登录钉钉开放平台：https://open.dingtalk.com/
2. 创建企业内部应用
3. 获取 Client ID 和 Client Secret

### 2. 开通 Stream 权限
1. 在应用管理页面，找到"Stream 模式"
2. 开通 Stream 模式权限
3. 配置消息接收权限（单聊、群聊）

### 3. 配置敏感信息

**重要：不要将敏感信息提交到代码库！**

#### 方式一：使用外部配置文件（推荐）

在用户目录下创建配置文件：
- Windows: `C:\Users\你的用户名\.autiva\settings.properties`
- macOS/Linux: `/home/你的用户名/.autiva/settings.properties`

配置文件内容：
```properties
# 钉钉配置
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret

# API Keys
spring.ai.deepseek.api-key=your-deepseek-api-key
spring.ai.zhipuai.api-key=your-zhipuai-api-key
```

详细说明请参考项目根目录下的 `SENSITIVE_CONFIG.md` 文件。

#### 方式二：使用环境变量

```bash
# Windows (PowerShell)
$env:DINGTALK_APP_CLIENT_ID="your-client-id"
$env:DINGTALK_APP_CLIENT_SECRET="your-client-secret"

# macOS/Linux
export DINGTALK_APP_CLIENT_ID="your-client-id"
export DINGTALK_APP_CLIENT_SECRET="your-client-secret"
```

### 4. 测试对话
启动应用后，在钉钉中：
- **单聊**：直接给机器人发送消息
- **群聊**：在群中 @机器人 发送消息

## 技术实现

### 依赖配置
项目已添加钉钉 Stream SDK 依赖：
```xml
<dependency>
    <groupId>com.dingtalk.open</groupId>
    <artifactId>dingtalk-stream</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 消息流转
1. **钉钉 → 系统**：`BotMessageConsumer.execute()` 接收消息 → `EventBus.inBoxPublishBlocked()` 发布
2. **系统 → 钉钉**：`EventBus.outBoxSubscribe()` 订阅回复 → `BotReplier.replyText()` 发送

### 会话隔离
- 使用 `SessionTypeEnum.DM` 创建点对点会话
- 每个钉钉 `conversationId` 对应一个系统 `Session`
- 会话映射存储在 `ConcurrentHashMap` 中，保证线程安全

## 注意事项

1. **条件注册机制**：
   - 钉钉相关组件使用 `@ConditionalOnProperty` 注解
   - 只有配置了 `dingtalk.app.client-id` 和 `dingtalk.app.client-secret` 才会注册
   - 未配置时不会启动钉钉 Stream 客户端
   - 避免不必要的资源占用

2. **Stream 模式优势**：
   - 无需公网 IP 和 webhook
   - 支持单聊和群聊消息接收
   - 自动重连机制

3. **消息发送方式**：
   - 使用 `inBoxPublishBlocked` 发送非流式消息
   - 智能体回复通过 `BotReplier` 发送
   - 支持文本消息格式

4. **会话管理**：
   - 会话映射使用 `conversationId` 作为 key
   - 避免重复创建会话
   - 支持多用户同时对话

5. **错误处理**：
   - 订阅错误时记录日志
   - 消息处理完成后记录日志
   - 异常时抛出 `RuntimeException`

## API 文档参考
- 钉钉开放平台：https://open.dingtalk.com/
- Stream 模式文档：https://open.dingtalk.com/document/orgapp/stream-mode
- 机器人消息：https://open.dingtalk.com/document/orgapp/receive-message

## 微信接入

### 架构设计
基于微信 iLink 协议自行实现接入，通过 `ILinkApiClient` 直接调用 iLink HTTP/JSON API 实现二维码登录、长轮询消息接收和消息发送。

**核心组件：**
- `WeixinILinkProperties`: 微信 iLink 配置属性类
- `WeixinILinkClient`: iLink 客户端管理器，负责登录、消息轮询和生命周期管理
- `WeixinILinkMessageHandler`: 消息处理器，处理消息与 EventBus 的集成
- `ILinkApiClient`: iLink 协议 HTTP 客户端，封装所有 iLink API 调用
- `ilink.model`: iLink 数据模型包（WeixinMessage、MessageItem、TextItem、LoginContext 等）

**条件注册：**
所有微信相关组件都使用 `@ConditionalOnProperty` 注解，只有当配置了 `weixin.ilink.enabled=true` 时才会注册到 Spring 容器中。

**工作流程：**
```
微信用户 → iLink 服务器 → WeixinILinkClient(getUpdates 长轮询) → WeixinILinkMessageHandler → EventBus.inBox → MainAgent → EventBus.outBox → WeixinILinkMessageHandler → WeixinILinkClient(sendText) → iLink 服务器 → 微信用户
```

### WeixinILinkProperties
微信 iLink 配置属性类，统一管理微信 iLink 相关配置。

**配置属性：**
- `enabled`: 是否启用微信 iLink 接入，默认 false
- `connectTimeoutMs`: 连接超时时间（毫秒），默认 35000
- `readTimeoutMs`: 读取超时时间（毫秒），默认 35000
- `writeTimeoutMs`: 写入超时时间（毫秒），默认 35000
- `httpMaxRetries`: HTTP 最大重试次数，默认 3
- `retryBaseDelayMs`: 重试基础退避时间（毫秒），默认 1000
- `retryMaxDelayMs`: 重试最大退避时间（毫秒），默认 10000
- `heartbeatEnabled`: 是否启用心跳，默认 true
- `heartbeatIntervalMs`: 心跳间隔（毫秒），默认 30000
- `channelVersion`: 渠道版本，默认 2.0.0

**配置方式：**
```yaml
weixin:
  ilink:
    enabled: true
    connect-timeout-ms: 35000
    read-timeout-ms: 35000
    write-timeout-ms: 35000
    http-max-retries: 3
    retry-base-delay-ms: 1000
    retry-max-delay-ms: 10000
    heartbeat-enabled: true
    heartbeat-interval-ms: 30000
    channel-version: "2.0.0"
```

**注意：** 只有当 `enabled=true` 时，微信相关的 Bean 才会被注册。

### WeixinILinkClient
iLink 客户端管理器，负责 ILinkApiClient 的完整生命周期管理。

**核心功能：**
- 创建 `ILinkApiClient` 并管理其生命周期
- 执行二维码登录流程（异步）
- 登录成功后启动消息长轮询
- 管理客户端生命周期（启动、关闭）
- 暴露 `connectedProperty()` 供 UI 绑定连接状态
- 暴露 `getQrCodeContent()` 供 UI 渲染二维码

**登录流程：**
1. `@PostConstruct` 初始化 ILinkApiClient 并调用 `startLogin()`
2. `startLogin()` 在独立线程中调用 `apiClient.getQRCode()` 获取二维码
3. 二维码内容存储在 `qrCodeContent` 字段，供 UI 读取
4. 用户在设置页点击"扫码登录"按钮，弹出登录对话框
5. 登录对话框使用 ZXing 将二维码内容渲染为图片
6. `apiClient.pollLoginStatus()` 轮询扫码状态，登录成功后更新 `connected` 属性
7. 自动启动消息轮询循环

**消息轮询：**
- 使用 `apiClient.getUpdates()` 长轮询获取消息（约 35 秒挂起）
- 轮询在独立线程中运行
- 异常时自动退避重试（5 秒间隔）
- 会话过期时自动重新登录
- 连接断开或重连失败时停止轮询

**会话过期处理：**
- 当 `getUpdates()` 返回 `errcode: -14` 时，自动清除状态并重新发起登录流程

### WeixinILinkMessageHandler
消息处理器，处理微信消息与 EventBus 的集成。

**核心功能：**
- 解析微信消息（提取文本内容）
- 管理微信会话与系统会话的映射
- 将消息转发到 EventBus
- 订阅智能体回复并通过 iLink 发送

**会话管理：**
- 使用 `userId`（格式：`xxx@im.wechat`）作为微信用户标识
- 通过 `SessionManager` 创建系统会话
- 维护 `userId` 到 `Session` 的映射关系

**消息处理流程：**
1. 从 `WeixinMessage` 中提取文本内容
2. 检查是否已有对应用户会话
3. 如果没有，创建新会话并订阅回复
4. 将消息发布到 EventBus（使用 `inBoxPublish` 模式）
5. 智能体处理消息后，通过 `ILinkApiClient.sendText()` 发送回复

### iLink 协议说明

微信 iLink 协议是微信 ClawBot 功能背后的 HTTP/JSON 协议，基座地址为 `https://ilinkai.weixin.qq.com`。

**协议阶段：**
1. **登录阶段**：二维码扫码登录
2. **消息阶段**：长轮询接收消息 + 发送消息
3. **媒体阶段**：CDN 上传/下载 + AES 加密

**核心概念：**
- `context_token`：消息上下文标识，每条回复必须回传入站消息中的 token。ILinkApiClient 自动管理，按用户缓存最新 token
- `cursor`：消息分页游标，ILinkApiClient 内部自动管理
- `bot_token`：登录成功后获取的 Bearer token，后续 API 调用需要携带

**用户 ID 格式：**
- 普通用户：`xxx@im.wechat`
- 机器人：`xxx@im.bot`

**核心 API：**

| 接口 | 方法 | 用途 |
|------|------|------|
| /get_bot_qrcode | GET | 获取登录二维码 |
| /get_qrcode_status | GET | 轮询扫码状态 |
| /getupdates | POST | 长轮询消息（35秒挂起） |
| /sendmessage | POST | 发送文本或媒体消息 |
| /getconfig | POST | 获取用户的 typing_ticket |
| /sendtyping | POST | 显示/隐藏输入状态 |
| /getuploadurl | POST | 获取 CDN 上传参数 |

**错误码：**
- `ret: 0`：成功
- `errcode: -14`：会话过期，需清除状态并重新登录
- `ret: -2`：参数错误

### 配置说明

#### 1. 启用微信 iLink 接入

在用户目录下创建配置文件：
- Windows: `C:\Users\你的用户名\.autiva\settings.properties`
- macOS/Linux: `/home/你的用户名/.autiva/settings.properties`

配置文件内容：
```properties
# 微信 iLink 配置
weixin.ilink.enabled=true
```

详细说明请参考项目根目录下的 `SENSITIVE_CONFIG.md` 文件。

#### 2. 扫码登录
启动应用后，控制台会输出二维码内容：
1. 将二维码内容渲染为二维码图片
2. 使用微信扫描二维码
3. 在手机上确认登录
4. 登录成功后自动开始接收消息

#### 3. 测试对话
登录成功后，在微信中：
- 直接给机器人发送消息即可对话

### 技术实现

#### 依赖配置
项目基于 iLink 协议自行实现，使用 Java 内置 `java.net.http.HttpClient` 和 `fastjson2` 进行 HTTP 通信和 JSON 序列化，无需第三方 SDK 依赖。

#### ILinkApiClient
iLink 协议 HTTP 客户端，封装所有 iLink API 调用。

**核心功能：**
- `getQRCode()`: 获取登录二维码
- `pollLoginStatus()`: 轮询扫码状态，等待用户确认登录
- `getUpdates()`: 长轮询获取消息，自动管理 cursor 和 context_token
- `sendText()`: 发送文本消息，自动使用缓存的 context_token
- `clearState()`: 清除所有状态（cursor、context_token、loginContext）

**请求头：**
所有业务 POST 请求需要以下请求头：
- `Content-Type: application/json`
- `AuthorizationType: ilink_bot_token`
- `Authorization: Bearer <bot_token>`
- `X-WECHAT-UIN: base64(String(random_uint32))`（每次请求重新生成）

**请求体：**
所有请求体包含 `base_info: { channel_version: "2.0.0" }`

**异常体系：**
- `SessionExpiredException`: 会话过期（errcode: -14），需重新登录
- `NotLoginException`: 未登录时调用业务 API
- `ILinkException`: 协议错误（ret != 0）

#### 消息流转
1. **微信 → 系统**：`WeixinILinkClient` 长轮询获取消息 → `WeixinILinkMessageHandler.handleMessage()` → `EventBus.inBoxPublish()` 发布
2. **系统 → 微信**：`EventBus.outBoxSubscribe()` 订阅回复 → `ILinkApiClient.sendText()` 发送

#### 会话隔离
- 使用 `SessionTypeEnum.DM` 创建点对点会话
- 每个微信 `userId`（格式：`xxx@im.wechat`）对应一个系统 `Session`
- 会话映射存储在 `ConcurrentHashMap` 中，保证线程安全

### 注意事项

1. **条件注册机制**：
   - 微信相关组件使用 `@ConditionalOnProperty` 注解
   - 只有配置了 `weixin.ilink.enabled=true` 才会注册
   - 未配置时不会启动 iLink 客户端
   - 避免不必要的资源占用

2. **登录机制**：
   - 启动时自动获取二维码并等待扫码
   - 登录状态通过 `connectedProperty()` 暴露给 UI
   - 二维码内容通过 `getQrCodeContent()` 供 UI 渲染
   - 设置页提供"扫码登录"按钮，弹出二维码登录对话框
   - 登录对话框使用 ZXing 生成二维码图片
   - 登录状态失效后可通过"刷新二维码"重新登录

3. **消息轮询**：
   - 使用长轮询模式（约 35 秒挂起）
   - 轮询在独立线程池中运行
   - 异常时自动退避重试（5 秒间隔）
   - 连接断开时停止轮询

4. **消息发送方式**：
   - 使用 `inBoxPublish` 发送消息到 EventBus
   - 智能体回复通过 `ILinkApiClient.sendText()` 发送
   - 当前仅支持文本消息格式
   - 发送消息前，目标用户必须先给 bot 发过消息（context_token 前提）

5. **会话管理**：
   - 会话映射使用 `userId` 作为 key
   - 避免重复创建会话
   - 支持多用户同时对话

6. **错误处理**：
   - 登录失败时记录日志
   - 消息轮询异常时退避重试
   - 回复发送失败时记录日志
   - 连接断开/重连失败时停止轮询

7. **生命周期管理**：
   - `@PostConstruct` 初始化客户端并启动登录
   - `@PreDestroy` 关闭客户端和线程池
   - ILinkApiClient 实现了 `AutoCloseable`

### API 文档参考
- 微信 iLink Bot API：https://www.wechatbot.dev/zh
- iLink 协议文档：https://www.wechatbot.dev/zh/protocol
