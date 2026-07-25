# Controller 包

## 概述
本包实现了 JavaFX 控制器，遵循 **MVVM 架构**，Controller 只负责 UI 控制和渲染，数据逻辑由对应的 ViewModel 处理。

## MVVM 职责划分

### Controller 职责
- FXML 绑定和 UI 初始化
- UI 事件处理（委托给 ViewModel）
- UI 组件动态创建（卡片、列表项等）
- 动画和视觉效果
- 页面显示/隐藏控制
- **不直接调用 Manager/Service 进行数据操作**

### ViewModel 职责
- 管理视图状态（JavaFX Properties / ObservableList）
- 业务逻辑处理（数据加载、转换、持久化）
- 调用 Service/Manager 层
- 数据格式化（时间格式化、类型标签转换等）
- **不持有任何 UI 引用**（Node、Scene、Stage）

## Controller 与 ViewModel 对应关系

| Controller | ViewModel | 说明 |
|---|---|---|
| HomePageController | HomePageViewModel | 聊天交互 |
| AgentPageController | AgentPageViewModel | 智能体管理 |
| SettingsPageController | SettingsPageViewModel | 配置管理 |
| SkillPageController | SkillPageViewModel | 技能管理 |
| TaskPageController | TaskPageViewModel | 定时任务管理 |
| CanvasDialogController | CanvasPageViewModel | 画布弹窗（绘图交互） |
| IndexController | 无 | 纯导航协调 |

## 核心控制器

### IndexController
主控制器，管理整个应用的布局和子控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `Router`: 使用 `@Lazy` 注解解决循环依赖，通过构造器注入

**职责：**
- 初始化所有子控制器
- 管理路由导航
- 控制侧边栏显示/隐藏
- 协调编辑器面板操作
- 监听 `Store.currentAgent` 变化触发联动：1) 重建 ButtonBar 按钮（按智能体类型过滤 terminal/project 按钮）；2) 切换到非 coder 时若 EditorPanel 正显示 TERMINAL/PROJECT 视图则自动关闭（SideBar 历史列表刷新由 SideBarController 自行监听触发）

**子控制器：**
- `buttonBarController`: 底部按钮栏
- `sideBarController`: 侧边栏
- `homePageController`: 主页
- `agentPageController`: 智能体页
- `settingsPageController`: 设置页
- `skillPageController`: 技能页
- `taskPageController`: 任务页
- `editorPanelController`: 编辑器面板（文件树、终端、文件内容、diff 渲染、工具调用、待办）

**编辑器面板协调方法：**
- `toggleTerminalPanel()`: 切换终端面板（toggle 行为：相同视图再次点击则关闭，否则打开/切换到终端视图）
- `toggleProjectPanel()`: 切换项目面板（toggle 行为：相同视图再次点击则关闭，否则打开/切换到项目视图）
- `toggleToolCallsPanel()`: 切换工具调用面板（toggle 行为：相同视图再次点击则关闭，否则打开/切换到工具调用视图）
- `toggleTodoPanel()`: 切换待办面板（toggle 行为：相同视图再次点击则关闭，否则打开/切换到待办视图）
- `closeEditorPanel()`: 关闭编辑器面板（保存 divider 位置并从 SplitPane 移除）
- `closeTerminal()`: 关闭终端会话
- `showFileInPanel(Path)`: 在编辑器面板显示文件内容
- `showDiffInProjectView(FileDiff)`: 在项目视图右侧内容区渲染 diff（单栏行内高亮，类似 IDEA in-editor diff）
- `updateCurrentProject(ProjectInfo)`: 通知编辑器面板更新当前项目（构建目录树）
- `addTextToChat(String)`: 将选中文本追加到对话框输入框（编辑器面板选择联动）
- `addFileToChat(File)`: 将文件添加到对话框附件（编辑器面板拖拽联动）
- `resolveWorkingDir()`: 解析当前工作目录（当前项目路径或 null）

**布局管理：** 主区域与编辑器面板通过 `SplitPane` (`mainSplit`) 组织，支持拖拽调整大小。编辑器面板默认从 SplitPane 移除（隐藏），打开时通过 `ensureEditorVisible()` 添加回 SplitPane 并恢复 divider 位置。

### HomePageController
主页控制器，实现聊天交互界面。

