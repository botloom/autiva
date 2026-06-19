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

**子控制器：**
- `buttonBarController`: 底部按钮栏
- `sideBarController`: 侧边栏
- `homePageController`: 主页
- `agentPageController`: 智能体页
- `settingsPageController`: 设置页
- `skillPageController`: 技能页
- `taskPageController`: 任务页

### HomePageController
主页控制器，实现聊天交互界面。

**Spring 注解：** `@Component`

**依赖注入：**
- `HomePageViewModel`: 视图模型（消息流处理、历史消息）
- `ToolUIBridge`: 工具UI桥接（直接操作JavaFX组件）
- `WindowManager`: 窗口管理器（打开画布弹窗）
- `AgentDefinitionManager`: 智能体定义管理器（智能体选择器）

**职责（仅 UI）：**
- 管理 ScrollPane + VBox 聊天容器
- 监听 ViewModel 的 ObservableList 变化，创建消息卡片组件
- 工具调用分组折叠：连续的 TOOL 类型消息自动归入 `ToolGroupCard`，非 TOOL 消息中断分组。通过 `currentToolGroup` 字段追踪当前活跃的工具分组，清除对话时重置
- 滚动到顶部加载更多历史消息：监听 ScrollPane vvalue 变化，当滚动到顶部时调用 `viewModel.loadMoreMessages(30)` 获取更早的消息，再调用 `viewModel.prependHistoricalMessages()` 在头部插入，ListChangeListener 自动创建卡片并保持滚动位置不变
- 处理发送输入框和发送按钮事件
- 管理停止按钮（流式生成时切换显示，点击暂停生成并保留部分响应，替代发送按钮）
- 管理模型选择 ComboBox
- 管理智能体选择按钮（agentSelector）：点击弹出 ContextMenu 选择智能体（从 AgentDefinitionManager 动态获取主智能体列表）
- 处理语音输入按钮
- 画布按钮：打开 CanvasDialog 弹窗
- 动画效果（图标淡出、聊天区域展开）
- 清除对话时的 UI 重置
- 自动滚动到底部：通过 `shouldScrollToBottom` 标志 + `PostLayoutPulseListener` 实现延迟滚动。AssistantMessageCard 和 TaskCard 均通过 `onContentChanged` 回调在内容变化时通知 `scrollToBottom()`，确保流式输出期间 ScrollPane 自动向下滚动
- 防止 ScrollPane 焦点导致布局偏移：`chatScrollPane.setFocusTraversable(false)`，阻止 ScrollPane 获得焦点
- 防止 TextFlow 重新换行导致布局偏移：`fitToWidth="false"` + `chatContainer.prefWidthProperty().bind(chatScrollPane.widthProperty())`。根因：`fitToWidth=true` 使内容宽度跟随 viewport 宽度，而 viewport 宽度在垂直滚动条出现/消失时会变化（vbarPolicy=AS_NEEDED），导致 TextFlow 重新换行产生"缩进"效果。改为手动绑定 ScrollPane 整体宽度（稳定不变），彻底切断 viewport 宽度变化对内容的影响

**ViewModel 委托：**
- `viewModel.sendMessage()` - 发送消息
- `viewModel.pauseGeneration()` - 暂停生成（终止按钮触发）
- `viewModel.clear()` - 清除对话
- `viewModel.addUserMessage(String)` - 添加用户消息卡片到列表
- `viewModel.prepareHistoricalMessages()` - 准备历史消息
- `viewModel.loadMoreMessages(int)` - 加载更多历史消息（滚动到顶部时触发）
- `viewModel.prependHistoricalMessages(List<MessageCard>)` - 在头部插入历史卡片
- `viewModel.hasMoreMessages()` - 是否还有更多历史消息
- `viewModel.getMessages()` - 监听消息列表变化
- `viewModel.createNewSession()` - 创建新会话（SideBarController 调用）
- `viewModel.switchToSession()` - 切换会话（SideBarController 调用）
- `viewModel.getAgentProperty()` - 获取当前智能体属性

### AgentPageController
智能体配置页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `AgentPageViewModel`: 视图模型

**职责（仅 UI）：**
- 渲染智能体卡片（TitledPane）
- 通过系统文件管理器打开配置目录（`Desktop.getDesktop().open()`）
- 删除确认弹窗

**ViewModel 委托：**
- `viewModel.loadAgents()` - 加载智能体列表
- `viewModel.getMainAgents()` - 获取主智能体列表
- `viewModel.getSubagents()` - 获取子智能体列表
- `viewModel.readFileContent()` - 读取文件内容
- `viewModel.readSubagentContent()` - 读取子智能体内容
- `viewModel.saveFile()` - 保存文件
- `viewModel.saveSubagentConfig()` - 保存子智能体配置
- `viewModel.deleteSubagent()` - 删除子智能体

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
- 历史对话列表：加载和渲染桌面端 session 列表，点击切换 session
- 当前活跃对话高亮显示
- 监听 currentSessionId 变化自动刷新历史列表
- 监听 Store.refreshHistory 信号刷新历史列表（聊天过程中更新会话标题）

**FXML 字段：**
- `historyScrollPane`: 历史对话滚动面板
- `historyList`: 历史对话列表容器（VBox）

**核心方法：**
- `refreshHistoryList()`: 增量刷新历史对话列表（使用 historyItemMap 缓存，仅更新变化的项，避免全量重建）
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
