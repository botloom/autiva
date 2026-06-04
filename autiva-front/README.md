# Autiva Front

Autiva 桌面客户端，基于 JavaFX + Spring AI 构建的 AI 智能体应用。

## 技术栈

- **Java 17**
- **JavaFX** - 桌面 UI 框架
- **Spring Boot** - 应用框架
- **Spring AI** - AI 模型集成（支持 DeepSeek、智谱 AI）
- **Project Reactor** - 响应式编程

## 核心功能

### 智能体系统
- 多智能体架构，支持自定义智能体
- 流式/阻塞两种响应模式
- 多模型切换（DeepSeek、智谱 AI）
- 对话记忆与自动压缩

### 工具系统
- 文件操作：Read、Write、Edit（精确字符串替换）
- 文件搜索：Glob（模式匹配）、Grep（正则搜索）
- 命令执行：Command、CommandOutput、KillCommand
- 网络操作：WebSearch、WebFetch
- 任务管理：Task、TaskOutput、SessionQuery
- 用户交互：AskUserQuestion、TodoWrite
- 配置管理：AppConfig、SkillConfig、McpConfig、SubagentConfig、MemoryManage
- 进化系统：EvolveApply、EvolveConfig、EvolveCycle、EvolveGeneManage、EvolveQuery

### 技能系统
- 动态加载专业知识
- ZIP 包导入技能
- YAML frontmatter 格式配置

### MCP 集成
- 支持 STDIO、SSE、STREAMABLE_HTTP 传输协议
- 动态注册 MCP 服务器

### 进化系统
- EvolverAgent：智能体自我进化引擎
- 基因管理：创建、查询、应用进化基因
- 进化周期：自动触发进化流程
- 金丝雀检查：进化稳定性验证

### 第三方接入
- 钉钉 Stream 模式：单聊/群聊机器人
- 微信 iLink 协议：扫码登录、消息收发

## 项目结构

```
src/main/java/cn/bitloom/
├── agentic/                 # 智能体核心
│   ├── agent/              # 智能体实现
│   ├── advisor/            # 日志顾问
│   ├── event/              # 事件总线
│   ├── mcp/                # MCP 客户端
│   ├── memory/             # 对话记忆
│   ├── session/            # 会话管理
│   ├── skill/              # 技能管理
│   ├── task/               # 任务管理
│   ├── tool/               # 工具系统
│   │   ├── core/           # 核心工具（文件、命令、网络）
│   │   ├── manage/         # 管理工具（配置、进化）
│   │   └── command/        # 命令执行实现
│   └── workflow/           # 工作流引擎
│   └── evolve/             # 进化系统
├── bridge/                  # 第三方桥接
│   ├── dingtalk/           # 钉钉机器人
│   └── weixin/             # 微信 iLink 接入
├── config/                  # 配置管理
├── constant/                # 常量定义
├── controller/              # JavaFX 控制器
├── cron/                    # 定时任务管理
├── exception/               # 异常定义
├── holder/                  # UI 持有者
├── node/                    # 自定义节点
├── router/                  # 路由管理
├── store/                   # 全局状态
├── util/                    # 工具类
├── vm/                      # 视图模型
└── window/                  # 窗口管理
```

## 配置文件

配置文件位于 `~/.autiva/settings.properties`：

```properties
# Application Settings
app.session.isolation=PER_PEER

# DingTalk Configuration
dingtalk.app.client-id=your-client-id
dingtalk.app.client-secret=your-client-secret
dingtalk.app.agent-id=your-agent-id

# DeepSeek Configuration
spring.ai.deepseek.chat.base-url=https://api.deepseek.com
spring.ai.deepseek.chat.api-key=your-deepseek-api-key
spring.ai.deepseek.chat.options.model=deepseek-chat

# ZhiPu AI Configuration
spring.ai.zhipuai.chat.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.zhipuai.api-key=your-zhipuai-api-key
spring.ai.zhipuai.chat.options.model=glm-4-flash

# WeChat iLink Configuration
weixin.ilink.enabled=false

# Search Configuration
app.search.bocha-api-key=your-bocha-api-key
```

## 数据目录

```
~/.autiva/
├── settings.properties      # 应用配置
├── skills/                  # 技能目录
│   └── skill-name/
│       └── SKILL.md
├── mcp/                     # MCP 配置
│   └── mcp-servers.json
├── workspace/               # 智能体工作目录
│   └── MAIN/
│       └── *.md
├── sessions/                # 会话数据
│   └── MAIN-DM-source-target/
│       ├── metadata.json
│       └── messages.jsonl
└── logs/                    # 日志目录
    └── transcripts/         # 对话记录归档
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 运行应用

```bash
cd autiva-front
mvn javafx:run
```

### 打包应用

```bash
mvn clean package
java -jar target/autiva-front-1.0-SNAPSHOT.jar
```

## 页面功能

| 页面 | 功能 |
|------|------|
| 主页 | 聊天交互、模型选择、任务卡片 |
| 智能体页 | 智能体配置、工具开关、子智能体管理 |
| 技能页 | 技能导入、管理、ZIP 包导入 |
| MCP 页 | MCP 服务器配置、动态注册 |
| 任务页 | 后台任务管理、任务输出查看 |
| 设置页 | 应用配置、API Key 管理、扫码登录 |

## 扩展开发

### 创建自定义智能体

```java
@Component
public class MyAgent extends AbstractAgent {
    
    @Override
    protected void run() {
        EventBus.inBoxSubscribe()
            .concatMap(event -> {
                this.status = AgentStatusEnum.WORKING;
                return this.model(ModelEnum.Z)
                    .prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, event.getSessionId()))
                    .toolContext(Map.of("sessionId", event.getSessionId()))
                    .messages(event.getMessage())
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> 
                        EventBus.outBoxPublish(event.getSessionId(), response.getResult().getOutput()))
                    .doOnComplete(() -> this.status = AgentStatusEnum.IDLE);
            })
            .subscribe();
    }
    
    @Override
    protected AgentIdentityEnum getIdentity() {
        return AgentIdentityEnum.MAIN;
    }
}
```

### 创建自定义工具

```java
@Slf4j
@Component
public class MyTool implements ITool {
    
    @Tool(name = "myTool", description = "我的自定义工具")
    public ToolResult myTool(
        @ToolParam(description = "参数描述") String param
    ) {
        log.info("[ToolCall] myTool - 执行操作: param={}", param);
        try {
            String result = "结果: " + param;
            return ToolResult.success("执行成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] myTool - 执行失败", e);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }
}
```

## 许可证

Apache License 2.0