**Spring 注解：** `@Component`

**依赖注入：**
- `HomePageViewModel`: 视图模型（消息流处理、历史消息）
- `ToolUIBridge`: 工具UI桥接（直接操作JavaFX组件）
- `WindowManager`: 窗口管理器（打开画布弹窗）

**职责（仅 UI）：**
- 管理 ScrollPane + VBox 聊天容器
- 监听 ViewModel 的 ObservableList 变化，创建消息卡片组件
- 工具调用重定向：ToolMessageCard 不在聊天消息区域展示，而是通过 `addChatCard` 拦截后重定向到 EditorPanel 的工具调用视图（`addToolCallCard`），直接添加到 `toolCallsContainer`（不再通过 ToolGroupCard 分组），减少聊天区域卡片渲染压力
- TodoCard 重定向：TodoCard 不在聊天消息区域展示，而是通过 `addChatNode` 拦截后重定向到 EditorPanel 的待办视图（`addTodoCard`）
- TaskCard 和 QuestionCard 仍在消息区域展示
- 处理发送输入框和发送按钮事件
- sendField 使用 AutoResizeTextArea 自定义组件（重写 computePrefHeight 按实际渲染高度计算），无需手动调整高度；原 adjustTextAreaHeight() 方法已置空保留
- 文件标签容器（fileTagsPane）包装在 ScrollPane（fileTagsScroll）中，限制最大高度 96px 并支持垂直滚动，防止数十个文件标签挤出按钮区；updateFileTagsPaneVisibility() 控制 fileTagsScroll 的 visible/managed
- **diff 审查卡片条（diffReviewBar）**：位于 `chatScrollPane` 与 `sendBox` 之间的 StackPane 叠加层（StackPane.alignment=BOTTOM_CENTER），订阅 DiffEvent 后**直接使用事件中的 FileDiff 数据**追加卡片。可折叠卡片，左侧显示"N 个文件待审查"，右侧有"展开"/"全部撤销"/"全部保留"按钮。展开时向上覆盖聊天区显示文件列表（每个文件行：文件名 + "+N -M" 统计 + 单个文件撤销/保留按钮），点击文件行调用 `indexController.showDiffInProjectView(diff)` 在项目视图右侧渲染左右对比 diff。数据源为 WriteTool/EditTool 调用 `generateDiff` 发布的 DiffEvent，不再扫描 git 工作区（`scanWorkingTreeDiffs` 仅用于进化功能）。同一文件多次修改追加独立卡片，保留完整修改历史。监听 `Store.currentSessionId` 变化，切换会话/新建会话时清空 diff 列表（仅当前会话的 diff 显示）
- 管理停止按钮（流式生成时切换显示，点击暂停生成并保留部分响应，替代发送按钮）
- 默认使用 DeepSeek 模型，无需手动选择
- 智能体切换入口已移至侧边栏分段按钮（SideBar 顶部 modeSwitcher），HomePage 不再持有 agentSelector；HomePageController 仅监听 `Store.currentAgent` 变化以同步 `projectSelectButton`/`branchDisplayButton` 的可见性（coder 模式才显示）
- `getButtonConfigs()` 根据当前智能体类型过滤 ButtonBar 按钮：default 模式只返回"新对话+工具+待办"3 个按钮；coder 模式返回全部 5 个按钮（含终端/项目按钮）
- 处理语音输入按钮
- 画布按钮：打开 CanvasDialog 弹窗
- 动画效果（图标淡出、聊天区域展开）
- 清除对话时的 UI 重置（同时清空 diff 文件卡片条）
- 自动滚动到底部：通过 `shouldScrollToBottom` 标志 + `PostLayoutPulseListener` 实现延迟滚动。AssistantMessageCard 和 TaskCard 均通过 `onContentChanged` 回调在内容变化时通知 `scrollToBottom()`，确保流式输出期间 ScrollPane 自动向下滚动
- 防止 ScrollPane 焦点导致布局偏移：`chatScrollPane.setFocusTraversable(false)`，阻止 ScrollPane 获得焦点
- 防止 TextFlow 重新换行导致布局偏移：`fitToWidth="false"` + `chatContainer.prefWidthProperty().bind(chatScrollPane.widthProperty())`。根因：`fitToWidth=true` 使内容宽度跟随 viewport 宽度，而 viewport 宽度在垂直滚动条出现/消失时会变化（vbarPolicy=AS_NEEDED），导致 TextFlow 重新换行产生"缩进"效果。改为手动绑定 ScrollPane 整体宽度（稳定不变），彻底切断 viewport 宽度变化对内容的影响

