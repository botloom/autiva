# Node 包

## 概述
本包定义了自定义 JavaFX 节点组件，包括通用 SVG 组件、聊天消息卡片组件、编辑器相关节点（语法高亮、文件树单元格、变更列表单元格）和画布相关组件。

## 子包

### canvas
画布相关组件，包含以下子包：

#### canvas/model
画布数据模型，定义画布元素和场景。

**核心类：**
- `CanvasElement`: 画布元素基类（描边/填充/线宽/粗糙度/透明度/线条样式/圆角等属性）
- `CanvasScene`: 画布场景（元素列表、图层管理）
- `CanvasSceneSerializer`: 场景序列化/反序列化
- `Layer`: 图层数据模型
- `Point`: 2D 点
- `RectangleElement/DiamondElement/EllipseElement`: 形状元素
- `ArrowElement/LineElement`: 线条元素
- `FreehandElement`: 手绘路径元素
- `TextElement`: 文字元素

#### canvas/render
画布渲染器，负责将元素绘制到 Canvas。

**核心类：**
- `CanvasRenderer`: 主渲染器（脏标记、重绘、缩放/平移变换、手写字体）
- `ElementRenderer`: 元素渲染工具（形状/线条/手绘路径/文字的绘制方法）
- `RoughRenderer`: 手绘风格渲染器（基于 rough.js 算法）
- `SelectionRenderer`: 选中状态渲染器（边框/手柄/旋转指示器）

#### canvas/tool
画布工具，处理鼠标交互。

**核心类：**
- `CanvasTool`: 工具基类（鼠标事件处理、光标设置）
- `SelectTool`: 选择工具（点击选中、框选、拖拽移动、缩放手柄）
- `RectangleTool/DiamondTool/EllipseTool`: 形状工具
- `ArrowTool/LineTool`: 线条工具
- `FreehandTool`: 手绘工具
- `TextTool`: 文字工具（双击编辑）
- `PanTool`: 平移工具（中键拖拽）
- `DiagramExportTool`: 图表导出工具
- `DiagramGenerateTool`: AI 图表生成工具

#### canvas/CanvasView
画布视图组件，管理 Canvas 和工具交互。

**核心功能：**
- Canvas 渲染循环（dirty 标记 + requestLayout 重绘）
- 工具切换和鼠标事件分发
- 缩放/平移变换（Ctrl+滚轮缩放、中键平移）
- 选中元素回调通知

### editor/syntax
代码语法高亮框架，基于 RichTextFX 的 `StyleClassedTextArea.setStyleClass` API 实现。所有高亮器无状态、线程安全，工厂内单例缓存。

**设计模式：**
- `SyntaxHighlighter`：接口，`void apply(StyleClassedTextArea area, String text)`
- `AbstractRegexHighlighter`：抽象基类，子类提供 `List<TokenGroup>`（regex + styleClass），基类将其用 `|` 拼接为 alternation 模式，按捕获组序号应用样式。**正则中只允许非捕获分组 `(?:...)`**，否则破坏基类的捕获组序号映射。alternation 中靠前的组优先匹配，因此注释/字符串等宽匹配规则放在前面
- `SyntaxHighlighterFactory`：单例工厂，按扩展名返回 highlighter。`forPath(Path)` / `forExtension(String)` 两个入口；未知扩展名返回 `PlainTextHighlighter`（no-op）

**支持语言与扩展名映射：**
| Highlighter | 扩展名 |
|---|---|
| `JavaSyntaxHighlighter` | java/kt/kts/scala/groovy/gradle |
| `JavaScriptSyntaxHighlighter`（含 TS） | js/jsx/mjs/cjs/ts/tsx/mts/cts |
| `PythonSyntaxHighlighter` | py/pyw/pyi |
| `JsonSyntaxHighlighter` | json/json5/geojson/tsbuildinfo |
| `XmlSyntaxHighlighter` | xml/fxml/html/htm/xhtml/svg/xsd/xsl/xslt/dtd/tld/plist |
| `YamlSyntaxHighlighter` | yml/yaml |
| `PropertiesSyntaxHighlighter` | properties/ini/conf/cfg/config/env |
| `MarkdownSyntaxHighlighter` | md/markdown/mdx |

