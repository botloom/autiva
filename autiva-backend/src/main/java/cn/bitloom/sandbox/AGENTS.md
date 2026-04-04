# Sandbox 包

## 概述
本包实现了沙箱服务管理，集成 OpenSandbox 官方 Java SDK 实现 FaaS，配合 BaaS 服务提供完整的后端能力。

## 核心类

### SandboxService
沙箱服务管理器，负责创建、停止和管理沙箱实例。

**实现方式：**
- 使用 OpenSandbox 官方 Java SDK (`com.alibaba.opensandbox.sandbox.Sandbox`)
- 通过 `Sandbox.builder()` 创建沙箱实例
- 使用 `sandbox.commands().run()` 执行命令
- 使用 `sandbox.kill()` 销毁沙箱

**核心方法：**
- `createSandbox(clientId, projectName, code, runtime)`: 创建沙箱并部署代码
- `stopSandbox(clientId, projectName)`: 停止沙箱
- `getStatus(clientId)`: 获取用户所有沙箱状态
- `getLogs(clientId, projectName)`: 获取沙箱日志
- `getSandboxBySubdomain(subdomain)`: 通过子域名查找沙箱
- `getServiceWithResources(subdomain)`: 获取沙箱及其 BaaS 资源

**沙箱缓存：**
- 使用 `ConcurrentHashMap<String, Sandbox>` 缓存沙箱实例
- 应用关闭时通过 `@PreDestroy` 自动清理所有沙箱

### SandboxInfo
沙箱信息记录类。

### SandboxResult
沙箱操作结果记录类。

### SubdomainRouter
子域名路由解析器，根据请求的 Host 头解析子域名并路由到对应的沙箱。

**核心方法：**
- `resolve(host)`: 解析主机名，返回路由目标

### RouteTarget
路由目标记录类，包含目标 URL 和是否为用户沙箱的标识。

## 运行时支持

| Runtime | Image | Entry File | Start Command |
|---------|-------|------------|---------------|
| node | opensandbox/code-interpreter:v1.0.2 | /app/index.js | node /app/index.js |
| python | opensandbox/code-interpreter:v1.0.2 | /app/main.py | python /app/main.py |
| java | opensandbox/code-interpreter:v1.0.2 | /app/Main.java | java /app/Main.java |

## 部署流程

1. 检查子域名是否已存在
2. 使用官方 SDK 创建沙箱容器 (`Sandbox.builder().image(image).build()`)
3. 通过 `sandbox.commands().run()` 写入用户代码并启动应用
4. 创建 BaaS 资源 (MySQL, Redis, MongoDB, MinIO)
5. 持久化服务信息到数据库
6. 返回公网访问 URL

## 官方 SDK 使用示例

```java
// 创建沙箱
Sandbox sandbox = Sandbox.builder()
        .image("ubuntu:20.04")
        .build();

// 执行命令
Execution execution = sandbox.commands().run("echo 'Hello Sandbox!'");

// 获取输出
System.out.println(execution.getLogs().getStdout().get(0).getText());

// 销毁沙箱
sandbox.kill();
```

## 配置

OpenSandbox SDK 会自动读取配置文件 `~/.sandbox.toml`，或通过环境变量 `SANDBOX_CONFIG_PATH` 指定配置文件路径。

配置文件示例：
```toml
[server]
host = "127.0.0.1"
port = 8080
log_level = "INFO"

[runtime]
type = "docker"
execd_image = "sandbox-registry.cn-zhangjiakou.cr.aliyuncs.com/opensandbox/execd:v1.0.7"

[ingress]
mode = "direct"
```

## 注意事项
1. 需要先启动 OpenSandbox 服务端
2. 需要配置 MySQL 数据库
3. 子域名全局唯一
4. 支持 Docker 和 Kubernetes 运行时
5. 使用官方 SDK 后，不再需要手动管理 HTTP API 调用
