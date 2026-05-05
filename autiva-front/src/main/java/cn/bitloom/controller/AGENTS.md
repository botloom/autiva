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
| McpPageController | McpPageViewModel | MCP 服务器管理 |
| TaskPageController | TaskPageViewModel | 定时任务管理 |
| IndexController | 无 | 纯导航协调 |
| SideBarController | 无 | 纯 UI 导航 |
| ButtonBarController | 无 | 纯 UI 渲染 |
| FileEditorController | 无 | 文件操作与 UI 紧耦合 |

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
- `mcpPageController`: MCP 页
- `taskPageController`: 任务页

### HomePageController
主页控制器，实现聊天交互界面。

**Spring 注解：** `@Component`

**依赖注入：**
- `HomePageViewModel`: 视图模型（消息流处理、历史消息）
- `SpeechRecognitionService`: 语音识别服务
- `ToolUIBridge`: 工具UI桥接（直接操作JavaFX组件）

**职责（仅 UI）：**
- 管理 ScrollPane + VBox 聊天容器
- 监听 ViewModel 的 ObservableList 变化，创建消息卡片组件
- 处理搜索输入框和发送按钮事件
- 管理停止按钮（流式生成时切换显示，替代发送按钮）
- 管理模型选择 ComboBox
- 处理语音输入按钮
- 动画效果（图标淡出、聊天区域展开）
- 清除对话时的 UI 重置
- 自动滚动到底部：通过 `chatContainer.heightProperty()` 监听器 + `scrollToBottom()` 嵌套 `Platform.runLater()` 双重保障
- 防止 ScrollPane 焦点导致布局偏移：`chatScrollPane.setFocusTraversable(false)`，阻止 ScrollPane 获得焦点
- 防止 TextFlow 重新换行导致布局偏移：`fitToWidth="false"` + `chatContainer.prefWidthProperty().bind(chatScrollPane.widthProperty())`。根因：`fitToWidth=true` 使内容宽度跟随 viewport 宽度，而 viewport 宽度在垂直滚动条出现/消失时会变化（vbarPolicy=AS_NEEDED），导致 TextFlow 重新换行产生"缩进"效果。改为手动绑定 ScrollPane 整体宽度（稳定不变），彻底切断 viewport 宽度变化对内容的影响

**ViewModel 委托：**
- `viewModel.sendMessage()` - 发送消息
- `viewModel.stopGeneration()` - 停止生成
- `viewModel.clear()` - 清除对话
- `viewModel.addUserMessage()` - 添加用户消息到列表
- `viewModel.prepareHistoricalMessages()` - 准备历史消息
- `viewModel.getMessages()` - 监听消息列表变化

### AgentPageController
智能体配置页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `AgentPageViewModel`: 视图模型
- `WindowManager`: 窗口管理器

**职责（仅 UI）：**
- 渲染智能体卡片（TitledPane）
- 打开文件编辑器对话框（使用 FileEditorDialog 编辑 ~/.autiva/workspace/ 目录）
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

**职责（仅 UI）：**
- 浏览器路径文件选择器
- 双向绑定 ViewModel 属性到 UI 字段

**ViewModel 委托：**
- `viewModel.loadFromStore()` - 加载配置
- `viewModel.save()` - 保存配置
- `viewModel.reset()` - 重置配置
- `viewModel.getBrowserPath()` 等属性 - 双向绑定

### SkillPageController
技能页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `SkillPageViewModel`: 视图模型
- `WindowManager`: 窗口管理器

**职责（仅 UI）：**
- 渲染技能卡片
- 打开文件编辑器对话框
- ZIP 导入文件选择器

**ViewModel 委托：**
- `viewModel.loadSkills()` - 加载技能列表
- `viewModel.getSkills()` - 获取技能列表
- `viewModel.importSkillFromZip()` - 导入技能
- `viewModel.deleteSkill()` - 删除技能

### McpPageController
MCP 页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `McpPageViewModel`: 视图模型
- `WindowManager`: 窗口管理器

**职责（仅 UI）：**
- 渲染 MCP 服务器卡片
- 打开文件编辑器对话框（使用 FileEditorDialog 编辑 ~/.autiva/mcp/ 目录）

**ViewModel 委托：**
- `viewModel.loadServers()` - 加载服务器列表
- `viewModel.getServers()` - 获取服务器列表
- `viewModel.buildServerDescription()` - 构建服务器描述
- `viewModel.addServer()` - 添加服务器
- `viewModel.updateServer()` - 更新服务器
- `viewModel.deleteServer()` - 删除服务器

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

**职责：**
- 导航到不同页面
- 更新当前选中状态
- 显示/隐藏侧边栏

**设计优化：**
- 使用 `routeOptionMap`（Map<String, HBox>）替代 if-else 链管理路由选项
- CSS 类名 `sidebar__option--active` 提取为常量 `ACTIVE_CSS_CLASS`
- 鼠标点击事件通过 Map 遍历统一注册

### ButtonBarController
底部按钮栏控制器。

**职责：**
- 显示状态信息
- 动态更新按钮
- 状态变化动画

## 对话框控制器

### BrowserDialogController
浏览器对话框控制器。

**职责：**
- 显示网页内容
- 导航控制（前进、后退、刷新）
- URL 输入和加载

### FileEditorController
通用文件编辑器对话框控制器，系统中所有需要编辑文件的地方都使用此控制器。

**Spring 注解：** `@Component`

**实现接口：** `WindowManager.StageAware`

**职责：**
- 编辑文件和文件夹
- 管理文件夹结构
- 文件树展示和操作
- 多文件Tab编辑

**UI 布局（IDEA风格）：**
- 顶部工具栏：新建文件、新建文件夹按钮
- 左侧面板：文件树（TreeView）
- 中间编辑器面板：TabPane多文件编辑 + 行号 + 代码编辑器
- 右侧按钮栏：预览按钮、格式化按钮
- 底部状态栏：文件路径、编码和行号

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
- 策略模式：不同页面有不同的按钮配置
- 观察者模式：Controller 监听 ViewModel 属性变化更新 UI
- 双向绑定：SettingsPageController 与 SettingsPageViewModel 属性双向绑定

## 注意事项
1. 控制器使用 @Component 注解，由 Spring 管理
2. FXML 字段使用 @FXML 注解
3. 初始化逻辑在 initialize() 方法中
4. UI 操作需在 JavaFX 应用线程执行
5. Controller 不直接调用 Manager/Service，通过 ViewModel 间接调用
6. ViewModel 不持有任何 UI 引用（Node、Scene、Stage）