**Token 样式类（与 editor-panel.css 中 `.syntax-*` 对应）：**
- `syntax-comment`：注释（块注释、行注释）
- `syntax-string`：字符串、字符、模板字符串
- `syntax-keyword`：语言关键字
- `syntax-literal`：字面量（true/false/null/None 等）
- `syntax-number`：数字
- `syntax-annotation`：注解/装饰器（`@Override`、`@decorator`）
- `syntax-type`：类型名（大写开头标识符）
- `syntax-key`：JSON/YAML 键名
- `syntax-operator`：操作符

**特殊实现技巧：**
- `JsonSyntaxHighlighter`：用零宽断言 `(?<=...)` 区分 key 与 string
- `YamlSyntaxHighlighter`：开启 `MULTILINE` flag，识别文档分隔符 `---`、锚点 `&anchor`、别名 `*alias`
- `PythonSyntaxHighlighter`：支持三引号字符串、f/r/b 前缀
- `JavaScriptSyntaxHighlighter`：包含 TypeScript 关键字和模板字符串 `${...}`

### editor（通用编辑器组件）
通用编辑器相关组件。

**核心类：**
- `AddToChatButton`: "添加到对话框"悬浮按钮，继承 `Button`。在编辑器面板选中文本后浮现，点击后将选中文本通过回调追加到对话框输入框。默认不可见（visible=false, managed=false），始终保持 managed=false 以支持 StackPane 内的绝对定位（setLayoutX/setLayoutY），通过 `show(text, x, y)` 定位显示（仅切换 visible），`hide()` 隐藏。样式类 `add-to-chat-button`（Apple 风格圆角半透明深色背景 + 阴影，hover 蓝色高亮）。

### diff
变更文件列表的富单元格组件。

**核心类：**
- `DiffListCell`: 变更文件富 ListCell，渲染 FileDiff。支持拖拽变更文件到对话框（`setOnDragDetected` 将 filePath 转为 File 放入 Dragboard）

## 核心类

### SvgImageView
自定义 ImageView，支持 SVG 图像加载和渲染。

**功能：**
- 加载 SVG 资源文件
- 自动转换为 PNG 格式显示
- 支持指定尺寸
- 延迟加载：先设置尺寸再加载 SVG，避免尺寸为0时渲染失败

### AutoResizeTextArea
自动调整高度的文本输入区域，继承 `TextArea`。

**功能：**
- 重写 `computePrefHeight(double width)`，根据内容计算首选高度
- 内容变化时调用 `requestLayout()` 触发重新布局
- 高度范围：48px（1行）~ 126px（5行）
- 超过5行后固定高度，内部自动显示滚动条
- 使用 Text 节点测量内容实际高度（包含自动换行）

**常量：**
- `LINE_HEIGHT = 22`：单行高度（含行间距）
- `VERTICAL_PADDING = 16`：上下内边距总和
- `MIN_HEIGHT = 48`：最小高度（1行）
- `MAX_HEIGHT = 126`：最大高度（5行）
- `CONTENT_WIDTH_OFFSET = 76`：内容宽度偏移（padding + 滚动条预留）

**设计原理：**
- JavaFX 布局系统通过 `computePrefHeight()` 获取组件首选高度
- 重写此方法让布局系统自然获取正确高度，避免手动 `setPrefHeight()` 与布局系统冲突
- 解决了手动设值导致的闪烁和失焦还原问题

### MessageCard（抽象类）
消息卡片抽象基类，继承 `VBox`，消除 ChatMessage 中间层。各卡片继承此抽象类，可以直接添加到 JavaFX 容器中。

**抽象方法：**
- `getMessageType()`: 返回消息类型（MessageEvent.Type：USER/ASSISTANT/TOOL）
- `getContent()`: 返回消息内容（用于复制按钮等操作）

**实例方法：**
- `getDisplayName()`: 返回卡片显示名称（默认返回消息类型名称，用于日志和调试）

### UserMessageCard
用户消息卡片，显示用户发送的消息。继承 MessageCard 抽象类。

**样式类：** `chat-message`, `chat-message--user`

