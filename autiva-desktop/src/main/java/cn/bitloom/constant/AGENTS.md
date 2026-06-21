# Constant 包

## 概述
本包定义了应用的全局常量，包括路径、窗口尺寸等。

## 核心类

### AppConstants
应用常量类，使用嵌套类组织不同类别的常量。

### Base 常量
基础路径常量：
- `USER_HOME`: 用户主目录
- `APP_DIR`: 应用目录 (`~/.autiva`)
- `LOGS_DIR`: 日志目录 (`~/.autiva/logs`)
- `WORKSPACE_DIR`: 工作区目录 (`~/.autiva/workspace`)
- `AGENT_CONFIG_FILE`: Agent 配置文件 (`~/.autiva/config.json`)，包含 tools/mcpServers/skills/subagents 字段
- `CONFIG_FILE`: 设置文件 (`~/.autiva/settings.properties`)
- `SKILLS_DIR`: 全局技能目录 (`~/.autiva/skills`)
- `SUBAGENTS_DIR`: 全局子智能体目录 (`~/.autiva/subagents`)
- `AGENTS_MD`: 主智能体人格文件 (`~/.autiva/AGENTS.md`)
- `MEMORY_MD`: 长期记忆文件 (`~/.autiva/MEMORY.md`)
- `BOOTSTRAP_MD`: 首次启动引导文件 (`~/.autiva/BOOTSTRAP.md`)
- `MEMORY_DIR`: 长期记忆流水账目录 (`~/.autiva/memory`)
- `PROJECTS_DIR`: 项目工作区根目录 (`~/.autiva/projects`)，编码智能体场景使用
- `PROJECTS_REGISTRY_FILE`: 项目注册表文件 (`~/.autiva/projects/registry.json`)，持久化项目列表
- `DEFAULT_USER`: 默认用户标识 (`"default"`)

### Base 路径方法
- `agentWorkspaceDir(agentId)`: 获取指定 agent 的工作空间目录 (`~/.autiva/workspace/{agentId}`)
- `agentConfigFile(agentId)`: 获取指定 agent 的 config.json 路径 (`~/.autiva/workspace/{agentId}/config.json`)
- `agentContextDir(agentId, sessionId)`: 获取指定 agent 的 context 目录 (`~/.autiva/workspace/{agentId}/context/{sessionId}`)
- `agentSessionsDir(agentId)`: 获取指定 agent 的 sessions 目录 (`~/.autiva/workspace/{agentId}/sessions`)
- `agentSessionDir(agentId, sessionId)`: 获取指定 agent 的某个 session 目录 (`~/.autiva/workspace/{agentId}/sessions/{sessionId}`)

### Stage 常量
窗口相关常量：
- `WIDTH`: 默认窗口宽度 (800)
- `HEIGHT`: 默认窗口高度 (500)
- `FXML`: 主 FXML 文件路径
- `ICON`: 应用图标路径（PNG 格式）
- `ICON_SVG`: 应用图标路径（SVG 格式）
- `NAME`: 应用名称

## 目录结构

```
~/.autiva/
├── AGENTS.md                    ← 静态：人格 + 行为约定
├── MEMORY.md                    ← 长期记忆：策划后的长期事实
├── memory/                      ← 长期记忆：每天追加的事实流水账
│   └── YYYY-MM-DD.md
├── BOOTSTRAP.md                 ← 静态：首次启动引导文件
├── config.json                  ← 全局 Agent 配置（tools/mcpServers/skills/subagents）
├── workspace/                   ← 运行时：每个 agent 的运行时根
│   ├── default/                 ← 默认主智能体
│   │   ├── config.json          ← MCP server + 工具白名单 + skill + subagent（覆盖根 config.json）
│   │   └── sessions/<session-id>/
│   │       ├── metadata.json    ← Session 序列化（唯一状态源）
│   │       └── messages.jsonl   ← 消息持久化
│   └── <agent-id>/              ← 自定义智能体（主智能体的扩展）
│       ├── xxx.md               ← 自定义提示词文件（覆盖根目录同名文件）
│       ├── config.json          ← 智能体级配置（覆盖根 config.json）
│       └── sessions/<session-id>/
│           ├── metadata.json    ← Session 序列化（唯一状态源）
│           └── messages.jsonl   ← 消息持久化
├── skills/                      ← 静态：技能目录
├── subagents/                   ← 静态：子 agent 声明
├── logs/                        ← 日志目录
└── settings.properties          ← 应用设置
```

## 配置合并策略

自定义智能体是主智能体的扩展，区别在于提示词模板、工具、子 agent、skill 的不同：

1. **config.json 合并**：先读根 `~/.autiva/config.json`，再读 `workspace/{agentId}/config.json`，workspace 非空字段覆盖 root
2. **.md 文件覆盖**：workspace 下的同名 .md 文件替换根目录版本（如 workspace/my-agent/AGENTS.md 替换 ~/.autiva/AGENTS.md）

## 使用示例

### 获取路径常量
```java
Path appDir = AppConstants.Base.APP_DIR;
Path workspaceDir = AppConstants.Base.WORKSPACE_DIR;
Path configFile = AppConstants.Base.CONFIG_FILE;
Path agentConfig = AppConstants.Base.AGENT_CONFIG_FILE;
```

### 获取 Agent 级路径
```java
String agentId = "my-agent";
Path agentWorkspace = AppConstants.Base.agentWorkspaceDir(agentId);
Path agentConfig = AppConstants.Base.agentConfigFile(agentId);
Path sessionDir = AppConstants.Base.agentSessionDir(agentId, "session-123");
```

### 获取窗口常量
```java
double width = AppConstants.Stage.WIDTH;
double height = AppConstants.Stage.HEIGHT;
String fxml = AppConstants.Stage.FXML;
String icon = AppConstants.Stage.ICON;
```

## 设计原则
1. 常量按类别分组（Base、Stage 等）
2. 使用私有构造函数防止实例化
3. 使用 static final 确保不可变
4. 路径使用 Path 类型，便于操作

## 注意事项
1. 所有常量都是静态不可变的
2. 路径常量使用 Path 类型
3. 应用目录在首次使用时由 AppBootstrap 自动创建
4. 常量修改需要重新编译
