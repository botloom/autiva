# Store 包

## 概述
本包实现了全局状态存储，使用 JavaFX 属性实现响应式状态管理。

## 核心类

### Store
全局状态存储类，包含应用级别的响应式属性。

**属性：**
- `statusText`: 状态文本（StringProperty）
- `browserPath`: 浏览器路径（ObjectProperty<Path>）
- `selectedModel`: 当前选择的模型（ObjectProperty<ModelEnum>，默认值：DEEPSEEK）

### ToolUIBridge
工具UI桥接组件，实现工具处理器与 WebView 之间的 Java-JavaScript 双向通信。

**功能：**
- `setWebEngine(WebEngine)`: 注册 WebEngine 引用（由 HomePageController 调用）
- `showQuestions(questionsJson, answerFuture)`: 在 WebView 中显示问题，阻塞等待用户回答
- `onQuestionAnswered(questionId, answersJson)`: JavaScript 回调，用户回答问题时触发
- `showTodos(todosJson)`: 在 WebView 中显示待办事项列表

**通信机制：**
- Java → JavaScript: 通过 `webEngine.executeScript()` 调用 JavaScript 函数
- JavaScript → Java: 通过 `JSObject.setMember()` 暴露 Java 对象给 JavaScript

**线程安全：**
- JavaScript 执行通过 `Platform.runLater()` 确保在 JavaFX 应用线程执行
- 使用 `CompletableFuture` 实现跨线程等待

## 使用示例

### 设置状态
```java
Store.statusText.set("正在处理...");
Store.statusText.set("就绪");
```

### 绑定状态
```java
statusBarLabel.textProperty().bind(Store.statusText);
```

### 监听状态变化
```java
Store.statusText.addListener((obs, oldVal, newVal) -> {
    System.out.println("状态变化: " + oldVal + " -> " + newVal);
});
```

### 设置浏览器路径
```java
Store.browserPath.set(Paths.get("C:\\path\\to\\browser.exe"));
```

### 获取浏览器路径
```java
Path path = Store.browserPath.get();
```

### 获取当前选择的模型
```java
ModelEnum model = Store.selectedModel.get();
```

### 监听模型选择变化
```java
Store.selectedModel.addListener((obs, oldVal, newVal) -> {
    System.out.println("模型切换: " + oldVal + " -> " + newVal);
});
```

## 响应式特性

### 自动更新 UI
```java
// 在控制器中绑定
statusBarLabel.textProperty().bind(Store.statusText);

// 任何地方修改状态
Store.statusText.set("新状态");  // UI 自动更新
```

### 监听器模式
```java
Store.browserPath.addListener((obs, oldVal, newVal) -> {
    // 浏览器路径变化时执行
    configManager.setBrowserPath(newVal.toString());
});
```

## 设计模式
- 单例模式：静态属性，全局访问
- 观察者模式：属性变化通知监听器

## 扩展指南
添加新的全局状态：
```java
public class Store {
    public static final StringProperty newStatus = new SimpleStringProperty();
    public static final ObjectProperty<SomeType> someValue = new SimpleObjectProperty<>();
}
```

## 注意事项
1. 所有属性都是静态的，全局共享
2. 使用 JavaFX 属性实现响应式
3. 属性变化自动通知绑定的 UI 组件
4. 线程安全：JavaFX 属性在应用线程使用
