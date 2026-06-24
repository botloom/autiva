# A2UI 模块

## 概述
本包实现了 A2UI v0.9.1 协议的消息模型和事件类型,为 Agent 提供声明式动态 UI 生成能力。

## 核心类

### A2UIMessage
A2UI 消息 sealed interface,定义四种消息类型:
- `CreateSurface` - 创建界面画布(surfaceId/catalogId/theme/sendDataModel)
- `UpdateComponents` - 更新组件(扁平列表,通过 ID 引用)
- `UpdateDataModel` - 更新数据模型(JSON Pointer)
- `DeleteSurface` - 删除界面

### A2UIComponent
A2UI 组件定义(record),采用邻接列表模型:
- `id` - 组件唯一 ID
- `component` - 组件类型(A2UIComponentType 枚举)
- `properties` - 组件属性 Map
- `action` - 可选交互动作(A2UIAction)
- `checks` - 可选验证规则列表

### A2UIComponentType
Basic Catalog 组件类型枚举:
- 布局: ROW, COLUMN, LIST
- 显示: TEXT, IMAGE, ICON, DIVIDER
- 交互: BUTTON, TEXT_FIELD, CHECK_BOX, SLIDER, DATE_TIME_INPUT, CHOICE_PICKER
- 容器: TABS, CARD

### A2UIAction
动作模型(sealed interface),双轨制:
- `Event` - 智能体事件(name + context),派发到 Agent
- `FunctionCall` - 本地函数(call + args),渲染器执行

### A2UICheck
验证规则(condition + message),用于 Button 禁用逻辑。

### A2UIEvent / A2UIActionEvent
**注意:** 这两个事件类已移到 `cn.bitloom.agentic.event` 包中,以符合 Java sealed class 的模块限制。

## 事件流转
```
Agent 调用 A2UITool
  → ToolUIBridge.handleA2UIMessage()
  → Platform.runLater 创建/更新 A2UICard
  → A2UISurface.handleMessage() 更新组件树和数据模型
  → A2UIRenderer.render() 渲染 JavaFX Node

用户交互(点击按钮)
  → A2UIRenderer.handleAction()
  → A2UISurface.fireUserAction()
  → ToolUIBridge.onA2UIAction()
  → EventBus.publishIn(A2UIActionEvent)
  → SessionRunner 处理 → Agent.runStream()
```
