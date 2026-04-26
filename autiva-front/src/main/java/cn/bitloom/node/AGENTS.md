# Node 包

## 概述
本包定义了自定义 JavaFX 节点组件，包括通用 SVG 组件和聊天消息卡片组件。

## 核心类

### SvgImageView
自定义 ImageView，支持 SVG 图像加载和渲染。

**功能：**
- 加载 SVG 资源文件
- 自动转换为 PNG 格式显示
- 支持指定尺寸
- 延迟加载：先设置尺寸再加载 SVG，避免尺寸为0时渲染失败

### ChatMessage
聊天消息数据模型，用于 ViewModel 和 Controller 之间的数据传递。

**类型枚举：**
- `Type.USER`: 用户消息
- `Type.ASSISTANT`: 助手消息
- `Type.TOOL`: 工具消息

**属性：**
- `content`: 消息内容（StringProperty），助手消息为 Markdown 文本
- `finishReason`: 完成原因（ObjectProperty<FinishReason>），STOP 或 TOOL_CALLS
- `streaming`: 是否正在流式输出（BooleanProperty）
- `toolCalls`: 工具调用信息列表（ObservableList<ToolCallInfo>）
- `responses`: 工具响应信息列表（ObservableList<ToolResponseInfo>）

**内部类：**
- `ToolCallInfo`: 工具调用信息（name, arguments）
- `ToolResponseInfo`: 工具响应信息（name, responseData）

### UserMessageCard
用户消息卡片，显示用户发送的消息。

**样式类：** `chat-message`, `chat-message--user`

**特性：**
- 蓝色渐变背景，右对齐
- 圆角气泡样式（右下角小圆角）
- 禁用焦点遍历（`setFocusTraversable(false)`），防止点击时焦点环导致布局偏移

### AssistantMessageCard
助手消息卡片，显示助手回复的消息，支持 Markdown 渲染和流式更新。

**样式类：** `chat-message`, `chat-message--assistant`, `chat-message--streaming`（流式输出时）

**特性：**
- 灰色背景，左对齐
- 使用 MarkdownFxRenderer 将 Markdown 渲染为 JavaFX Node
- 监听 ChatMessage.content 属性变化，自动重新渲染
- 流式输出时添加 streaming 样式类
- 禁用焦点遍历（`disableFocusRecursively()`），递归遍历所有后代节点，对 Control 子类设置 `setFocusTraversable(false)`，防止点击时焦点环导致 MD 内容缩进偏移
- 布局偏移根因：JavaFX Modena 主题 `:focused` 伪类会改变 ScrollPane 的 `-fx-background-insets`，导致 viewport 宽度变化，进而使 TextFlow 重新换行。需在 CSS 中为 `.chat-scroll-pane:focused` 显式设置 `-fx-background-insets: 0`，并在 Controller 中设置 `chatScrollPane.setFocusTraversable(false)` 双重保障

### ToolMessageCard
工具消息卡片，显示工具调用请求或响应，支持折叠展开。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--tool-request` / `chat-message--tool-response`

**特性：**
- 可折叠的 JSON 内容区域
- 点击标题栏切换展开/折叠
- JSON 自动格式化（使用 fastjson2 PrettyFormat）
- 请求和响应有不同的背景色
- 禁用焦点遍历（`setFocusTraversable(false)`），卡片、header、Label、TextFlow 均禁用焦点

### QuestionCard
问题交互卡片，用于 AskUserQuestionTool 的用户交互。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--question`

**特性：**
- 支持单选和多选模式
- 选项按钮点击切换选中状态
- **提交逻辑：**
  - 单问题单选：点击选项后立即提交
  - 单问题多选：需要点击"提交"按钮
  - 多问题（无论单选/多选）：需要回答所有问题后点击"提交"按钮
- 提交后禁用选项，显示已回答区域
- 通过 BiConsumer 回调通知 ToolUIBridge

### TodoCard
待办事项卡片，用于 TodoWriteTool 的进度展示。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--todo`

**特性：**
- 显示待办事项列表，每项有状态指示器
- 支持 pending/in_progress/completed 三种状态
- 已完成项显示删除线
- 进行中项显示 activeForm
- 进度条和完成统计

**字段：**
- `svgPath`: SVG 文件路径
- `loaded`: 是否已加载完成

**方法：**
- `setSvgPath(String)`: 设置 SVG 路径并加载（如果尺寸已设置）
- `loadSvg()`: 内部方法，加载并转换 SVG

**加载机制：**
- 构造函数中注册 fitWidth/fitHeight 属性监听器
- 当尺寸从0变为正值且 svgPath 已设置时，自动触发加载
- `setSvgPath()` 在尺寸已设置时立即加载，否则等待尺寸设置后自动加载
- `loaded` 标志防止重复加载

## 使用示例

### FXML 中使用
```xml
<SvgImageView fx:id="icon" svgPath="/cn/bitloom/images/icon.svg" 
              fitWidth="32" fitHeight="32"/>
```

### 代码中使用
```java
SvgImageView imageView = new SvgImageView();
imageView.setFitWidth(32);
imageView.setFitHeight(32);
imageView.setSvgPath("/cn/bitloom/images/icon.svg");
```

## 实现原理

### SVG 转换流程
1. 读取 SVG 文件内容
2. 使用 Apache Batik 的 PNGTranscoder 转换
3. 设置目标宽高
4. 转换为 BufferedImage
5. 使用 SwingFXUtils 转换为 JavaFX Image

### 依赖库
- Apache Batik: SVG 解析和转换
- JavaFX: 图像显示

## 错误处理
- 资源不存在：打印错误信息到 stderr
- 转换失败：打印异常信息到 stderr

## 设计模式
- 继承：扩展 ImageView 功能
- 封装：隐藏 SVG 转换细节

## 注意事项
1. SVG 路径是类路径资源路径
2. 尺寸通过 fitWidth/fitHeight 设置
3. **必须先设置尺寸（fitWidth/fitHeight），再设置 svgPath**，否则 SVG 无法正确渲染
4. 修改尺寸后需要重新设置 svgPath
5. loaded 标志确保 SVG 只加载一次
