# Sandbox 包

## 概述
本包实现了沙箱服务的完整生命周期管理，集成 OpenSandbox 官方 Java SDK 提供 FaaS 能力，配合 BaaS 服务提供完整的后端能力。职责分为两层：SandboxManager（底层沙箱容器管理）和 SandboxService（部署编排）。

## 核心类

### SandboxManager
沙箱容器管理器，封装 OpenSandbox SDK 的底层操作。

**职责：**
- 沙箱容器的创建和销毁
- 命令执行和结果解析
- 沙箱实例缓存管理
- 应用关闭时自动清理

**核心方法：**
- `create(sandboxId, runtime)`: 创建沙箱容器，返回 Sandbox 实例
- `kill(sandboxId)`: 销毁沙箱容器
- `getSandbox(sandboxId)`: 获取缓存的沙箱实例
- `exists(sandboxId)`: 检查沙箱是否存在
- `execute(sandbox, command)`: 在沙箱中执行命令，返回 ExecutionResult
- `generateSandboxId()`: 生成唯一沙箱 ID

**ExecutionResult 记录类：**
- `success`: 命令是否成功
- `stdout`: 标准输出
- `stderr`: 标准错误
- `error`: 异常信息

**运行时镜像映射：**
| Runtime | Image |
|---------|-------|
| node | opensandbox/code-interpreter:v1.0.2 |
| python | opensandbox/code-interpreter:v1.0.2 |
| java | opensandbox/code-interpreter:v1.0.2 |

### SandboxService
部署编排服务，协调沙箱创建、文件写入、依赖安装、BaaS 资源创建和应用启动。

**核心方法：**
- `deployProject(DeployRequest)`: 部署多文件项目
- `stopService(clientId, projectName)`: 停止服务
- `listServices(clientId)`: 列出用户所有服务
- `getLogs(clientId, projectName)`: 获取服务日志
- `restartProject(clientId, projectName)`: 重启项目（停止旧沙箱并创建新沙箱）
- `getServiceBySubdomain(subdomain)`: 通过子域名查询服务
- `getServiceDetails(subdomain)`: 获取服务及 BaaS 资源详情
- `getProjectDetails(clientId, projectName)`: 通过 clientId + projectName 获取服务及 BaaS 资源详情

**部署流程：**
1. 检查子域名是否已存在
2. 自动检测运行时（package.json -> node, requirements.txt -> python, pom.xml -> java）
3. 通过 SandboxManager 创建沙箱容器
4. 写入所有项目文件到 `/app/` 目录（使用 heredoc 避免特殊字符问题）
5. 安装依赖（npm install / pip install / mvn package）
6. 通过 BaasManager 创建 BaaS 资源
7. 将用户环境变量 + BaaS 连接信息合并写入 `/app/.env`
8. 启动应用程序（自动检测入口文件）
9. 持久化服务信息和 BaaS 资源到数据库
10. 返回公网访问 URL

**错误处理：**
- 部署失败时自动回滚（kill 沙箱）
- 依赖安装失败不阻断流程（使用 `|| true`）
- .env 写入失败仅记录警告

### SandboxInfo
沙箱信息记录类。

**字段：** sandboxId, projectName, runtime, subdomain, status

### RestartResult
重启结果记录类。

**字段：** url, sandboxId, subdomain

### SubdomainRouter
子域名路由解析器，直接注入 SandboxService 查询服务信息。

**核心方法：**
- `resolve(host)`: 解析主机名，返回路由目标

### RouteTarget
路由目标记录类。

**字段：** targetUrl, isUserSandbox

## 设计模式
- **单一职责**：SandboxManager 管容器，SandboxService 管编排
- **策略模式**：运行时检测和启动命令选择
- **模板方法**：部署流程步骤固定，细节可扩展

## 注意事项
1. 需要先启动 OpenSandbox 服务端
2. 需要配置 MySQL 数据库
3. 子域名全局唯一
4. 阻塞操作使用 `Schedulers.boundedElastic()` 调度
5. SubdomainRouter 直接注入 SandboxService，不再通过 HTTP 调用自身
