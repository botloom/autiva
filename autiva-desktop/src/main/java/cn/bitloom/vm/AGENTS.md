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
- `ProjectRegistry`: 项目注册表管理器（编码智能体场景）

**属性：**
- `messages`: 消息卡片列表（ObservableList<MessageCard>），ViewModel 直接创建卡片组件，Controller 监听此列表变化将卡片添加到 UI
- `isStreaming`: 流式生成状态（BooleanProperty），Controller 监听此属性切换发送/暂停/停止按钮
- `isPaused`: 暂停状态（BooleanProperty），Controller 监听此属性切换暂停/发送按钮
- `currentSessionId`: 当前会话ID（StringProperty），SideBarController 监听此属性刷新历史列表
- `agentProperty`: 当前选中的智能体（ObjectProperty<String>），默认 "default"
- `currentProject`: 当前编码项目（ProjectInfo），仅 coder 智能体场景使用，切换非 coder 智能体时清空

**核心方法：**
- `init()`: 初始化，仅同步 userId 到 Store，不加载/创建任何 session
- `subscribeOutBox()`: 订阅 Session outBox 消息流（切换 session 时重新订阅，使用 Disposable 管理）
- `createNewSession()`: 切换到初始态（session 设为 null，清空消息列表，重置状态，清空 currentProject），不创建真正的 session
- `switchToSession(String sessionId)`: 切换到指定 session（同步调用 activate 激活，activate 只加载最近 100 条消息，速度足够快；激活后订阅 OutBox 并渲染历史消息）
- `switchAgent(String agentId)`: 切换智能体（设置 Store.currentAgent，非 coder 智能体时清空 currentProject，调用 createNewSession）
- `sendMessage(String)`: 发送消息给智能体系统（接收纯文本，内部构建 MessageEvent），懒创建 session（首次发送时才创建），coder 智能体且有 currentProject 时通过 buildMessageWithContext() 在消息前附加项目信息（格式：`[项目: {name} @ {path}]\n`），暂停后恢复时重新激活会话，发送后翻转 Store.refreshHistory 触发侧边栏标题刷新
- `buildMessageWithContext(String)`: 构建带项目上下文的消息，coder 智能体且有 currentProject 时在消息前添加项目信息前缀
- `processMessage(MessageEvent)`: 处理消息流，根据 MessageEvent.Type 分发（USER/ASSISTANT/TOOL），直接访问结构化字段
- `processAssistantEvent(MessageEvent)`: 处理助手消息（根据 MessageEvent 的 finishReason 判断流式/完成/工具调用，直接调用 AssistantMessageCard.appendContent() 和 complete()）
- `processToolEvent(MessageEvent)`: 处理工具消息（直接创建 ToolMessageCard）
- `prependHistoricalMessages(List<MessageCard>)`: 在消息列表头部批量插入历史卡片（供 Controller 加载更多历史消息时调用）
- `convertEventToCards(MessageEvent)`: 将 MessageEvent 转换为卡片列表（用于历史消息加载，一个事件可能产生多个卡片）
- `prepareHistoricalMessages()`: 准备历史消息，渲染 session.getMessages() 中的全部消息（activate 已只加载最近 100 条），分批处理避免阻塞 FX 线程
- `loadMoreMessages(int count)`: 加载更多历史消息（从磁盘 messages.jsonl 按需读取，根据 memoryBaseOffset 和 memoryCursor 计算偏移，返回 List<MessageCard>）
- `hasMoreMessages()`: 是否还有更多历史消息可加载（基于 memoryBaseOffset > memoryCursor 判断）
- `addUserMessage(String)`: 添加用户消息卡片到列表
- `pauseGeneration()`: 暂停当前流式生成，调用 session.stop() 通知后端停止（同时停止所有子智能体会话），保留部分响应，设置 SessionState 为 PAUSED
- `hasHistoricalMessages()`: 是否有历史消息
- `clear()`: 清除所有状态，清空主 session 消息记录，删除所有子 session（从内存和磁盘）

**编码项目管理方法：**
- `getCurrentProject()`: 获取当前编码项目（ProjectInfo）
- `setCurrentProject(ProjectInfo)`: 设置当前编码项目
- `listProjects()`: 列出所有注册项目（委托 ProjectRegistry）
- `createNewProject(String name)`: 新建项目并设为当前（委托 ProjectRegistry.createProject）
- `registerLocalProject(String path, String name)`: 注册本地文件夹并设为当前（委托 ProjectRegistry.registerLocal）

