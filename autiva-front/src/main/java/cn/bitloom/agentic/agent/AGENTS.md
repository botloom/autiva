# Agent 包

## 概述
本包实现了智能体核心系统，所有智能体继承自 AbstractAgent，通过 AgentManager 进行统一管理。智能体通过 EventBus 的 Inbox/Outbox 双通道进行消息收发。

## 核心类

### AbstractAgent
抽象基类，所有智能体的父类。

**核心功能：**
- 提供统一的 ChatClient 初始化和配置
- 集成 ChatMemory 实现对话记忆
- 集成 LoggingAdvisor 实现日志记录
- 自动注册到 AgentManager
- 支持多模型切换（DeepSeek、智谱AI）

**依赖注入：**
- `WorkspaceManager`: 工作目录管理器
- `ToolManager`: 工具管理器
- `SkillManager`: 技能管理器
- `AgentManager`: 智能体管理器
- `ChatModel`: 聊天模型（deepSeekChatModel、zhiPuAiChatModel）
- `ChatMemory`: 对话记忆
- `LoggingAdvisor`: 日志记录器
- `ToolCallingManager`: 工具调用管理器
- `ConfigManager`: 配置管理器
- `SessionManager`: 会话管理器

**关键方法：**
- `run()`: 抽象方法，子类实现具体的运行逻辑
- `getIdentity()`: 抽象方法，返回智能体身份标识
- `model(ModelEnum)`: 获取指定模型的 ChatClient
- `getSystemPrompt()`: 获取系统提示词

**系统提示词结构：**
```
## 技能
[技能描述]

---

## 工作目录
Working directory: ~/.autiva/workspace/{agentName}

---

## 项目上下文
[工作目录下的 .md 文件内容]

---

## 时间
[当前时间]

---

## 运行时
agent:{agentName}
```

### AgentManager
智能体管理器，管理所有注册的智能体实例、会话绑定关系和工作目录。

**核心属性：**
- `agents`: Map<String, AbstractAgent> - 智能体实例映射
- `agentSessionMap`: Map<String, String> - 智能体与会话的绑定关系
- `currentSubAgentMap`: Map<String, String> - 当前子智能体映射

**核心方法：**
- `init()`: 初始化智能体工作目录
- `register(name, agent)`: 注册智能体
- `unregister(name)`: 注销智能体
- `getAgent(name)`: 获取指定智能体
- `bindSession(agentName, sessionId)`: 绑定智能体与会话
- `getSessionByAgent(agentName)`: 获取智能体绑定的会话ID
- `getAgentBySession(sessionId)`: 根据会话ID获取智能体
- `listAgents()`: 列出所有智能体及其状态
- `getDescription(agentName)`: 获取智能体描述（读取工作目录下的.md文件）
- `exists(name)`: 检查智能体是否存在
- `count()`: 获取智能体数量
- `loadAgentFolders()`: 加载智能体文件夹列表

**AgentInfo 记录类：**
```java
public record AgentInfo(String name, AgentStatusEnum status, String sessionId) {}
```

**AgentFolder 类：**
```java
public static class AgentFolder {
    private final String name;
    private final Path path;
    private final List<AgentFile> files;
    // getter methods...
}
```

**AgentFile 类：**
```java
public static class AgentFile {
    private final Path path;
    private final String displayName;
    // getter methods...
}
```

### MainAgent
主智能体，处理用户的主要对话。

**功能：**
- 订阅 EventBus Inbox
- 处理用户请求并通过 Outbox 返回响应
- 支持流式响应和非流式响应两种模式
- 工具列表可通过配置文件动态配置
- 默认使用智谱AI模型（ModelEnum.Z）

**响应模式：**
- `STREAM`: 流式响应，逐字返回
- `BLOCK`: 阻塞响应，一次性返回完整结果

**工具集：**
工具列表通过 `ConfigManager.getAgentToolList(agentName)` 获取，可在智能体配置页面为每个智能体配置。
默认工具包括：
- `read`, `write`, `edit`, `exec`: 文件和命令操作
- `web_search`, `web_fetch`: 网络操作
- `cron_create`, `cron_list`, `cron_delete`, `cron_trigger`: 定时任务

### 枚举类
- `AgentStatusEnum`: 智能体状态 (IDLE, WORKING, SHUTDOWN)
- `AgentIdentityEnum`: 智能体身份标识 (MAIN, DOCTOR)
- `ModelEnum`: 支持的模型 (DEEPSEEK, Z)

## 消息流程

```
用户消息 -> EventBus.inBoxPublish()
                    │
                    ▼
            MainAgent/DoctorAgent
            (订阅 Inbox，处理消息)
                    │
                    ▼
            EventBus.outBoxPublish()
                    │
                    ▼
            用户接收响应
```

## 使用示例

### 创建自定义智能体
```java
@Component
public class MyAgent extends AbstractAgent {
    
    @Override
    protected void run() {
        EventBus.inBoxSubscribe()
            .concatMap(event -> {
                this.status = AgentStatusEnum.WORKING;
                Session session = sessionManager.getById(event.getSessionId());
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

## 设计模式
- 模板方法模式：AbstractAgent 定义骨架
- 观察者模式：通过 EventBus 订阅消息
- 响应式编程：基于 Project Reactor

## 注意事项
1. 智能体在 @PostConstruct 时自动注册到 AgentManager
2. 使用 EventBus 的 Inbox/Outbox 双通道进行消息收发
3. ChatMemory 的 CONVERSATION_ID 使用 sessionId
4. 状态变更需要注意线程安全
