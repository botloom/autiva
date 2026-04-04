# Bridge 包

## 概述
本包实现了与第三方平台的连接和通信，提供消息推送、用户管理、审批流程等能力。支持钉钉 Stream 模式和微信 ClawBot 插件，可以在钉钉和微信中与智能体进行对话。

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
使用官方 `openclaw-weixin-cli` 桥接器实现微信 ClawBot 插件接入，通过 WebSocket Gateway 与智能体系统通信。

**核心组件：**
- `WeixinProperties`: 微信 Gateway 配置属性类
- `WeixinGatewayServer`: WebSocket 服务器（端口 18789），处理 ACP/JSON-RPC 协议

**条件注册：**
所有微信相关组件都使用 `@ConditionalOnProperty` 注解，只有当配置了 `weixin.gateway.enabled=true` 时才会注册到 Spring 容器中。

**工作流程：**
```
微信用户 → ClawBot 插件 → openclaw-weixin-cli → ws://localhost:18789 → WeixinGatewayServer → EventBus.inBox → MainAgent → EventBus.outBox → WeixinGatewayServer → openclaw-weixin-cli → ClawBot 插件 → 微信用户
```

### WeixinProperties
微信 Gateway 配置属性类，统一管理微信相关配置。

**配置属性：**
- `port`: WebSocket 端口，默认 18789
- `token`: Gateway 认证令牌
- `gatewayId`: Gateway 标识，默认 java-gateway-001
- `version`: Gateway 版本，默认 2026.3.22
- `enabled`: 是否启用微信接入，默认 false

**配置方式：**
```yaml
weixin:
  gateway:
    enabled: true
    port: 18789
    token: your-gateway-token
    gateway-id: java-gateway-001
    version: 2026.3.22
```

**注意：** 只有当 `enabled=true` 时，微信相关的 Bean 才会被注册。

### WeixinGatewayServer
WebSocket 服务器，监听端口 18789，处理 ACP/JSON-RPC 协议。

**核心功能：**
- 启动 WebSocket 服务器（端口 18789）
- 处理客户端连接和认证
- 解析 ACP/JSON-RPC 消息
- 管理会话映射
- 与 EventBus 集成

**协议格式：**

1. **连接认证（connect）**
```json
{
  "jsonrpc": "2.0",
  "method": "connect",
  "params": {
    "token": "your-gateway-token",
    "agentId": "default"
  }
}
```

2. **连接成功响应（gateway.connected）**
```json
{
  "jsonrpc": "2.0",
  "method": "gateway.connected",
  "params": {
    "gatewayId": "java-gateway-001",
    "version": "2026.3.22"
  }
}
```

3. **接收消息（channel.message）**
```json
{
  "jsonrpc": "2.0",
  "method": "channel.message",
  "params": {
    "from": "user-id",
    "text": "用户消息内容",
    "context_token": "窗口绑定标识"
  }
}
```

4. **发送消息（channel.send）**
```json
{
  "jsonrpc": "2.0",
  "method": "channel.send",
  "params": {
    "channel": "openclaw-weixin",
    "to": "user-id",
    "text": "回复消息内容",
    "context_token": "窗口绑定标识"
  }
}
```

**会话管理：**
- 使用 `userId:contextToken` 作为会话标识
- 通过 `SessionManager` 创建系统会话
- 维护会话映射关系
- 支持多窗口会话隔离

**消息处理流程：**
1. 接收 WebSocket 连接
2. 处理 `connect` 认证请求
3. 接收 `channel.message` 消息
4. 创建或获取会话
5. 将消息发布到 EventBus（使用 `inBoxPublishBlocked` 非流式模式）
6. 订阅智能体回复
7. 通过 `channel.send` 发送回复

### 配置说明

#### 1. 安装官方桥接器
```bash
# 安装 openclaw-weixin-cli（需要 Node 环境）
npm install -g @tencent-weixin/openclaw-weixin-cli
```

#### 2. 配置桥接器
```bash
# 配置 Gateway 地址
openclaw config set gateway.url ws://localhost:18789

# 配置 Gateway Token
openclaw config set gateway.token your-gateway-token

# 绑定微信（扫码）
openclaw channels login --channel openclaw-weixin

# 启动桥接
openclaw gateway start
```

#### 3. 启用微信 ClawBot 插件
1. 微信 → 我 → 设置 → 插件 → **ClawBot** → 启用
2. 进入 ClawBot → 绑定设备 → 扫终端二维码
3. 绑定成功后，微信里 **ClawBot** 就是你的智能体

#### 4. 配置敏感信息

**重要：不要将敏感信息提交到代码库！**

在用户目录下创建配置文件：
- Windows: `C:\Users\你的用户名\.autiva\settings.properties`
- macOS/Linux: `/home/你的用户名\.autiva/settings.properties`

配置文件内容：
```properties
# 微信配置
weixin.gateway.enabled=true
weixin.gateway.token=your-gateway-token
```

详细说明请参考项目根目录下的 `SENSITIVE_CONFIG.md` 文件。

### 技术实现

#### 依赖配置
项目使用 Spring Boot WebFlux (Reactor Netty) 实现 WebSocket 服务：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

#### 消息流转
1. **微信 → 系统**：`WeixinWebSocketHandler.handle()` 接收消息 → `EventBus.inBoxPublish()` 发布
2. **系统 → 微信**：`EventBus.outBoxSubscribe()` 订阅回复 → `sendReply()` 发送

#### 会话隔离
- 使用 `SessionTypeEnum.DM` 创建点对点会话
- 每个微信 `userId:contextToken` 对应一个系统 `Session`
- 会话映射存储在 `ConcurrentHashMap` 中，保证线程安全
- 支持多窗口消息隔离（通过 `context_token`）

### 注意事项

1. **条件注册机制**：
   - 微信相关组件使用 `@ConditionalOnProperty` 注解
   - 只有配置了 `weixin.gateway.enabled=true` 才会注册
   - 未配置时不会启动 WebSocket 服务器
   - 避免不必要的资源占用

2. **端口要求**：
   - 默认端口 18789（官方桥接器默认连接端口）
   - 确保端口未被占用
   - 可通过配置修改端口

3. **协议规范**：
   - 严格遵循 ACP/JSON-RPC 2.0 格式
   - 必须透传 `context_token`（微信窗口绑定标识）
   - 首帧必须是 `connect` 认证

4. **消息发送方式**：
   - 使用 `inBoxPublishBlocked` 发送非流式消息
   - 智能体回复通过 `channel.send` 发送
   - 支持文本消息格式

5. **会话管理**：
   - 会话映射使用 `userId:contextToken` 作为 key
   - 避免重复创建会话
   - 支持多用户同时对话
   - 支持同一用户多窗口隔离

6. **错误处理**：
   - 认证失败时关闭连接
   - 订阅错误时记录日志
   - 消息处理异常时发送错误响应

### API 文档参考
- 微信开放平台：https://open.weixin.qq.com/
- ClawBot 插件文档：https://open.weixin.qq.com/doc/clawbot
- OpenClaw Gateway 协议：https://openclaw.dev/docs/gateway-protocol