**ViewModel 委托：**
- `viewModel.sendMessage()` - 发送消息
- `viewModel.pauseGeneration()` - 暂停生成（终止按钮触发）
- `viewModel.clear()` - 清除对话
- `viewModel.addUserMessage(String)` - 添加用户消息卡片到列表
- `viewModel.prepareHistoricalMessages()` - 准备历史消息（从 events.jsonl 加载所有未压缩事件）
- `viewModel.getMessages()` - 监听消息列表变化
- `viewModel.createNewSession()` - 创建新会话（SideBarController 调用）
- `viewModel.switchToSession()` - 切换会话（SideBarController 调用）
- `viewModel.getAgentProperty()` - 获取当前智能体属性
- `viewModel.setCurrentProject(ProjectInfo)` - 设置当前编码项目
- `viewModel.createNewProject(String name)` - 新建项目并设为当前
- `viewModel.registerLocalProject(String path, String name)` - 注册本地文件夹并设为当前
- `viewModel.listProjects()` - 列出所有注册项目

**编辑器面板联动方法：**
- `appendTextToChat(String text)`: 将选中文本追加到输入框末尾（已有内容时换行分隔），由 IndexController.addTextToChat 转发调用。仅追加文本和聚焦输入框，不触发界面状态切换动画
- `addAttachedFile(File file)`: 添加附件文件（去重处理），复用 attachedFiles 机制，由 IndexController.addFileToChat 转发调用或拖拽释放时直接调用
- `setupDragDrop()`: 将 sendBox 注册为拖拽目标，接收来自文件树/Diff 列表的文件拖拽
- `handleDragOver(DragEvent)`: 拖拽悬停时接受 COPY 传输模式
- `handleDragDropped(DragEvent)`: 拖拽释放时将文件添加为附件（仅添加附件，不触发界面状态切换动画）
- `updateSelectorLockState(boolean locked)`: 根据是否已有对话消息锁定/解锁项目选择按钮（projectSelectButton）。一个 session 只能绑定一个智能体和一个项目，有了对话后不可修改；消息列表清空（新建会话/清除对话）后自动解锁。智能体切换入口已移至侧边栏分段按钮（不受此锁定影响，切换会创建新会话）。在消息列表 ListChangeListener 中根据 `!messages.isEmpty()` 调用

### AgentPageController
智能体配置页控制器，实现 Initializable、ButtonBarHolder、PageHolder。

**Spring 注解：** `@Component`

**依赖注入：**
- `AgentPageViewModel`: 视图模型
- `WindowManager`: 窗口管理器（打开配置编辑器对话框）

**职责（仅 UI）：**
- 渲染智能体卡片列表（ScrollPane + VBox，与 SkillPage 风格一致）
- 每个卡片包含：名称、描述、操作按钮（打开目录/复制/删除）、配置文件列表
- 新建智能体：弹出 TextInputDialog 输入名称
- 删除智能体：确认弹窗后删除
- 复制智能体：弹出 TextInputDialog 输入新名称
- 打开目录：调用系统文件管理器
- 编辑文件：通过 WindowManager 打开 AgentConfigEditorDialog

**ViewModel 委托：**
- `viewModel.loadAgentsAsync()` - 异步加载智能体列表
- `viewModel.getMainAgents()` - 获取主智能体列表
- `viewModel.readFileContent()` - 读取文件内容
- `viewModel.createAgent()` - 创建新智能体
- `viewModel.deleteAgent()` - 删除智能体
- `viewModel.copyAgent()` - 复制智能体
- `viewModel.openAgentDirectory()` - 打开智能体目录
- `viewModel.agentExists()` - 检查智能体是否存在

**按钮栏配置：**
- "新建智能体" 按钮

### AgentConfigEditorDialogController
智能体配置文件编辑器对话框控制器，实现 WindowManager.StageAware、DialogHolder、Initializable。

