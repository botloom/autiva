# WebSocket 包

## 概述
本包实现了 WebSocket 服务端，用于与 autiva-front 客户端建立双向通信通道。

## 核心类

### AutivaWebSocketHandler
WebSocket 处理器，处理客户端连接和消息。

### ClientConnectionManager
客户端连接管理器，管理所有在线客户端。

**核心方法：**
- `register(clientId, sink)`: 注册客户端连接
- `unregister(clientId)`: 注销客户端连接
- `sendToClient(clientId, message)`: 发送消息给指定客户端
- `broadcast(message)`: 广播消息给所有客户端
- `isOnline(clientId)`: 检查客户端是否在线
- `getOnlineCount()`: 获取在线客户端数量

### MessageDispatcher
消息分发器，根据消息类型路由到对应的处理器。

**支持的消息类型：**
| 类型 | 说明 | 响应类型 |
|------|------|----------|
| `ping` | 心跳检测 | `pong` |
| `deploy` | 部署项目到沙箱 | `deploy_result` |
| `stop` | 停止沙箱服务 | `stop_result` |
| `status` | 查询服务状态 | `status_result` |
| `logs` | 获取服务日志 | `logs_result` |

**deploy 消息格式：**

请求：
```json
{
    "type": "deploy",
    "payload": {
        "projectName": "my-app",
        "runtime": "node",
        "files": [
            {"path": "index.js", "content": "..."},
            {"path": "package.json", "content": "..."}
        ],
        "envVars": {"PORT": "3000"}
    }
}
```

响应：
```json
{
    "type": "deploy_result",
    "success": true,
    "url": "https://user-xxx-my-app.autiva.dev",
    "message": "Deployed successfully",
    "sandboxId": "sandbox-xxx",
    "subdomain": "user-xxx-my-app"
}
```

### WebSocketConfig
WebSocket 配置类，配置 WebSocket 端点。

**端点：** `/ws`

## 注意事项
1. 客户端 ID 通过 URL 参数 `clientId` 传递
2. 消息必须是 JSON 格式
3. 所有消息必须包含 `type` 字段
4. 统一使用 `deploy` 消息类型（支持多文件部署）
