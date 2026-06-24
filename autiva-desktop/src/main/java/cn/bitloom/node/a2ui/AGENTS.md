# A2UI 渲染器模块

## 概述
本包实现 A2UI v0.9.1 协议的 JavaFX 渲染层,将 A2UI 组件树渲染为 JavaFX 原生组件。

## 核心类

### A2UISurface
Surface 管理器,管理组件树和数据模型:
- `components` - Map<String, A2UIComponent> 组件树(扁平存储)
- `dataModel` - Map<String, Object> 数据模型(真相源)
- `renderer` - A2UIRenderer 渲染器
- `rootNode` - JavaFX 根节点

核心方法:
- `handleMessage(A2UIMessage)` - 处理四种 A2UI 消息
- `getByPath(String)` - JSON Pointer 获取数据
- `resolveValue(Object)` - 解析数据绑定({path: "/xxx"})
- `fireUserAction(componentId, actionName, context)` - 触发用户交互回流
- `executeFunction(name, args)` - 执行本地函数(required/email/openUrl)
- `evaluateCheck(A2UICheck)` - 评估验证规则

### A2UIRenderer
JavaFX 渲染器,每个 Basic Catalog 组件一个渲染方法:
- 布局: renderRow(HBox)/renderColumn(VBox)/renderList(ScrollPane+VBox)
- 显示: renderText(Label)/renderImage(ImageView)/renderIcon(Label)/renderDivider(Region)
- 交互: renderButton/renderTextField/renderCheckBox/renderSlider/renderDateTimeInput/renderChoicePicker
- 容器: renderTabs(TabPane)/renderCard(VBox)

渲染流程:
1. 创建 JavaFX 组件
2. 应用属性(支持 DynamicString/Number/Boolean 绑定)
3. 绑定 action(Event 回流 / FunctionCall 本地执行)
4. 应用 checks(验证规则,Button 禁用)
5. 应用 Apple 风格样式

数据绑定:
- `bindInputToDataModel(property, valueSpec)` - 输入组件值变化时自动更新数据模型
- `resolveValue(value)` - 解析 {path: "/xxx"} 引用

### A2UICard
A2UI Surface 的 JavaFX 容器(继承 VBox):
- 嵌入聊天流作为特殊"卡片"
- 持有 A2UISurface 管理组件树
- `handleMessage(A2UIMessage)` - 处理消息更新 UI
- `setOnUserAction(callback)` - 设置用户交互回调

## 样式
样式文件: `resources/cn/bitloom/style/a2ui.css`
- Apple 设计规范
- 圆角 8-12px
- 系统蓝色 #007aff
- 浅灰背景 #f5f5f7
- SF Pro Text 字体