**Spring 注解：** `@Component`

**DialogHolder 配置：**
- 宽 700、高 500、可调整大小（最小 500x350）

**职责：**
- 初始化编辑器内容（文件内容）
- 保存文件内容到磁盘
- 取消关闭对话框

### AgentInputDialogController
智能体输入对话框控制器，实现 WindowManager.StageAware、DialogHolder、Initializable。

**Spring 注解：** `@Component`

**DialogHolder 配置：**
- 宽 400、高 180

**职责：**
- 初始化提示信息和默认值
- 确认时通过 `Consumer<String>` 回调返回输入值
- Enter 键确认，空值不响应

### AgentConfirmDialogController
智能体确认对话框控制器，实现 WindowManager.StageAware、DialogHolder、Initializable。

**Spring 注解：** `@Component`

**DialogHolder 配置：**
- 宽 380、高 180

**职责：**
- 初始化标题和提示信息
- 确认/取消时通过 `Consumer<Boolean>` 回调返回结果

### SettingsPageController
设置页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `SettingsPageViewModel`: 视图模型
- `ApplicationContext`: 获取 WechatILinkClient Bean

**职责（仅 UI）：**
- 双向绑定 ViewModel 属性到 UI 字段
- 监听 WechatILinkClient 状态变化，更新微信扫码区域 UI
- 渲染二维码图片（ZXing）
- 处理重新绑定按钮事件（调用 `client.restartLogin()`）
- 处理刷新二维码按钮事件（调用 `client.startLogin()`）

**微信扫码区域 UI 状态（StackPane 覆盖层设计）：**
- `CONNECTED`：显示已连接覆盖层（"重新加载"按钮），提示文案"微信已绑定，点击重新加载可更换账号"
- `CONNECTING`：隐藏所有覆盖层，提示文案显示"连接中..."
- `DISCONNECTED`：隐藏所有覆盖层，显示二维码，提示文案"扫码即可绑定微信"
- `QR_EXPIRED`：显示过期覆盖层（"重新加载"按钮），提示文案"二维码已过期，请点击重新加载"
- 未启用：隐藏整个微信扫码区域

**ViewModel 委托：**
- `viewModel.loadFromStore()` - 加载配置
- `viewModel.save()` - 保存配置
- `viewModel.reset()` - 重置配置

### SkillPageController
技能页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `SkillPageViewModel`: 视图模型

**职责（仅 UI）：**
- 渲染技能卡片
- 通过系统文件管理器打开技能目录（`Desktop.getDesktop().open()`）
- ZIP 导入文件选择器

**ViewModel 委托：**
- `viewModel.loadSkills()` - 加载技能列表
- `viewModel.getSkills()` - 获取技能列表
- `viewModel.importSkillFromZip()` - 导入技能
- `viewModel.deleteSkill()` - 删除技能

### TaskPageController
任务页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `TaskPageViewModel`: 视图模型

**职责（仅 UI）：**
- 渲染任务卡片（按 Session 分组）
- TitledPane 折叠展示

**ViewModel 委托：**
- `viewModel.loadTasks()` - 加载任务列表
- `viewModel.getTasks()` - 获取任务列表
- `viewModel.getTasksGroupedBySessionId()` - 按 Session 分组
- `viewModel.triggerTask()` - 触发任务
- `viewModel.deleteTask()` - 删除任务
- `viewModel.getTypeLabel()` - 获取类型标签
- `viewModel.getTaskConfig()` - 获取任务配置描述
- `viewModel.truncateMessage()` - 截断消息
- `viewModel.formatCreateTime()` - 格式化创建时间

### GepPageController
基因进化管理页控制器（窄列卡片列表风格，对齐 AgentPage 设计规范）。阶段4 改造为 L4 自优化统一入口。

**Spring 注解：** `@Component`

**依赖注入：**
- `GepPageViewModel`: 视图模型（MVVM）

