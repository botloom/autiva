# Resources 根目录

## 概述
本目录是资源文件的根目录，包含主布局文件和应用配置。

## 文件列表

### index.fxml
应用主布局文件，使用系统默认标题栏。

**控制器：** IndexController

**结构：**
```
BorderPane (rootContainer) - 主内容区
└── HBox (center) - 内容区
    ├── fx:include (SideBar.fxml) - 侧边栏
    └── VBox
        ├── fx:include (ButtonBar.fxml) - 底部按钮栏
        ├── fx:include (HomePage.fxml) - 主页
        ├── fx:include (SettingsPage.fxml) - 设置页
        ├── fx:include (SkillPage.fxml) - 技能页
        ├── fx:include (GepPage.fxml) - 基因进化页
        └── fx:include (TaskPage.fxml) - 任务页
```

**样式表：** `@style/index.css`

**特点：**
- 使用系统默认标题栏
- 使用 fx:include 引入子组件
- 水平布局：侧边栏 + 主内容区
- 所有页面默认只显示主页

### application.yml
Spring Boot 配置文件。

**配置项：**
- 应用名称
- 服务器端口
- Spring AI 配置
- 日志配置

## 目录结构

```
cn/bitloom/
├── components/          # 可复用组件
│   ├── ButtonBar.fxml
│   └── SideBar.fxml
├── style/                 # 样式文件
│   ├── index.css
│   ├── button-bar.css
│   ├── side-bar.css
│   ├── home-page.css
│   ├── settings-page.css
│   ├── skill-page.css
│   ├── mcp-page.css
│   ├── task-page.css
│   ├── md-editor-dialog.css
│   ├── browser-dialog.css
│   ├── skill-editor-dialog.css
│   └── scroll-bar.css
├── images/              # 图像资源
│   ├── icon.png
│   ├── icon.svg
│   ├── icon-color.svg
│   ├── arrow-up.svg
│   ├── left.svg
│   ├── right.svg
│   ├── reload.svg
│   ├── plus.svg
│   └── voice.svg
├── view/                # 页面视图
│   ├── HomePage.fxml
│   ├── SettingsPage.fxml
│   ├── SkillPage.fxml
│   ├── McpPage.fxml
│   ├── TaskPage.fxml
│   ├── CanvasDialog.fxml
│   ├── BrowserDialog.fxml
│   ├── SkillEditorDialog.fxml
│   └── chat.html
└── index.fxml           # 主布局
```

## 资源加载

### FXML 加载
```java
FXMLLoader loader = new FXMLLoader(getClass().getResource("/cn/bitloom/index.fxml"));
loader.setControllerFactory(springContext::getBean);
Parent root = loader.load();
```

### CSS 加载
在 FXML 中引用：
```xml
stylesheets="@./css/home-page.css"
```

### 图像加载

```java
Image image = new Image(getClass().getResourceAsStream("/cn/bitloom/images/icon_bak.png"));
```

## 注意事项
1. 资源路径使用 `/` 分隔
2. FXML 文件与控制器一一对应
3. CSS 文件与 FXML 一一对应
4. 使用相对路径引用同级目录资源