**特性：**
- 蓝色渐变背景，右对齐
- 圆角气泡样式（右下角小圆角）
- 禁用焦点遍历（`setFocusTraversable(false)`），防止点击时焦点环导致布局偏移
- `getMessageType()` 返回 `MessageEvent.Type.USER`
- `getContent()` 返回用户输入的文本内容

**外部操作按钮：**
- 由 HomePageController 在卡片外部创建操作按钮栏（chat-message__actions），位于卡片下方
- 用户消息的操作按钮右对齐（chat-message__actions--user）
- 复制按钮：点击将消息文本复制到系统剪贴板，复制后短暂显示蓝色高亮状态
- 喜欢按钮：点击切换喜欢状态（蓝色高亮），与不喜欢互斥
- 不喜欢按钮：点击切换不喜欢状态（红色高亮），与喜欢互斥

### AssistantMessageCard
助手消息卡片，显示助手回复的消息，支持 Markdown 渲染和流式更新。继承 MessageCard 抽象类。

**样式类：** `chat-message`, `chat-message--assistant`, `chat-message--streaming`（流式输出时）

**特性：**
- 灰色背景，左对齐
- 使用 MarkdownFxRenderer 将 Markdown 渲染为 JavaFX Node
- **自身持有 JavaFX 属性**（原 ChatMessage 的属性下沉到卡片）：
  - `content`（StringProperty）：消息内容
  - `finishReason`（ObjectProperty<String>）：完成原因（STOP/TOOL_CALLS）
  - `streaming`（BooleanProperty）：是否正在流式输出
- **流式累积方法**（原 ChatMessage 的逻辑下沉）：
  - `appendContent(String chunk)`: 累积流式内容，自动设置 streaming=true，触发 contentProperty 变化
  - `complete(String reason)`: 结束流式输出，设置 finishReason 和 streaming=false，内容为空时将 content 设为 null
  - `isValid()`: 判断消息内容是否有效（非空）
- **流式渲染优化**：
  - 流式期间（streaming=true）：复用 TextFlow，直接替换文本内容（不清空重建），避免高频节点重建
  - 流式结束（streaming=false）：监听器触发，一次性 Markdown 渲染，确保最终显示效果美观
- 流式输出时添加 streaming 样式类
- **内部管理 actionBar**：通过 `setActionBar(HBox)` 接收操作按钮栏，流式期间隐藏，完成后自动显示

**构造函数：**
- `AssistantMessageCard()`: 空构造，用于流式消息
- `AssistantMessageCard(String initialContent, String finishReason)`: 带初始内容构造，用于历史消息

**回调：**
- `onContentChanged(Consumer<String>)`: 内容变化时触发，由 HomePageController 设置为 `scrollToBottom()`

**字段：**
- `streamingContainer/streamingText`: 流式期间复用的组件
- `accumulator`: 流式内容累积器（StringBuilder）
- `actionBar`: 操作按钮栏（由 Controller 传入，卡片内部管理显示/隐藏）

**监听逻辑说明**：
- `complete()` 只设置 streaming=false，不修改 content（除非内容为空）
- 因此需要监听 streamingProperty 变化来触发 Markdown 渲染
- contentProperty 监听器只负责流式期间的文本更新

### ToolMessageCard
工具消息卡片，显示工具调用请求或响应，支持折叠展开和结构化渲染。继承 MessageCard 抽象类。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--tool-request` / `chat-message--tool-success` / `chat-message--tool-error` / `chat-message--tool-warning`

**请求卡片（isRequest=true）：**
- 蓝色背景，工具名 + JSON 格式化参数
- 点击 header 展开/折叠内容

**响应卡片（isRequest=false）— 结构化渲染：**
- 尝试 `ToolResult.fromJson(arguments)` 解析
- 解析成功：
  - 根据状态添加样式：SUCCESS=绿色、ERROR=红色、WARNING=黄色
  - Header：状态圆点 + 工具名 + 摘要文本（message）
  - Content（可折叠）：data 标签区（key-value pill）+ rawOutput 区域
- 解析失败：降级到纯文本展示（绿色背景，与原有行为一致）

**组件结构（结构化响应）：**
```
ToolMessageCard (VBox)
├── header (HBox, clickable)
│   ├── statusDot (Circle, 4px)
│   ├── nameLabel (Label: 工具名)
│   └── summaryLabel (Label: 摘要)
└── contentBox (VBox, collapsible)
    ├── dataPane (FlowPane)
    │   ├── dataItem (HBox: keyLabel + valueLabel)
    │   └── ...
    ├── divider (Region)
    └── outputFlow (TextFlow: rawOutput)