**职责（仅 UI）：**
- 渲染概览卡片（基因数/启用数/各类型计数/事件数/事件成功率）
- 渲染 L4 爬山自优化卡片（描述 + "分析优化"按钮 + 分析摘要 + 可展开的 L4 分析报告详情，阶段4 新增）
- 渲染 L2 校验报告卡片（Trace 总数/通过/失败/通过率/工具调用/阻断，阶段4 新增）
- 渲染最近 Trace 卡片（最近 10 条 Trace：状态标签/时间/工具调用数/阻断数/用户消息摘要，阶段4 新增）
- 渲染可展开基因卡片（收起：标题+描述+标签+展开按钮；展开：配置内容/版本历史/父版本/时间/操作按钮）
- 渲染进化事件卡片（行列表）
- 基因启用/禁用/删除操作
- 版本回滚确认

**FXML 字段：**
- `gepPage`: 页面根容器
- `contentContainer`: 内容容器（动态填充）

**ViewModel 委托：**
- `viewModel.loadData()` - 加载所有数据（Gene/Event/Trace/统计）
- `viewModel.climbAsync(Runnable)` - 触发 L4 爬山分析（阶段4 新增）
- `viewModel.toggleGene(id)` - 切换基因启用状态
- `viewModel.deleteGene(id)` - 删除基因
- `viewModel.getGeneHistory(geneId)` - 获取基因版本历史
- `viewModel.revertGene(geneId, commitHash)` - 回滚基因版本

**L4 分析按钮交互：**
- 点击"分析优化" → 按钮置灰显示"分析中..." → 异步调用 `viewModel.climbAsync()` → 完成后按钮恢复 + `refreshContent()` 重新渲染
- 分析结果展示在按钮下方：摘要 Label + 可折叠 TitledPane（含完整 Markdown 报告 TextFlow）

### SideBarController
侧边栏控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `SessionManager`: 会话管理器
- `HomePageViewModel`: 主页视图模型

**职责：**
- 导航到不同页面
- 更新当前选中状态
- 显示/隐藏侧边栏
- "新聊天"按钮：创建新 session 并导航到首页
- 历史对话列表：加载和渲染桌面端 session 列表，**按当前智能体 `Store.currentAgent` 过滤**（只显示该智能体的 session），点击切换 session
- 当前活跃对话高亮显示
- 监听 currentSessionId 变化自动刷新历史列表
- 监听 `Store.currentAgent` 变化自动刷新历史列表（按新智能体过滤）
- 监听 Store.refreshHistory 信号刷新历史列表（聊天过程中更新会话标题）
- **智能体模式分段切换按钮（栏首 modeSwitcher）**：iOS SegmentedControl 风格，两个 ToggleButton（Work/Code）放入同一 ToggleGroup 互斥，点击调用 `homePageViewModel.switchAgent(agentId)`；监听 `Store.currentAgent` 变化同步选中态（切换历史会话时触发）

**FXML 字段：**
- `modeSwitcher`: 智能体分段切换容器（HBox，栏首）
- `defaultModeBtn`: "Work" 段 ToggleButton（work.svg 图标）
- `coderModeBtn`: "Code" 段 ToggleButton（code.svg 图标）
- `historyScrollPane`: 历史对话滚动面板
- `historyList`: 历史对话列表容器（VBox）

**核心方法：**
- `refreshHistoryList()`: 增量刷新历史对话列表（使用 historyItemMap 缓存，仅更新变化的项，避免全量重建）；按 `Store.currentAgent.get()` 过滤 session（`s.getAgentId().equals(currentAgent)`）
- `createHistoryItem(Session)`: 创建历史对话项 UI
- `updateHistoryItemTitle(HBox, Session)`: 更新历史对话项标题（仅当标题为默认值"新对话"时，取第一条用户消息截断前20字符作为标题，并通过 updateSession 持久化到 Session）
- `updateHistoryActiveState(HBox)`: 更新历史对话选中状态
- `formatTime(long)`: 格式化时间戳（今天显示 HH:mm，其他显示 MM/dd HH:mm）

**设计优化：**
- 使用 `routeOptionMap`（Map<String, HBox>）替代 if-else 链管理路由选项
- CSS 类名 `sidebar__option--active` 提取为常量 `ACTIVE_CSS_CLASS`
- CSS 类名 `sidebar__history-item--active` 提取为常量 `HISTORY_ACTIVE_CSS_CLASS`
- 鼠标点击事件通过 Map 遍历统一注册
- `historyItemMap`（Map<String, HBox>）缓存历史项，增量更新避免全量重建

### ButtonBarController
底部按钮栏控制器。