**消息流处理设计（简化版）：**
- `currentAssistantCard`: 追踪当前流式消息的 AssistantMessageCard 对象
- ASSISTANT 流式消息：直接调用 `assistantCard.appendContent(chunk)` 累积内容 → 卡片内部触发 contentProperty 变化 → Controller 自动更新 UI
- ASSISTANT 完成（STOP）：调用 `assistantCard.complete("STOP")` → 若 `isValid()` 返回 false 则从 messages 列表移除
- ASSISTANT 完成（TOOL_CALLS）：调用 `assistantCard.complete("TOOL_CALLS")` → 若 `isValid()` 返回 false 则移除 → 创建工具调用卡片
- TOOL 消息：直接创建 ToolMessageCard
- Controller 监听 ObservableList 变化，自动将卡片添加到 UI
- **空白消息过滤**：`AssistantMessageCard.complete()` 时若累积内容为空会将 content 设为 null，ViewModel 通过 `isValid()` 判断是否移除
- **职责下沉**：累积逻辑下沉到 AssistantMessageCard，ViewModel 只做事件分发和卡片创建

### AgentPageViewModel
智能体页视图模型，管理智能体列表和文件操作。

**Spring 注解：** `@Component`

**依赖注入：**
- `AgentDefinitionManager`: 智能体定义管理器

**属性：**
- `mainAgents`: 主智能体列表（ObservableList<AgentDefinition>）

**核心方法：**
- `loadAgents()`: 同步加载主智能体列表（阻塞 FX 线程，仅用于兼容）
- `loadAgentsAsync(Runnable)`: 异步加载智能体列表，使用 `javafx.concurrent.Task` + `ExecutorManager.platformTaskExecutor` 在后台线程执行 I/O，完成后在 FX 线程更新 ObservableList 并回调 onLoaded
- `readFileContent(String agentId, String fileName)`: 读取配置文件内容
- `saveFileContent(String agentId, String fileName, String content)`: 保存配置文件内容，并重新加载智能体定义
- `createAgent(String agentId)`: 创建新智能体（复制 default 模板，创建 workspace 目录）
- `deleteAgent(String agentId)`: 删除智能体目录（递归删除）
- `copyAgent(String sourceAgentId, String targetAgentId)`: 复制智能体（复制文件并替换名称）
- `openAgentDirectory(String agentId)`: 用系统文件管理器打开智能体目录
- `getAgentFiles(String agentId)`: 获取智能体目录下的文件列表
- `agentExists(String agentId)`: 检查智能体是否已存在

### SettingsPageViewModel
设置页视图模型，管理配置状态。

**Spring 注解：** `@Component`

**依赖注入：**
- `ConfigManager`: 配置管理器

**属性：**
- `dingTalkClientId`: 钉钉 Client ID（StringProperty）
- `dingTalkClientSecret`: 钉钉 Client Secret（StringProperty）
- `deepseekBaseUrl`: DeepSeek API 基础地址（StringProperty）
- `deepseekCompletionsPath`: DeepSeek API 补全路径（StringProperty）
- `deepseekApiKey`: DeepSeek API Key（StringProperty）
- `deepseekChatModel`: DeepSeek 聊天模型名称（StringProperty）

**核心方法：**
- `loadFromStore()`: 从 ConfigManager 加载配置到属性
- `save()`: 将属性值保存到 ConfigManager，并通过 `EventBus.sysPublish(AutivaEventType.CONFIG_CHANGED)` 触发热更新
- `reset()`: 重置为默认值并保存，并通过 `EventBus.sysPublish(AutivaEventType.CONFIG_CHANGED)` 触发热更新

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
- `loadSkills()`: 同步加载技能列表（阻塞 FX 线程，仅用于兼容）
- `loadSkillsAsync(Runnable)`: 异步加载技能列表，使用 `javafx.concurrent.Task` + `ExecutorManager.platformTaskExecutor` 在后台线程执行 I/O，完成后在 FX 线程更新 ObservableList 并回调 onLoaded
- `importSkillFromZip(Path)`: 同步从 ZIP 导入技能（阻塞 FX 线程，仅用于兼容）
- `importSkillFromZipAsync(Path, Runnable)`: 异步从 ZIP 导入技能，使用 `javafx.concurrent.Task` 在后台线程执行 ZIP 解压和技能加载，完成后在 FX 线程更新 ObservableList 并回调 onLoaded
- `deleteSkill(String)`: 删除技能

### TaskPageViewModel
任务页视图模型，管理定时任务列表和数据格式化。

**Spring 注解：** `@Component`

**依赖注入：**
- `CronManager`: 定时任务管理器

**属性：**
- `tasks`: 任务列表（ObservableList<CronTaskInfo>）

**核心方法：**
- `loadTasks()`: 同步加载任务列表（阻塞 FX 线程，仅用于兼容）
- `loadTasksAsync(Runnable)`: 异步加载任务列表，使用 `javafx.concurrent.Task` + `ExecutorManager.platformTaskExecutor` 在后台线程执行 I/O，完成后在 FX 线程更新 ObservableList 并回调 onLoaded
- `getTasksGroupedBySessionId()`: 按 Session ID 分组任务
- `triggerTask(String, String)`: 触发任务
- `deleteTask(String, String)`: 删除任务
- `getTypeLabel(String)`: 获取任务类型标签（数据格式化）
- `getTaskConfig(CronTaskInfo)`: 获取任务配置描述（数据格式化）
- `truncateMessage(String)`: 截断消息（数据格式化）
- `formatCreateTime(Instant)`: 格式化创建时间（数据格式化）

