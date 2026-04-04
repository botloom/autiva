# Controller 包

## 概述
本包提供 REST API 端点，供网关和其他服务调用。

## 核心类

### SandboxController
沙箱信息查询控制器。

**端点：**
- `GET /api/sandbox/{subdomain}`: 根据子域名查询沙箱信息

### GatewayController
网关路由控制器，提供子域名解析接口。

**端点：**
- `GET /gateway/resolve?host={host}`: 解析主机名，返回路由目标

## 使用示例

### 查询沙箱信息
```bash
curl http://localhost:9527/api/sandbox/user-123-my-app
```

### 响应
```json
{
    "containerId": "sandbox-user-123-my-app",
    "projectName": "my-app",
    "runtime": "node",
    "subdomain": "user-123-my-app",
    "status": "running"
}
```

## 注意事项
1. 子域名格式: `{clientId}-{projectName}`
2. 如果沙箱不存在，返回 404