**职责：**
- 显示状态信息
- 动态更新按钮
- 状态变化动画
- 侧边栏切换按钮：使用 panel-left.svg 图标，点击调用 `indexController.toggleSidebar()`
- 项目选择按钮：coder 智能体时显示，MenuButton 下拉菜单（选择文件夹+最近项目）
- 分支显示按钮：coder 智能体时显示，默认只显示 git-branch.svg 图标，选择项目后显示分支名（disabled）

**FXML 字段：**
- `sidebarButton`: 侧边栏切换按钮，使用 panel-left.svg 图标
- `dynamicButtonContainer`: 动态按钮容器（左对齐按钮）
- `rightButtonContainer`: 右侧动态按钮容器（右对齐按钮）

**按钮配置来源：**
各页面控制器通过实现 `ButtonBarHolder.getButtonConfigs()` 提供动态按钮配置，由 ButtonBarController 注入到 `dynamicButtonContainer`（Alignment.LEFT）或 `rightButtonContainer`（Alignment.RIGHT）。HomePageController 根据当前智能体类型返回不同按钮集合：default 模式 3 个按钮（"新对话"+工具+待办），coder 模式 5 个按钮（"新对话"+终端/项目/工具/待办）。切换智能体时由 IndexController 监听 `Store.currentAgent` 触发 `router.updateButtonBarForRoute()` 重建按钮。

**核心方法：**
- `setupProjectBinding()`: 设置项目绑定（由 IndexController 在初始化完成后调用），监听 currentProject 变化更新分支显示和通知 IndexController
- `updateProjectButtonsVisibility(String)`: 根据智能体 ID 控制项目按钮显示/隐藏
- `setupProjectMenu()`: 设置项目选择下拉菜单（选择文件夹+最近项目列表）
- `handleOpenLocalFolder()`: 打开 DirectoryChooser 选择本地文件夹
- `refreshProjectMenu()`: 刷新项目下拉菜单内容
- `refreshProjectMenuText(ProjectInfo)`: 刷新项目选择按钮显示文本
- `refreshBranchDisplay(ProjectInfo)`: 更新分支按钮文本（默认空，选择项目后显示分支名）

### EditorPanelController
统一编辑器面板控制器，通过 StackPane 管理四个视图：终端、项目（文件树+文件内容/diff 渲染）、工具调用、待办。实现 Initializable。Diff 不再独立成视图，而是注入到项目视图右侧内容区（单栏行内高亮，类似 IDEA in-editor diff）。面板无关闭按钮栏，由 ButtonBar 的"终端/项目/工具/待办"按钮 toggle 切换。

**Spring 注解：** `@Component`

**依赖注入：**
- `FileTreeService`: 文件树构建服务
- `PtyTerminalService`: PTY 终端服务
- `DiffService`: Diff 管理服务

**职责：**
- 管理四视图切换（终端/项目/工具调用/待办），通过 StackPane + visible/managed 控制
- 追踪当前视图类型（`currentViewType`），供 IndexController 的 toggle 判断使用
- 管理项目目录树展示（双击文件在项目视图右侧显示文件内容，文件树用 `FileTreeCell` 渲染图标和样式）
- 管理终端（使用 JediTerminalView，异步启动，加载状态，错误重试）
- 管理文件内容显示与编辑（RichTextFX CodeArea 可编辑 + 行号 + `SyntaxHighlighterFactory.forPath` 注入语法高亮 + Ctrl+S 保存）
- 管理 diff 渲染（注入到项目视图右侧 fileContentPanel，CodeArea 单栏行内高亮 + 顶部悬浮横幅 + 撤销/保留按钮）

**内部枚举：**
- `ViewType.TERMINAL` / `ViewType.PROJECT` / `ViewType.TOOL_CALLS` / `ViewType.TODO`: 四种视图类型，由 `currentViewType` 字段追踪

