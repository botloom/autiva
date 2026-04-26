# VM 包 (ViewModel)

## 概述
本包实现了 MVVM 架构中的 ViewModel 层，负责管理视图状态和业务逻辑。ViewModel 不持有任何 UI 引用，通过 JavaFX 属性与 Controller 通信。

## MVVM 职责划分

### ViewModel 职责
- 管理视图状态（JavaFX Properties / ObservableList）
- 业务逻辑处理（数据加载、转换、持久化）
- 调用 Service/Manager 层
- 数据格式化（时间格式化、类型标签转换等）
- **不持有任何 UI 引用**（Node、Scene、Stage）

### Controller 职责
- FXML 绑定和 UI 初始化
- UI 事件处理（委托给 ViewModel）
- UI 组件动态创建
- 动画和视觉效果

## 核心类

### HomePageViewModel
主页视图模型，管理聊天交互状态和消息流处理。

**Spring 注解：** `@Component`

**依赖注入：**
- `SessionManager`: 会话管理器

**属性：**
- `messages`: 聊天消息列表（ObservableList<ChatMessage>），Controller 监听此列表变化创建消息卡片

**核心方法：**
- `init()`: 初始化 Session，订阅 EventBus 消息流
- `processMessage(Message)`: 处理消息流，根据类型创建/更新 ChatMessage 对象
- `processAssistantMessage(JSONObject)`: 处理助手消息（流式累积、finishReason 判断）
- `processToolMessage(JSONObject)`: 处理工具消息
- `prepareHistoricalMessages()`: 准备历史消息，填充 messages 列表
- `addUserMessage(String)`: 添加用户消息到列表
- `sendMessage(UserMessage)`: 发送消息给智能体系统
- `hasHistoricalMessages()`: 是否有历史消息
- `clear()`: 清除所有状态

**消息流处理设计：**
- `streamMessage` StringBuilder 累积流式响应
- `currentStreamingMessage` 追踪当前流式消息的 ChatMessage 对象
- ASSISTANT 流式消息：累积文本 → 更新 ChatMessage.content → Controller 自动更新 UI
- ASSISTANT 完成：设置 finishReason（STOP/TOOL_CALLS）
- TOOL 消息：创建包含 ToolCallInfo/ToolResponseInfo 的 ChatMessage
- Controller 监听 ObservableList 变化，自动创建对应的消息卡片组件

### AgentPageViewModel
智能体页视图模型，管理智能体列表和文件操作。

**Spring 注解：** `@Component`

**依赖注入：**
- `AgentManager`: 智能体管理器

**属性：**
- `mainAgents`: 主智能体列表（ObservableList<AgentFolder>）
- `subagents`: 子智能体列表（ObservableList<SubagentFolder>）

**核心方法：**
- `loadAgents()`: 加载主智能体和子智能体列表
- `readFileContent(AgentFile)`: 读取配置文件内容
- `readSubagentContent(SubagentFolder)`: 读取子智能体配置内容
- `saveFile(AgentFile, String)`: 保存文件
- `saveSubagentConfig(String, String)`: 保存子智能体配置
- `deleteSubagent(String)`: 删除子智能体

### SettingsPageViewModel
设置页视图模型，管理配置状态。

**Spring 注解：** `@Component`

**依赖注入：**
- `ConfigManager`: 配置管理器

**属性：**
- `browserPath`: 浏览器路径（StringProperty）
- `dingTalkClientId`: 钉钉 Client ID（StringProperty）
- `dingTalkClientSecret`: 钉钉 Client Secret（StringProperty）
- `deepseekApiKey`: DeepSeek API Key（StringProperty）
- `zApiKey`: 智谱 AI API Key（StringProperty）

**核心方法：**
- `loadFromStore()`: 从 ConfigManager 加载配置到属性
- `save()`: 将属性值保存到 ConfigManager
- `reset()`: 重置为默认值并保存

**绑定模式：**
- Controller 的 TextField/PasswordField 与 ViewModel 属性双向绑定
- 用户修改 UI → 自动更新 ViewModel 属性 → 调用 save() 持久化

### SkillPageViewModel
技能页视图模型，管理技能列表和操作。

**Spring 注解：** `@Component`

**依赖注入：**
- `SkillManager`: 技能管理器

**属性：**
- `skills`: 技能列表（ObservableList<Skill>）

**核心方法：**
- `loadSkills()`: 加载技能列表
- `importSkillFromZip(Path)`: 从 ZIP 导入技能
- `deleteSkill(String)`: 删除技能

### McpPageViewModel
MCP 页视图模型，管理 MCP 服务器列表和操作。

**Spring 注解：** `@Component`

**依赖注入：**
- `McpManager`: MCP 管理器

**属性：**
- `servers`: 服务器列表（ObservableList<McpServer>）

**核心方法：**
- `loadServers()`: 加载服务器列表
- `addServer(McpServer)`: 添加服务器
- `updateServer(String, McpServer)`: 更新服务器
- `deleteServer(String)`: 删除服务器
- `buildServerDescription(McpServer)`: 构建服务器描述文本（数据格式化逻辑）

### TaskPageViewModel
任务页视图模型，管理定时任务列表和数据格式化。

**Spring 注解：** `@Component`

**依赖注入：**
- `CronManager`: 定时任务管理器

**属性：**
- `tasks`: 任务列表（ObservableList<CronTaskInfo>）

**核心方法：**
- `loadTasks()`: 加载任务列表
- `getTasksGroupedBySessionId()`: 按 Session ID 分组任务
- `triggerTask(String, String)`: 触发任务
- `deleteTask(String, String)`: 删除任务
- `getTypeLabel(String)`: 获取任务类型标签（数据格式化）
- `getTaskConfig(CronTaskInfo)`: 获取任务配置描述（数据格式化）
- `truncateMessage(String)`: 截断消息（数据格式化）
- `formatCreateTime(Instant)`: 格式化创建时间（数据格式化）

## 与其他组件的关系

1. **Manager/Service 层**: ViewModel 调用 Manager/Service 进行数据操作
2. **Controller 层**: Controller 调用 ViewModel 方法，监听 ViewModel 属性变化
3. **Store**: ViewModel 可更新全局状态（如 statusText）
4. **EventBus**: HomePageViewModel 订阅智能体事件

## 设计模式
- MVVM 模式：分离视图和业务逻辑
- 响应式编程：使用 JavaFX 属性和 ObservableList
- 观察者模式：属性变化通知 Controller 更新 UI
- 工厂模式：通过 `getOrCreateForSource` 获取 Session

## 注意事项
1. UI 更新必须在 JavaFX 应用线程执行
2. ViewModel 不持有任何 UI 引用（Node、Scene、Stage）
3. 属性绑定是双向的，注意循环更新
4. ObservableList 的变更会自动通知监听者
5. 新增 ViewModel 需遵循相同的职责划分原则
