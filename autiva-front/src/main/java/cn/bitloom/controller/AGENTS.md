# Controller 包

## 概述
本包实现了 JavaFX 控制器，负责 UI 交互和业务逻辑协调。

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

**职责：**
- 管理搜索输入框
- 加载和显示聊天页面（WebView）
- 处理消息发送
- 渲染 Markdown 响应
- 管理模型选择（通过 MenuButton）

**聊天 UI 设计：**

**消息类型：**
- `USER`: 用户消息，右对齐，蓝色渐变背景
- `ASSISTANT`: AI 消息，左对齐，灰色背景，支持 Markdown 渲染
- `TOOL_CALLS`: 工具调用消息，左对齐，带工具图标和参数
- `TOOL`: 工具响应消息，左对齐，带响应内容

**工具消息样式：**
- 工具图标：紫色渐变背景 SVG 图标，替代 emoji 避免乱码
- 工具名称：蓝色字体显示工具名
- 工具状态：等待中显示 "..."（橙色），完成显示 "✓"（绿色）
- 工具参数：灰色背景 JSON 格式化展示，语法高亮
  - Key 显示为蓝色
  - String 显示为绿色
  - Number 显示为橙色
  - Boolean 显示为紫色
  - Null 显示为灰色
- 工具响应：浅蓝色背景框，JSON 格式化展示