**FXML 字段：**
- `editorPanel`: 编辑器面板根容器（VBox，透明背景 + padding 8 8 8 0 留白），默认 visible=false managed=false
- `viewContainer`: 视图容器（StackPane）
- `terminalView`: 终端视图容器（VBox）
- `projectSplit`: 项目视图（SplitPane，左侧文件树 + 右侧文件内容/diff 渲染区）
- `fileTree`: 项目文件树（TreeView<Path>）
- `fileContentPanel`: 文件内容面板（VBox，同时承载 diff 渲染）
- `fileContentPlaceholder`: 文件内容占位符（Label）
- `toolCallsView`: 工具调用视图容器（VBox + ScrollPane）
- `toolCallsContainer`: 工具调用卡片容器（VBox）
- `todoView`: 待办视图容器（VBox + ScrollPane）
- `todoContainer`: 待办卡片容器（VBox）

**核心方法：**
- `show()/hide()/isVisible()`: 控制面板显隐
- `setupRoundedClip()`: 给 viewContainer 设置 Rectangle clip（arcWidth/arcHeight=24），裁剪终端/项目/工具调用/待办四视图的方角到 12px 圆角形状
- `setupFileTree()`: 设置文件树，注入 `FileTreeCell` 工厂，绑定双击事件
- `setupTerminalContextMenu(JediTerminalView)`: 为终端设置右键菜单（"添加到对话框"），使用 `setOnContextMenuRequested` + `ContextMenu.show()` 实现（JediTerminalView 不是 Control，无法使用 setContextMenu）
- `setupCodeAreaContextMenu(CodeArea)`: 为文件内容/diff CodeArea 设置右键菜单（"添加到对话框"），使用 `setContextMenu`
- `showTerminalView()`: 切换到终端视图，设置 currentViewType=TERMINAL
- `showProjectView()`: 切换到项目视图，设置 currentViewType=PROJECT
- `showToolCallsView()`: 切换到工具调用视图，设置 currentViewType=TOOL_CALLS
- `showTodoView()`: 切换到待办视图，设置 currentViewType=TODO
- `addToolCallCard(Node)`: 添加工具调用卡片到工具调用视图（直接添加到 `toolCallsContainer`，不再通过 ToolGroupCard 分组）
- `addTodoCard(Node)`: 添加待办卡片到待办视图
- `clearToolCalls()`: 清空工具调用卡片
- `clearTodos()`: 清空待办卡片
- `getCurrentViewType()`: 获取当前视图类型（供 IndexController toggle 判断）
- `setCurrentProject(ProjectInfo)`: 设置当前项目，构建目录树
- `openTerminal(Path)`: 打开终端，异步启动 JediTerminalView
- `ensureTerminalStarted(Path)`: 确保终端已启动，若未启动则异步创建
- `closeTerminal()`: 关闭终端会话
- `showFileContent(Path)`: 在项目视图右侧显示文件内容（CodeArea + 行号 + `SyntaxHighlighterFactory.forPath` 应用语法高亮）。**支持编辑**（`setEditable(true)` + `setShowCaret(ON)` 显示光标），Ctrl+S 保存文件内容到磁盘（调用 `saveFileContent`），显示后 `requestFocus()` 聚焦以显示光标
- `saveFileContent(Path, String)`: 保存文件内容到磁盘（`Files.writeString`），Ctrl+S 时由 `showFileContent` 的按键监听器调用
- `showDiffInProjectView(FileDiff)`: 切换到项目视图，调用 `renderDiffIntoPanel(diff, fileContentPanel)` 在右侧内容区渲染 diff
- `renderDiffIntoPanel(FileDiff, VBox)`: 直接使用 DiffEvent 中的原始 FileDiff 渲染**左右双栏对比视图**（不调用 `recomputeDiff`，diff 显示工具调用时的快照）：SplitPane 左侧 CodeArea 显示旧版本内容（REMOVE 行 `.diff-line-remove-left` 红色背景 + 旧行号），右侧 CodeArea 显示新版本内容（ADD 行 `.diff-line-add-right` 绿色背景 + 新行号）；两侧段落按 hunk 对齐（ADD 行在左侧留空行，REMOVE 行在右侧留空行）；两侧分别应用 `SyntaxHighlighterFactory.forPath` 语法高亮（字符级样式与段落级背景样式叠加）；左右 CodeArea `estimatedScrollYProperty` 双向监听 + 标志位防止循环，实现同步滚动；StackPane 叠加 SplitPane + 顶部悬浮横幅（文件名 + 撤销/保留）
- `createDiffBanner(FileDiff)`: 创建顶部悬浮横幅（文件名 + spacer + 撤销/保留按钮），点击按钮调用 `diffService.rejectFileDiff/approveFileDiff` 并恢复项目视图占位符
- `computeDiffStats(FileDiff)`: 静态方法，遍历 hunks/lines 统计 ADD/REMOVE 行数，返回 int[2]
- `createLoadingContent(String)`: 创建加载状态内容（ProgressIndicator + 文本）
- `createErrorContent(String, Runnable)`: 创建错误状态内容（带重试按钮）