```

**特性：**
- 状态圆点颜色：SUCCESS=#22c55e、ERROR=#ef4444、WARNING=#f59e0b
- 工具名颜色随状态变化：SUCCESS=绿色、ERROR=红色、WARNING=琥珀色
- data 标签使用浅色背景 pill 样式
- rawOutput 使用等宽字体展示
- 禁用焦点遍历（`setFocusTraversable(false)`）

### ToolGroupCard
工具分组卡片，将连续的工具调用自动分组到一个可折叠容器中，减少纵向空间占用。

**样式类：** `chat-message`, `chat-message--tool-group`

**特性：**
- 默认折叠，仅显示摘要行（工具数量 + 工具名称列表）
- 折叠态示例：`▶ 3 个工具调用 · SearchCodebase · Grep · Read`
- 展开态示例：`▼ 3 个工具调用` + 各 ToolMessageCard 列表
- 点击 header 切换展开/折叠（与项目中 ToolMessageCard、TaskCard 的折叠模式一致，使用 visible/managed 切换）
- 支持动态添加 ToolMessageCard（流式场景下工具逐个到达时自动追加到当前分组）
- 工具名称去重（使用 LinkedHashSet 保持插入顺序）
- 单个工具调用也使用 ToolGroupCard 包裹，保持一致性

**组件结构：**
```
ToolGroupCard (VBox)
├── header (HBox, clickable)
│   ├── chevronLabel (Label: "▶" / "▼")
│   ├── countLabel (Label: "N 个工具调用")
│   ├── separatorLabel (Label: "·")
│   └── namesLabel (Label: "工具名称列表")
└── body (VBox, collapsible)
    ├── ToolMessageCard
    ├── ToolMessageCard
    └── ...
```

**分组逻辑（在 HomePageController 中）：**
- 连续的 TOOL 类型消息自动归入同一个 ToolGroupCard
- 非 TOOL 类型消息（USER/ASSISTANT）中断当前分组，下次 TOOL 消息创建新分组
- 通过 `currentToolGroup` 字段追踪当前活跃的工具分组
- 清除对话时重置 `currentToolGroup = null`

### QuestionCard
问题交互卡片，用于 AskUserQuestionTool 的用户交互。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--question`

**特性：**
- 支持单选和多选模式
- 选项按钮点击切换选中状态
- 每个问题自动添加"其他"选项，点击后显示文本输入框，支持自由文本输入
- "其他"选项使用虚线边框样式（`chat-message__question-option--other`）区分
- 文本输入框按 Enter 键可提交答案
- **提交逻辑：**
  - 单问题单选（选择预设选项）：点击选项后立即提交
  - 单问题单选（选择"其他"）：显示文本输入框，按 Enter 或点击"提交"按钮提交
  - 单问题多选：需要点击"提交"按钮
  - 多问题（无论单选/多选）：需要回答所有问题后点击"提交"按钮
- 提交后禁用选项和文本输入框，显示已回答区域
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

### FileTreeCell
文件树单元格，用于 EditorPanelController 的项目目录树展示。位于 `node/project` 子包。

**继承：** `TreeCell<Path>`

**职责：**
- 显示文件/文件夹名称
- 使用 SvgImageView 加载 SVG 图标（16x16），按文件扩展名选择对应类型图标
  - 文件夹 → `folder.svg`（蓝色 #0071e3）
  - 代码文件 → `file-code.svg`（青色 #5ac8fa，`</>` 符号）
  - 数据/配置文件 → `file-data.svg`（橙色 #ff9f0a，`{}` 符号）
  - Markdown 文件 → `file-md.svg`（蓝色 #0a84ff，`#` 符号）
  - 纯文本文件 → `file-text.svg`（灰色 #98989d，横线）
  - 图片文件 → `file-image.svg`（绿色 #30d158，山形+圆）
  - 其他 → `file.svg`（白色，兜底）
- 支持拖拽文件到对话框：仅文件（`Files.isRegularFile`）注册 `setOnDragDetected`，将文件转为 File 放入 Dragboard（COPY 传输模式），目录不注册拖拽

