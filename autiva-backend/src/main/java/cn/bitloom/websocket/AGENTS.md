# WebSocket 包

## 概述
本包实现了 WebSocket 服务端，用于与 autiva-front 客户端建立双向通信通道。

## 核心类

### AutivaWebSocketHandler
WebSocket 处理器，处理客户端连接和消息。

**功能：**
- 处理 WebSocket 握手和连接
- 解析客户端消息并分发
- 向客户端发送响应消息

### ClientConnectionManager
客户端连接管理器，管理所有在线客户端。

**核心方法：**
- `register(clientId, sink)`: 注册客户端连接
- `unregister(clientId)`: 注销客户端连接
- `sendToClient(clientId, message)`: 向指定客户端发送消息
- `broadcast(message)`: 向所有客户端广播消息
- `isOnline(clientId)`: 检查客户端是否在线

### MessageDispatcher
消息分发器，根据消息类型路由到对应的处理器。

**支持的消息类型：**
- `ping`: 心跳检测，返回 `pong`
- `deploy`: 部署服务到沙箱
- `stop`: 停止沙箱服务
- `status`: 查询服务状态
- `logs`: 获取服务日志

### WebSocketConfig
WebSocket 配置类，配置 WebSocket 端点。

**端点：**
- `/ws`: WebSocket 连接入口

## 消息格式

### 请求格式
```json
{
    "type": "deploy",
    "payload": {
        "projectName": "my-app",
        "code": "...",
        "runtime": "node"
    }
}
```

### 响应格式
```json
{
    "type": "deploy_result",
    "success": true,
    "url": "https://user-xxx-my-app.autiva.dev",
    "message": "Deployed successfully"
}
```

## 使用示例

### 客户端连接
```javascript
const ws = new WebSocket('ws://localhost:9527/ws?clientId=user-123');
ws.onopen = () => {
    ws.send(JSON.stringify({
        type: 'deploy',
        payload: {
            projectName: 'my-app',
            code: 'console.log("Hello")',
            runtime: 'node'
        }
    }));
};
```

## 注意事项
1. 客户端 ID 通过 URL 参数 `clientId` 传递
2. 消息必须是 JSON 格式
3. 所有消息必须包含 `type` 字段