**Markdown 渲染：**
- 支持标题（# ## ###）
- 支持代码块（``` ```）和行内代码（`）
- 支持加粗（**）和斜体（*）
- 支持链接（[text](url)）
- 支持列表（- * 1.）

**动画效果：**
- 首次发送消息时，图标上移并淡出
- 聊天区域从底部展开

**模型选择功能：**
- 使用 ComboBox 实现模型选择
- 胶囊形状设计（上下直线，左右椭圆）
- 直接显示当前选择的模型名称（DEEPSEEK、Z）
- 无下拉箭头，宽度动态自适应模型名称
- 最小宽度为圆形（37px），与其他按钮统一
- 选择模型后自动保存到 Store.selectedModel
- 悬停时背景变灰，提供视觉反馈
- 使用 Text 类计算文本宽度，确保精确适配

### SettingsPageController
设置页控制器。

**职责：**
- 管理浏览器路径设置
- 管理保存目录设置
- 保存配置到文件

### AgentPageController
智能体配置页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `WindowManager`: 通过构造器注入
- `ConfigManager`: 配置管理器
- `ToolManager`: 工具管理器
- `AgentManager`: 智能体管理器

**职责：**
- 通过 AgentManager 加载和显示智能体文件夹列表
- 使用 TitledPane 实现智能体卡片折叠展示
- 显示每个智能体的工作区配置文件
- 以卡片形式显示工具配置，支持开关切换
- 打开配置文件编辑器

**UI 结构：**
- 使用 VBox 布局，每个智能体一个 TitledPane 卡片
- TitledPane 支持折叠/展开功能，默认折叠
- 智能体卡片之间有16px间隔
- TitledPane 标题栏样式：白色背景、圆角卡片、阴影效果
- 标题栏悬停时背景变为浅灰色，提供视觉反馈
- 标题栏左侧显示折叠箭头图标，展开时旋转180度
- 内容区域为独立卡片，与标题栏分离
- 工作区配置和工具配置区域有标题和分割线
- 工具配置：使用 VBox 布局，一行显示一个工具卡片
- 工具卡片显示工具名称和描述，右侧有iOS风格开关按钮

**工具配置：**
- 工具以卡片形式展示，类似 MCP 卡片样式
- 每个卡片显示工具名称和描述（支持换行）
- 右侧iOS风格开关按钮用于启用/禁用工具（无对号，滑动样式）
- 保存所有智能体的工具配置到配置文件

**配置文件：**
- 位于 `~/.autiva/workspace` 目录
- 每个智能体一个文件夹
- 文件内容为 Markdown 格式

**使用 MdEditor 组件：**
- 编辑智能体配置文件
- 使用 WindowManager 打开编辑器对话框

**按钮配置：**
- "刷新"按钮：重新加载智能体列表
- "保存配置"按钮：保存所有智能体的工具配置

**与 AgentManager 的关系：**
- 通过 AgentManager.loadAgentFolders() 获取智能体文件夹列表
- 使用 AgentManager.AgentFolder 和 AgentManager.AgentFile 类

### SkillPageController
技能页控制器。

**职责：**
- 加载和显示技能列表
- 从本地ZIP包导入技能
- 删除技能（直接删除，无确认弹框）

**依赖：**
- `SkillManager`: 技能管理器

**按钮配置：**
- "导入"按钮：打开文件选择器，选择ZIP包导入技能

**ZIP导入流程：**
1. 用户点击"导入"按钮
2. 打开文件选择器，筛选ZIP文件
3. 调用SkillManager.importSkillFromZip()解压并验证
4. 显示导入结果

**技能卡片：**
- 显示技能名称
- 显示技能描述
- 包含删除按钮（点击直接删除）

### McpPageController
MCP 页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `McpManager`: 通过构造器注入
- `WindowManager`: 通过构造器注入

**职责：**
- 加载和显示 MCP 服务器列表
- 打开 MCP 编辑器
- 删除 MCP 服务器配置（直接删除，无确认弹框）

**使用 WindowManager：**
- 使用 WindowManager 打开 MCP 编辑器对话框

### TaskPageController
任务页控制器。

**Spring 注解：** `@Component`

**依赖注入：**
- `CronManager`: 通过构造器注入

**职责：**
- 显示定时任务列表（按sessionId分组展示）
- 手动触发定时任务
- 删除定时任务
- 刷新任务列表

**任务分组展示：**
- 使用 VBox 布局，每个 Session 一个 TitledPane 卡片
- TitledPane 支持折叠/展开功能，默认折叠
- 卡片之间有16px间隔
- TitledPane 标题栏样式：白色背景、圆角卡片、阴影效果
- 标题栏悬停时背景变为浅灰色，提供视觉反馈
- 箭头按钮已隐藏，点击标题栏任意位置可折叠/展开
- 内容区域与标题栏融合为一个完整卡片
- 每个 Session 卡片内显示该 Session 的所有任务

**任务卡片显示：**
- 任务名称
- 任务类型（一次性任务/周期性任务/Cron任务）
- 任务状态（运行中/已取消）
- 创建时间
- 任务配置（延迟/间隔/Cron表达式）
- 消息内容（超过50字符自动截断）

**按钮配置：**
- "刷新"按钮：重新加载任务列表

**操作：**
- 触发任务：手动触发指定的定时任务（验证sessionId权限）
- 删除任务：删除指定的定时任务（验证sessionId权限）

**时间格式：**
- 使用 `yyyy-MM-dd HH:mm:ss` 格式显示创建时间

**与 CronManager 的关系：**
- 通过 CronManager 获取和管理任务
- 获取所有任务时传null，不按sessionId过滤
- 删除和触发任务时传入任务的sessionId进行权限验证

### SideBarController
侧边栏控制器。

**职责：**
- 导航到不同页面
- 更新当前选中状态
- 显示/隐藏侧边栏

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

### MdEditorDialogController
Markdown 编辑器对话框控制器。

**Spring 注解：** `@Component`

**实现接口：** `WindowManager.StageAware`

**职责：**
- Markdown 编辑
- 实时预览
- 字数统计
- 处理智能体的编辑命令

**Stage 管理：**
- 通过 StageAware 接口接收 Stage 对象
- 支持保存和关闭操作

### McpEditorDialogController
MCP 编辑器对话框控制器。

**Spring 注解：** `@Component`

**实现接口：** `WindowManager.StageAware`

**职责：**
- 编辑 MCP 服务器配置
- 支持多种传输类型
- 保存配置

**Stage 管理：**
- 通过 StageAware 接口接收 Stage 对象
- 支持保存和取消操作

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
- MVC 模式：控制器协调视图和模型
- 组合模式：IndexController 管理子控制器
- 策略模式：不同页面有不同的按钮配置

## 注意事项
1. 控制器使用 @Component 注解，由 Spring 管理
2. FXML 字段使用 @FXML 注解
3. 初始化逻辑在 initialize() 方法中
4. UI 操作需在 JavaFX 应用线程执行
