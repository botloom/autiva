# Controller 包

## 概述
本包提供 REST API 端点，供客户端和网关调用。

## 核心类

### SandboxController
沙箱管理控制器，提供项目部署、停止、状态查询等 API。

**端点：**
| 方法 | 路径 | 说明 | 响应类型 |
|------|------|------|----------|
| POST | `/api/sandbox/deploy` | 部署多文件项目 | `ResponseEntity<DeployResult>` |
| POST | `/api/sandbox/stop?clientId=&projectName=` | 停止沙箱服务 | `ResponseEntity<Map>` |
| GET | `/api/sandbox/status?clientId=` | 查询用户所有服务状态 | `ResponseEntity<List<SandboxInfo>>` |
| GET | `/api/sandbox/logs?clientId=&projectName=` | 获取服务日志 | `ResponseEntity<Map>` |
| GET | `/api/sandbox/{subdomain}` | 根据子域名查询沙箱 | `ResponseEntity<SandboxInfo>` |
| GET | `/api/sandbox/{subdomain}/details` | 获取沙箱及BaaS资源详情 | `ResponseEntity<Map>` |

**设计特点：**
- 使用 `ResponseEntity` 提供精确的 HTTP 状态码
- 部署成功返回 200，失败返回 400
- 查询不存在返回 404
- 停止失败返回 500

### GatewayController
网关路由控制器，提供子域名解析接口。

**端点：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/gateway/resolve?host={host}` | 解析主机名，返回路由目标 |

### HealthController
健康检查控制器（新增）。

**端点：**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 服务健康检查 |

## 部署请求格式

```json
POST /api/sandbox/deploy
{
    "clientId": "user-123",
    "projectName": "my-app",
    "runtime": "node",
    "files": [
        {"path": "index.js", "content": "console.log('hello')"},
        {"path": "package.json", "content": "{...}"}
    ],
    "envVars": {
        "PORT": "3000"
    }
}
```

## 部署响应格式

```json
{
    "success": true,
    "url": "https://user-123-my-app.autiva.dev",
    "message": "Deployed successfully",
    "sandboxId": "sandbox-xxx",
    "subdomain": "user-123-my-app"
}
```
