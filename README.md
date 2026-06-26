# Autiva

> AI 智能体桌面客户端 — 基于 JavaFX 25 + Spring Boot 4.1 + Spring AI 2.0 构建

Autiva 是一个可扩展的 AI 智能体桌面应用，提供多智能体架构、工具系统、技能系统、进化引擎和 IM 集成，旨在打造一个可自主进化的 AI 代理平台。

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
│       │   ├── a2ui/        #   动态界面协议（组件模型）
│       │   ├── agent/       #   智能体核心（MAIN + SUBAGENT 双类型）
│       │   ├── diff/        #   差异计算服务
│       │   ├── event/       #   事件总线（含 A2UI 事件）
│       │   ├── evolve/      #   进化系统引擎（完整 16 子包）
│       │   ├── hook/        #   Agent 钩子系统
│       │   ├── memory/      #   对话记忆（含压缩）
│       │   ├── model/       #   AI 模型工厂
│       │   ├── project/     #   项目管理（文件树 / Git）
│       │   ├── session/     #   会话管理
│       │   ├── skill/       #   技能系统
│       │   ├── task/        #   后台任务管理
│       │   ├── tool/        #   工具系统（13 子类，43+ 工具）
│       │   └── util/        #   Agent 工具类
│       ├── bridge/          # 外部集成
│       │   ├── wechat/      #   微信 iLink 协议接入
│       │   └── desktop/     #   Tool ↔ UI 桥接
│       ├── bootstrap/       # 应用启动引导
│       ├── config/          # 配置管理
│       ├── constant/        # 常量定义
│       ├── controller/      # JavaFX 页面控制器
│       ├── cron/            # 定时任务引擎
│       ├── exception/       # 异常体系
│       ├── holder/          # UI 组件持有者
│       ├── node/            # 自定义 UI 节点（A2UI、终端、画布、编辑器等）
│       ├── router/          # 页面路由
│       ├── store/           # 全局状态管理
│       ├── util/            # 通用工具类
│       ├── vm/              # 视图模型层
│       └── window/          # 窗口管理
├── autiva-gene-door/        # 进化基因入口模块（骨架项目）
└── sandbox/                 # 沙箱执行环境（Python OpenSandbox，独立项目）
```

## 核心功能

### 智能体系统
- 统一 Agent 架构：MAIN（主智能体）和 SUBAGENT（子智能体）均为 `Agent` 类实例，由 `AgentDefinition` 区分
- 支持流式 / 阻塞两种响应模式
- 多模型切换（DeepSeek、智谱 AI 等）
- 对话记忆自动压缩与上下文管理
- 支持自定义智能体定义（agent.md + config.json），通过 `~/.autiva/agents/` 管理
- 子智能体系统：Coder（编码）、Explore（探索）、Plan（架构规划）、Review（代码审查）、Doctor（诊断）、General（全栈编码）

### 工具系统
- **文件操作**：Read / Write / Edit（精确字符串替换）
- **文件搜索**：Glob（模式匹配）、Grep（正则搜索）
- **命令执行**：Command（Shell 执行）、Process（后台进程管理）
- **网络操作**：WebSearch、WebFetch
- **任务管理**：Task（子智能体委派）、TaskOutput
- **用户交互**：AskUserQuestion、TodoWrite
- **A2UI**：智能体动态生成 UI 组件（表单、按钮、卡片等）
- **配置管理**：AppConfig（读取/修改/路径查询）、Memory（4 个操作工具）、MCP（3 个管理工具）、Skill（4 个管理工具）、Evolve（8 个管理工具）
- **定时任务**：Cron（创建/列表/触发/删除）

### 技能系统
- 通过 ZIP 包动态加载专业知识
- YAML frontmatter 格式的技能定义
- 技能热加载与运行时管理

### 进化系统（Evolve）
- **EvolverAgent**：智能体自我进化引擎
- **基因模型**：Gene + GeneCategory + Capsule + GeneRuntimeType
- **信号系统**：SignalExtractor + SignalHistory + 多类型信号检测
- **策略引擎**：StrategyEngine + StrategyPreset（多种进化策略）
- **路由引擎**：RoutingEngine + RoutingEntry（动态路由）
- **执行引擎**：JavaGeneExecutor / ShellGeneExecutor / StrategyGeneExecutor
- **安全保障**：EvolutionSafety（安全校验）
- **固化器**：CanaryCheck（金丝雀检查）+ EvolutionEvent + Solidifier
- **经验引擎**：ExperienceEngine + Experience + ExperienceTarget
- **记忆引擎**：MemoryEngine + MemoryRule
- **基因仓库**：GeneRepository + GeneMigration
- **周期管理**：完整进化周期控制（Cycle → Route → Execute → Solidify → Record）

### 记忆系统
- 长期记忆（persistent）：跨会话持续存在
- 会话记忆（in-memory）：自动压缩
- 4 个管理工具：MemorySave / MemoryUpdate / MemoryDelete / MemorySearch

### MCP 集成
- 支持 STDIO、SSE、STREAMABLE_HTTP 传输协议
- 基于 Spring AI MCP Client 实现
- MCP 服务器动态注册与管理（List / Path / Update）

### A2UI 动态界面
智能体可以通过标准化协议动态生成交互式 UI，包括：
- 布局组件：Row、Column、List、Tabs、Card
- 交互组件：TextFiled、Button、CheckBox、ChoicePicker、Slider
- 展示组件：Text、Image、Icon、Divider
- 事件系统：A2UIEvent + A2UIActionEvent 完整生命周期

### 外部集成
- **微信**：iLink 协议接入（扫码登录、多类型消息收发：文本/图片/视频/文件/语音）
- **Tool ↔ UI 桥接**：桌面端 ToolUIBridge（A2UI 消息、Question/Task/Todo 卡片渲染）

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
├── agents/                      # 智能体定义（不随 session 变化）
│   ├── default/                 # 默认主智能体
│   │   ├── agent.md             # 智能体定义（YAML frontmatter + 提示词）
│   │   ├── config.json          # MCP + 工具白名单 + skill + subagent
│   │   ├── AGENTS.md            # 人格 + 行为约定
│   │   ├── MEMORY.md            # 长期记忆
│   │   ├── BOOTSTRAP.md         # 首次启动引导
│   ├── coder/                   # 编码子智能体
│   │   └── agent.md
│   ├── explore/                 # 探索子智能体
│   │   └── agent.md
│   └── <agent-id>/              # 用户自定义智能体
├── workspace/                   # 运行时数据（仅 session 相关）
│   └── <agent-id>/
│       └── sessions/
│           ├── metadata.json    # Session 序列化
│           └── messages.jsonl   # 消息持久化
├── skills/                      # 技能目录
├── mcp/                         # MCP 配置
└── settings.properties          # 应用配置
```

## 许可证

Apache License 2.0
