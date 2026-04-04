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
- `SKILL_DIR`: 技能目录 (`~/.autiva/skills`)
- `MCP_DIR`: MCP 配置目录 (`~/.autiva/mcp`)
- `WORKSPACE_DIR`: 工作区目录 (`~/.autiva/workspace`)
- `SESSION_DIR`: 会话目录 (`~/.autiva/sessions`)
- `MCP_CONFIG_FILE`: MCP 配置文件 (`~/.autiva/mcp/mcp-servers.json`)
- `CONFIG_FILE`: 设置文件 (`~/.autiva/settings.properties`)
- `CURRENT_SESSION_FILE`: 当前会话指针文件 (`~/.autiva/sessions/current.json`)

### Stage 常量
窗口相关常量：
- `WIDTH`: 默认窗口宽度 (800)
- `HEIGHT`: 默认窗口高度 (500)
- `FXML`: 主 FXML 文件路径
- `ICON`: 应用图标路径

## 目录结构

```
~/.autiva/
├── sessions/              # 会话目录
│   ├── {sessionId-1}/
│   │   ├── metadata.json
│   │   └── messages.jsonl
│   ├── {sessionId-2}/
│   │   ├── metadata.json
│   │   └── messages.jsonl
│   └── current.json       # 当前会话指针
│
├── workspace/             # 工作区目录
│   ├── main/              # MainAgent工作区
│   │   ├── memory/
│   │   │   ├── 2025-03-24.jsonl
│   │   │   └── 2025-03-25.jsonl
│   │   └── *.md
│   └── coder/
│       ├── memory/
│       └── *.md
│
├── logs/                  # 日志目录
│   └── transcripts/       # 压缩归档目录
│
├── skills/                # 技能目录
│   ├── skill-1/
│   │   └── SKILL.md
│   └── skill-2/
│       └── SKILL.md
│
├── mcp/                   # MCP 配置目录
│   └── mcp-servers.json
│
└── settings.properties    # 应用设置
```

## 使用示例

### 获取路径常量
```java
Path appDir = AppConstants.Base.APP_DIR;
Path sessionDir = AppConstants.Base.SESSION_DIR;
Path workspaceDir = AppConstants.Base.WORKSPACE_DIR;
Path configFile = AppConstants.Base.CONFIG_FILE;
Path currentSessionFile = AppConstants.Base.CURRENT_SESSION_FILE;
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

## 扩展指南
添加新的常量类别：
```java
public static class NewCategory {
    private NewCategory() {}

    public static final String CONSTANT_NAME = "value";
}
```

## 注意事项
1. 所有常量都是静态不可变的
2. 路径常量使用 Path 类型
3. 应用目录在首次使用时可能需要创建
4. 常量修改需要重新编译