**扩展名集合：**
- `CODE_EXTS`：java/kt/scala/groovy/gradle/js/jsx/mjs/cjs/ts/tsx/mts/cts/py/pyw/pyi/c/cpp/cc/h/hpp/go/rs/rb/php/swift/m/mm/sh/bash/zsh/bat/cmd/ps1
- `DATA_EXTS`：json/json5/geojson/tsbuildinfo/xml/fxml/html/htm/xhtml/svg/xsd/xsl/xslt/dtd/tld/plist/yml/yaml/properties/ini/conf/cfg/config/env/toml
- `MD_EXTS`：md/markdown/mdx
- `TEXT_EXTS`：txt/log/csv/tsv
- `IMAGE_EXTS`：png/jpg/jpeg/gif/bmp/webp/ico/icns/tiff/tif

**样式类（BEM）：**
- `file-tree__folder`：文件夹（粗体）
- `file-tree__file`：文件（常规）
- 修饰类：`file-tree__file--code` / `--data` / `--md` / `--text` / `--image`（与图标颜色对应，字色微调；选中态由 CSS 强制为白色）

**实现细节：**
- `updateItem` 中先移除所有样式类再按文件类型添加，避免复用 cell 时残留旧类
- `extensionOf` 方法：处理 dot <= 0（隐藏文件无扩展名）和 dot == length-1（结尾是点）边界情况

**设计规范：**
- 符合 Apple 设计规范，使用 SVG 矢量图标替代 emoji
- 图标通过 `cn.bitloom.node.svg.SvgImageView` 加载
- 扩展名集合与 DiffListCell 保持一致，保证文件树与变更列表的图标语义统一

### DiffListCell
变更文件列表富单元格，用于 EditorPanelController 的变更列表展示。位于 `node/diff` 子包。

**继承：** `ListCell<FileDiff>`

**职责：**
- 富渲染变更文件：文件类型图标 + 文件名 + 相对目录 + 行数统计（+N -M）
- 行数统计遍历 hunks/lines 累加 ADD/REMOVE 行数
- 图标解析与 FileTreeCell 完全一致（复用相同的扩展名集合）
- **变更类型通过整体底色区分（不展示文字徽章）**：cell 添加修饰类 `diff-list-cell--add/--modify/--delete`，由 CSS 控制整体背景色（绿/橙/红，10% 透明度），hover 时透明度提升到 20%，selected 时统一为蓝色填充

**组件结构：**
```
DiffListCell (ListCell)
└── HBox (diff-list-cell, center-left, spacing 8)
    ├── SvgImageView (diff-list-cell__icon, 16x16)
    ├── VBox (diff-list-cell__text)
    │   ├── Label (diff-list-cell__name, 13px 白)
    │   └── Label (diff-list-cell__path, 11px 次要色)
    ├── Region (spacer)
    └── HBox (diff-list-cell__stats)
        ├── Label (diff-list-cell__stat--add, "+N" 11px 等宽绿)
        └── Label (diff-list-cell__stat--remove, "-M" 11px 等宽红)
```

**样式类（BEM）：**
- `diff-list-cell`：根 HBox
- `diff-list-cell__icon` / `__text` / `__name` / `__path` / `__stats`
- `diff-list-cell__stat` + `--add`（绿）/ `--remove`（红）：行数统计
- `diff-list-cell--add` / `--modify` / `--delete`：**cell 级修饰类**（加在 ListCell 上，非 HBox），控制整体背景色区分新建/修改/删除

**辅助方法：**
- `extractFileName(path)`：从路径中提取文件名
- `extractDirPath(path)`：从路径中提取相对目录
- `computeStats(diff)`（静态）：遍历 hunks/lines 统计 ADD/REMOVE 行数
- `extensionOf(fileName)`：解析扩展名

**设计规范：**
- 行高 56px，富信息显示，与 Xcode/GitHub Desktop 的变更列表风格一致
- 与 FileTreeCell 共享扩展名集合，保证图标语义统一
- 不再显示 A/M/D 徽章色块（变更类型由 cell 整体底色体现）

### DiffReviewCard
Diff 审核卡片，用于 WriteTool/EditTool 的文件修改审核展示。参考 QuestionCard 的设计模式。