**交互逻辑：**
- 四视图通过 StackPane 的 visible/managed 切换，单一视图模式
- 面板无 header 栏，通过 ButtonBar 的四个按钮 toggle 切换（相同视图再次点击则关闭面板）
- 终端会话持久化：切换视图或关闭面板时终端会话保持（JediTerminalView 节点不销毁）
- 文件内容/diff 渲染均注入到 fileContentPanel，替换占位符
- diff 审核按钮点击后恢复项目视图占位符
- 终端启动使用独立线程，避免阻塞 UI

### ProjectPickerDialogController
项目选择对话框控制器，实现 Initializable、WindowManager.StageAware、DialogHolder。

**Spring 注解：** `@Component`

**依赖注入：**
- `HomePageViewModel`: 视图模型（项目列表数据）

**职责：**
- 显示已注册项目列表
- 新建项目：弹出 TextInputDialog 输入项目名
- 打开本地文件夹：弹出 DirectoryChooser 选择目录
- 确认选择后通过 `Consumer<ProjectInfo>` 回调返回选中项目
- 取消关闭对话框

**DialogHolder 配置：**
- 宽 500、高 400、可调整大小

**核心方法：**
- `setOnProjectSelected(Consumer<ProjectInfo>)`: 设置项目选择回调
- `handleNewProject()`: 新建项目并选中
- `handleOpenLocal()`: 打开本地文件夹并注册为项目
- `handleConfirm()`: 确认选择
- `handleCancel()`: 取消

## 对话框控制器

### CanvasDialogController
画布弹窗控制器，实现 Initializable、StageAware、DialogHolder。

**Spring 注解：** `@Component`

**依赖注入：**
- `CanvasPageViewModel`: 视图模型（画布属性管理、元素操作）

**职责：**
- 管理悬浮工具栏切换（8个绘图工具）
- 管理工具专属属性面板（按工具动态显示/隐藏属性行）
- 管理图层面板
- 双击编辑文字
- 填充颜色色块：透明背景使用 CSS linear-gradient 实现斜线效果（替代 SVG data URI，因 JavaFX CSS 不支持 SVG）

**ViewModel 委托：**
- `viewModel.applyPropertiesToSelection()` - 应用属性到选中元素
- `viewModel.syncSelectionProperties()` - 从选中元素同步属性

### BrowserDialogController
浏览器对话框控制器。

**职责：**
- 显示网页内容
- 导航控制（前进、后退、刷新）
- URL 输入和加载

## 接口

### PageHolder
页面持有者接口，定义页面的显示/隐藏行为。

```java
public interface PageHolder {
    void show();
    void hide();
}
```

### ButtonBarHolder
按钮栏持有者接口，定义页面的按钮配置。

```java
public interface ButtonBarHolder {
    List<ButtonConfig> getButtonConfigs();
    
    record ButtonConfig(String id, String text, String styleClass, 
                        EventHandler<ActionEvent> actionHandler) {}
}
```

## 设计模式
- MVVM 模式：Controller 负责 UI，ViewModel 负责数据和业务逻辑
- 组合模式：IndexController 管理子控制器
- 策略模式：不同页面有不同的按钮配置（ButtonBarHolder）
- 观察者模式：Controller 监听 ViewModel 属性变化更新 UI
- 双向绑定：SettingsPageController 与 SettingsPageViewModel 属性双向绑定

## 注意事项
1. 控制器使用 @Component 注解，由 Spring 管理
2. FXML 字段使用 @FXML 注解
3. 初始化逻辑在 initialize() 方法中
4. UI 操作需在 JavaFX 应用线程执行
5. Controller 不直接调用 Manager/Service，通过 ViewModel 间接调用
6. ViewModel 不持有任何 UI 引用（Node、Scene、Stage）
