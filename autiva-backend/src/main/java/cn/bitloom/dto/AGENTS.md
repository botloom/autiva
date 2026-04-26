# DTO 包

## 概述
本包定义了数据传输对象（Data Transfer Objects），用于前后端通信。

## 核心类

### ProjectFile
项目文件记录类，表示项目中的一个文件。

**字段：**
- `path`: 文件相对路径（如 "index.js", "src/app.js"）
- `content`: 文件内容

### DeployRequest
部署请求记录类，包含项目部署所需的全部信息。

**字段：**
- `clientId`: 客户端 ID
- `projectName`: 项目名称
- `files`: 项目文件列表 (`List<ProjectFile>`)
- `runtime`: 运行时 (node/python/java)，可为 null 自动检测
- `envVars`: 环境变量 (`Map<String, String>`)

### DeployResult
部署结果记录类。

**字段：**
- `success`: 是否成功
- `url`: 公网访问 URL
- `message`: 结果消息
- `sandboxId`: 沙箱 ID
- `subdomain`: 子域名

**静态方法：**
- `success(url, sandboxId, subdomain)`: 创建成功结果
- `failure(message)`: 创建失败结果