### CanvasPageViewModel
画布视图模型，管理画布属性和元素操作。

**Spring 注解：** `@Component`

**依赖注入：**
- `SelectTool`: 选择工具

**属性：**
- `selectedStrokeColor`: 选中描边颜色（StringProperty）
- `selectedFillColor`: 选中填充颜色（StringProperty）
- `selectedStrokeWidth`: 选中线宽（DoubleProperty）
- `selectedRoughness`: 选中粗糙度（DoubleProperty）
- `selectedOpacity`: 选中透明度（DoubleProperty）
- `selectedLineStyle`: 选中线条样式（StringProperty）
- `selectedArrowStyle`: 选中箭头样式（StringProperty）
- `selectedCornerRadius`: 选中圆角半径（IntegerProperty）
- `selectedText`: 选中文本（StringProperty）
- `hasSelection`: 是否有选中元素（BooleanProperty）
- `currentToolName`: 当前工具名称（StringProperty）
- `zoomLevel`: 缩放级别（IntegerProperty）

**核心方法：**
- `syncSelectionProperties()`: 从选中元素同步属性到 ViewModel
- `applyPropertiesToSelection()`: 将 ViewModel 属性应用到选中元素
- `zoomIn()/zoomOut()/resetZoom()`: 缩放控制
- `getToolByName(String)`: 按名称获取工具实例

### GepPageViewModel
基因进化管理页视图模型，管理进化系统数据和统计。

**Spring 注解：** `@Component`

**依赖注入：**
- `GeneStore`: 基因存储
- `EvolutionEngine`: 进化引擎
- `EvolveConfig`: 进化配置
- `RoutingEngine`: 路由引擎
- `MemoryEngine`: 记忆引擎

**属性：**
- `genes`: 基因列表（ObservableList<Gene>）
- `routes`: 路由列表（ObservableList<RoutingEntry>）
- `rules`: 记忆规则列表（ObservableList<MemoryRule>）
- `capsules`: 胶囊列表（ObservableList<Capsule>）
- `events`: 进化事件列表（ObservableList<EvolutionEvent>）
- `geneCount`: 基因总数（IntegerProperty）
- `enabledGeneCount`: 启用基因数（IntegerProperty）
- `eventCount`: 事件总数（IntegerProperty）
- `successRate`: 成功率（DoubleProperty）
- `routeCount`: 路由数（IntegerProperty）
- `ruleCount`: 规则数（IntegerProperty）
- `strategyPreset`: 当前策略（StringProperty）

**核心方法：**
- `loadData()`: 同步加载所有数据并更新统计
- `loadDataAsync(Runnable)`: 异步加载数据
- `toggleGene(String)`: 切换基因启用状态
- `deleteGene(String)`: 删除基因
- `setStrategyPreset(StrategyPreset)`: 设置进化策略
- `runEvolutionCycle()`: 执行进化周期
- `extractAndEvolve()`: 提取经验并进化
- `addRoute(pattern, geneId, weight)`: 添加路由
- `removeRoute(pattern)`: 删除路由
- `addRule(pattern, action, confidence)`: 添加记忆规则
- `deleteRule(ruleId)`: 删除记忆规则
- `deleteCapsule(capsuleId)`: 删除胶囊
- `getGeneHistory(geneId)`: 获取基因JGit版本历史
- `revertGene(geneId, commitHash)`: 回滚基因版本
- `getGeneCode(geneId)`: 获取基因可执行代码

## 与其他组件的关系

1. **Manager/Service 层**: ViewModel 调用 Manager/Service 进行数据操作
2. **Controller 层**: Controller 调用 ViewModel 方法，监听 ViewModel 属性变化
3. **Store**: ViewModel 可更新全局状态（如 statusText）
4. **EventBus**: HomePageViewModel 通过 session.getEventBus() 订阅智能体事件

## 设计模式
- MVVM 模式：分离视图和业务逻辑
- 响应式编程：使用 JavaFX 属性和 ObservableList
- 观察者模式：属性变化通知 Controller 更新 UI
- 工厂模式：通过 `getOrCreateForSource` 获取 Session
- 懒创建模式：HomePageViewModel 初始化时不创建 session，首次发送消息时才创建；点击"新会话"仅切换到初始态

## 注意事项
1. UI 更新必须在 JavaFX 应用线程执行
2. ViewModel 不持有任何 UI 引用（Node、Scene、Stage）
3. 属性绑定是双向的，注意循环更新
4. ObservableList 的变更会自动通知监听者
5. 新增 ViewModel 需遵循相同的职责划分原则
6. 涉及 I/O 的数据加载应使用 `xxxAsync(Runnable)` 异步方法，避免阻塞 FX 线程；同步方法仅保留用于兼容
7. 异步方法内部使用 `javafx.concurrent.Task` + `ExecutorManager.platformTaskExecutor`，`setOnSucceeded` 在 FX 线程执行，可安全更新 ObservableList
