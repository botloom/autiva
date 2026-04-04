# Components 目录

## 概述
本目录包含可复用的 UI 组件 FXML 文件。

## 组件列表

### ButtonBar.fxml
底部按钮栏组件。

**功能：**
- 显示侧边栏切换按钮
- 动态按钮容器（根据页面变化）
- 状态标签显示

**控制器：** ButtonBarController

**结构：**
```
HBox (button-bar)
├── Button (sidebarButton) - 侧边栏切换按钮
├── HBox (dynamicButtonContainer) - 动态按钮容器
└── Label (statusBarLabel) - 状态标签
```

**样式类：**
- `.button-bar`: 容器样式
- `.button-bar__toggle-btn`: 切换按钮样式
- `.button-bar__status`: 状态标签样式

### SideBar.fxml
侧边栏组件。

**功能：**
- 页面导航菜单
- 显示/隐藏切换
- 当前页面高亮

**控制器：** SideBarController

**结构：**
```
VBox (sideBar)
└── VBox (sidebar__content)
    ├── HBox (homeOption) - 首页
    ├── HBox (skillOption) - 技能
    ├── HBox (mcpOption) - MCP
    ├── HBox (taskOption) - 任务
    ├── HBox (workflowOption) - 工作流
    ├── Region (sidebar__spacer) - 弹性空间
    └── HBox (settingsOption) - 设置
```

**样式类：**
- `.sidebar`: 容器样式
- `.sidebar__option`: 菜单项样式
- `.sidebar__option--active`: 激活状态样式
- `.sidebar__icon`: 图标样式
- `.sidebar__label`: 标签样式

### MdEditor.fxml
Markdown 编辑器组件。

**功能：**
- Markdown 内容编辑
- 实时预览
- 字数统计
- 状态显示
- 预览切换

**控制器：** MdEditorController

**结构：**
```
VBox (md-editor)
├── HBox (headerBox) - 头部栏
│   ├── TextField (nameField) - 名称/标题输入
│   ├── Button - 预览按钮
│   ├── Button (saveButton) - 保存按钮
│   └── Button (cancelButton) - 取消按钮
├── SplitPane (editorSplitPane) - 编辑区域
│   ├── TextArea (contentArea) - 编辑器
│   └── WebView (previewWebView) - 预览
└── HBox (footer) - 底部栏
    ├── Label (wordCountLabel) - 字数统计
    └── Label (statusLabel) - 状态
```

**样式类：**
- `.md-editor`: 容器样式
- `.md-editor__header`: 头部栏样式
- `.md-editor__name-field`: 名称输入框样式
- `.md-editor__btn`: 按钮样式
- `.md-editor__split-pane`: 分割面板
- `.md-editor__textarea`: 文本区域
- `.md-editor__footer`: 底部栏
- `.md-editor__word-count`: 字数统计
- `.md-editor__status`: 状态

**滚动条样式：**
- 宽度 6px，符合 Apple 设计规范
- 半透明圆角滑块，悬停时加深
- 透明轨道和按钮，极简风格

**API 方法：**
- `setTitle(String)`: 设置标题提示文本
- `setName(String)`: 设置名称
- `getName()`: 获取名称
- `setContent(String)`: 设置内容
- `getContent()`: 获取内容
- `setStatus(String)`: 设置状态文本
- `setStage(Stage)`: 设置对话框 Stage
- `setOnSaveCallback(Consumer<MdEditorData>)`: 设置保存回调

**使用示例：**
```java
FXMLLoader loader = new FXMLLoader(new ClassPathResource("cn/bitloom/components/MdEditor.fxml").getURL());
Scene scene = new Scene(loader.load(), 900, 700);

MdEditorController controller = loader.getController();
controller.setStage(dialogStage);
controller.setTitle("技能名称");

if (skill != null) {
    controller.setName(skill.getName());
    controller.setContent(skill.getContent());
}

controller.setOnSaveCallback(data -> {
    String name = data.name();
    String content = data.content();
});

dialogStage.showAndWait();
```

## 使用方式

在 FXML 中引用组件：
```xml
<fx:include source="components/SideBar.fxml" fx:id="sideBar"/>
<fx:include source="components/ButtonBar.fxml" fx:id="buttonBar"/>
```

作为对话框加载：
```java
FXMLLoader loader = new FXMLLoader(new ClassPathResource("cn/bitloom/components/MdEditor.fxml").getURL());
Scene scene = new Scene(loader.load(), 900, 700);
```

## 设计原则
1. 组件独立可复用
2. 每个组件有独立的控制器
3. 样式通过 CSS 文件定义
4. 使用 BEM 命名规范

## 注意事项
1. 组件通过 fx:include 引入或作为独立对话框加载
2. 控制器需要在 index.fxml 中配置（对于 include 方式）
3. 样式文件路径使用相对路径
4. 组件 ID 需要与控制器中的 @FXML 字段对应
