# Deploy 包

## 概述
本包实现了项目部署功能，将本地项目文件发送到后端沙箱进行部署，实现 FaaS + BaaS 的完整流程。

## 核心类

### DeployTool
项目部署工具（Builder 模式创建），提供三个工具方法：

**工具方法：**
- `deploy(projectName, runtime)`: 部署项目到云沙箱
  - 从 `~/.autiva/project/{projectName}/` 读取项目文件
  - 自动检测运行时（package.json -> node, requirements.txt -> python, pom.xml -> java）
  - 读取 `.env` 文件中的环境变量
  - 发送到后端创建沙箱容器
  - 返回公网访问 URL
- `stopProject(projectName)`: 停止已部署的项目
- `listDeployments()`: 列出所有已部署的项目及状态

**Builder 参数：**
- `backendUrl(String)`: 后端服务地址，默认 `http://localhost:9527`
- `clientId(String)`: 客户端 ID，默认 `autiva-user`

**文件过滤规则：**
- 忽略目录：node_modules, .git, __pycache__, .idea, .vscode, target, build, dist, .next, .nuxt, .cache
- 忽略文件：.DS_Store, Thumbs.db, .env.local, .env.production
- 最大文件大小：1MB

### BackendClient
后端 HTTP 客户端，使用 Java 11+ 内置的 `java.net.http.HttpClient` 与后端通信。

**核心方法：**
- `deployProject(clientId, projectName, files, runtime, envVars)`: 部署项目
- `stopProject(clientId, projectName)`: 停止项目
- `getStatus(clientId)`: 获取部署状态
- `isBackendAvailable()`: 检查后端是否可用

**内部记录类：**
- `ProjectFileInfo(path, content)`: 项目文件信息
- `DeployResponse(success, url, message, sandboxId, subdomain)`: 部署响应
- `StopResponse(success, message)`: 停止响应
- `StatusResponse(success, data)`: 状态响应

## 使用示例

```java
DeployTool deployTool = DeployTool.builder()
        .backendUrl("http://localhost:9527")
        .clientId("user-123")
        .build();
```

## 部署流程

1. 用户通过 AI 智能体调用 `deploy` 工具
2. DeployTool 从 `~/.autiva/project/{projectName}/` 读取所有项目文件
3. 自动检测运行时环境
4. 读取 `.env` 文件中的环境变量
5. 通过 BackendClient 发送 HTTP POST 请求到后端
6. 后端创建 OpenSandbox 沙箱容器
7. 后端写入项目文件、安装依赖、创建 BaaS 资源、注入环境变量
8. 后端启动应用程序
9. 返回公网访问 URL

## 设计模式
- Builder 模式：DeployTool 使用 Builder 创建，不依赖 Spring 管理
- 与其他工具（FileSystemTools, ShellTools 等）保持一致的设计风格

## 注意事项
1. 项目文件必须先写入 `~/.autiva/project/{projectName}/` 目录
2. 后端服务必须运行在 `http://localhost:9527`
3. 部署超时时间为 5 分钟
4. 大文件（>1MB）会被自动跳过