**样式类：** `chat-message`, `chat-message--tool`, `diff-review-card`

**构造参数：**
- `diff`: FileDiff 对象
- `reviewId`: 审核 ID（UUID，用于关联 CompletableFuture）
- `onReviewed`: BiConsumer<String, String> 回调（reviewId, resultJson），通知 ToolUIBridge 完成审核

**组件结构（每行 HBox）：**
```
lineRow (HBox)
├── gutter (Region, 3px 指示条：绿/红)
├── oldLineLabel (Label, 36px 右对齐)
├── newLineLabel (Label, 36px 右对齐)
└── lineFlow (TextFlow: prefixText + contentText)
```

**特性：**
- 展示文件路径和操作类型（新建/修改/删除）
- **不展示 @@ hunk 头行和 +/- 前缀**，纯色覆盖
- 双列行号（old/new）：ADD 行显示 new 行号（绿色），REMOVE 行显示 old 行号（红色），CONTEXT 行显示双列
- Gutter 指示条：ADD 行绿色竖条（#22c55e），REMOVE 行红色竖条（#ef4444）
- 行级背景色：ADD 行绿色背景（rgba(34,197,94,0.12)），REMOVE 行红色背景（rgba(239,68,68,0.12)）
- 滚动面板展示完整 Diff（最大高度 400px）
- "批准修改"按钮（绿色）和"拒绝修改"按钮（红色）
- 点击后禁用按钮，通过 onReviewed 回调返回结果 JSON（{"approved":true/false, "comment":""}）

### TaskCard
任务卡片，用于子智能体任务的流式输出展示。

**样式类：** `chat-message`, `chat-message--tool`, `chat-message--task`

**特性：**
- Header 显示脉冲动画点、子智能体名称、任务描述、运行状态
- 可折叠的 body 区域，包含消息容器
- 通过 `session.getEventBus().outBoxSubscribe()` 订阅子会话消息流，实时处理子智能体输出
- 使用 `MarkdownFxRenderer` 将 Markdown 渲染为 JavaFX Node
- **processEvent(MessageEvent) 替代 processMessage(Message)**：通过 MessageEvent 的结构化字段（Type、text、finishReason、toolCalls、responses）直接访问数据，不再使用 FastJSON 反射解析
- **空白消息过滤**：流式累积内容为空白时不创建流式容器，STOP/TOOL_CALLS 完成时内容为空白则移除容器，避免出现空白区域
- **流式渲染优化**：流式输出期间使用轻量 TextFlow 纯文本渲染（`renderLightweightStream`），避免高频 Markdown 解析；STOP/TOOL_CALLS 完成时做完整 Markdown 渲染（`renderStreamContent`），确保最终显示效果美观
- 支持流式输出（`appendOutput`）和事件订阅（`subscribeSession`）两种内容更新方式
- **线程安全设计**：`appendOutput()`、`setStatus()`、`complete()` 公开方法内部包裹 `Platform.runLater()`，确保从任意线程调用安全；内部实现方法 `doAppendOutput()`、`doSetStatus()` 不包含 `Platform.runLater()`，供 `complete()` 在 FX 线程内直接调用，避免嵌套 `Platform.runLater()`
- `onContentChanged` 回调：内容变化时通知外层 HomePageController 触发 ScrollPane 自动滚动，与 AssistantMessageCard 的回调模式一致
- 脉冲动画：运行中时脉冲点闪烁，完成/失败时停止
- 内嵌工具卡片宽度约束：工具卡片用 HBox 包裹后添加到 messageContainer，HBox 的 `fillHeight=true` 但不拉伸卡片宽度，使工具卡片按内容自然宽度显示（折叠时仅为工具名标签宽度，展开后按内容撑开），与主智能体中独立工具卡片的布局方式一致

**回调：**
- `onContentChanged(Consumer<String>)`: 内容变化时触发，由 HomePageController 在 `addChatNode()` 中设置为 `scrollToBottom()`，确保流式输出时 ScrollPane 自动向下滚动

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
4. 输出 PNG 字节流
5. 直接从 PNG 字节流创建 JavaFX Image（无需 AWT/SwingFXUtils 中间层）

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
