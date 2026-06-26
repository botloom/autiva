# Autiva

> AI 智能体桌面客户端 — 基于 JavaFX + Spring AI 构建

Autiva 是一个可扩展的 AI 智能体桌面应用，提供多智能体架构、工具系统、技能系统、进化引擎和第三方 IM 集成，旨在打造一个可自主进化的 AI 代理平台。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **JDK** | 25 | 运行环境 |
| **JavaFX** | 25.0.3 | 桌面 UI 框架 |
| **Spring Boot** | 4.1.0 | 应用框架 |
| **Spring AI** | 2.0.0 | AI 模型集成 |
| **Project Reactor** | — | 响应式编程 |
| **Maven** | 3.8+ | 构建工具 |

## 模块结构

```
autiva/
├── autiva-desktop/          # 主桌面应用（核心模块）
│   └── src/main/java/cn/bitloom/
│       ├── agentic/         # 智能体核心
│       │   ├── agent/       #   多智能体实现
│       │   ├── tool/        #   工具系统（文件/命令/搜索/A2UI...）
│       │   ├── session/     #   会话管理
│       │   ├── memory/      #   对话记忆
│       │   ├── skill/       #   技能系统
│       │   ├── task/        #   后台任务管理
│       │   ├── evolve/      #   进化系统引擎
│       │   ├── event/       #   事件总线
│       │   └── a2ui/        #   动态界面协议
│       ├── controller/      # JavaFX 控制器
│       ├── vm/              # 视图模型
│       ├── node/            # 自定义 UI 节点
│       ├── bridge/          # 外部集成（钉钉/微信）
│       ├── config/          # 配置管理
│       └── router/          # 页面路由
├── autiva-gene-door/        # 进化基因入口模块
└── sandbox/                 # 沙箱执行环境（Python FastAPI）
```

## 核心功能

### 智能体系统
- 多智能体架构，支持自定义智能体定义与切换
- 流式 / 阻塞两种响应模式
- 多模型切换（DeepSeek、智谱 AI 等）
- 对话记忆与自动压缩

### 工具系统
- **文件操作**：Read / Write / Edit（精确字符串替换）
- **文件搜索**：Glob（模式匹配）、Grep（正则搜索）
- **命令执行**：Command（Shell 执行）、Process（后台进程管理）
- **网络操作**：WebSearch、WebFetch
- **任务管理**：Task（子智能体委派）、TaskOutput
- **用户交互**：AskUserQuestion、TodoWrite
- **A2UI**：智能体动态生成 UI 组件（表单、按钮、卡片等）
- **配置管理**：AppConfig / Memory / MCP / Skill / Evolution 全套管理工具

### 技能系统
- 通过 ZIP 包动态加载专业知识
- YAML frontmatter 格式的技能定义
- 技能热加载

### 进化系统（Evolve）
- **EvolverAgent**：智能体自我进化引擎
- **基因管理**：创建 / 查询 / 应用进化基因
- **进化周期**：自动触发进化流程
- **金丝雀检查**：进化稳定性验证
- **策略引擎**：多种进化策略预设

### MCP 集成
- 支持 STDIO、SSE、STREAMABLE_HTTP 传输协议
- MCP 服务器动态注册与管理

### 外部集成
- **钉钉**：Stream 模式机器人（单聊 / 群聊）
- **微信**：iLink 协议接入（扫码登录、消息收发）

### A2UI 动态界面
智能体可以通过标准化协议动态生成交互式 UI，包括：
- 布局组件：Row、Column、List、Tabs、Card
- 交互组件：TextField、Button、CheckBox、ChoicePicker、Slider
- 展示组件：Text、Image、Icon、Divider

## 快速开始

### 环境要求

- JDK 25+
- Maven 3.8+

### 运行

```bash
# 克隆项目后进入目录
cd autiva

# 编译
mvn clean compile

# 启动桌面应用
mvn -pl autiva-desktop javafx:run
```

### 打包

```bash
mvn clean package
```

Windows 打包：
```bash
.\build-package.ps1
```

macOS 打包：
```bash
./build-package-mac.sh
```

## 配置

配置文件位于 `~/.autiva/settings.properties`，主要配置项：

```properties
# AI 模型（DeepSeek）
spring.ai.deepseek.chat.api-key=your-api-key
spring.ai.deepseek.chat.options.model=deepseek-chat

# AI 模型（智谱 AI）
spring.ai.zhipuai.api-key=your-api-key
spring.ai.zhipuai.chat.options.model=glm-4-flash

# 钉钉接入（可选）
dingtalk.app.client-id=your-client-id

# 微信接入（可选）
weixin.ilink.enabled=false

# 搜索（可选，博查 API）
app.search.bocha-api-key=your-api-key

# 会话隔离模式
app.session.isolation=PER_PEER
```

## 数据目录

```
~/.autiva/
├── settings.properties     # 应用配置
├── skills/                 # 技能目录
├── mcp/                    # MCP 配置
├── workspace/              # 智能体工作目录
├── sessions/               # 会话数据
└── logs/                   # 日志
```

## 许可证

Apache License 2.0
