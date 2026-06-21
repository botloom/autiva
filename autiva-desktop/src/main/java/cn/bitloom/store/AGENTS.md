# Store 包

## 概述
本包实现了全局状态存储，使用 JavaFX 属性实现响应式状态管理。Store 是全局 UI 状态的唯一真相源，各组件通过 Store 属性实现跨组件通信，避免 ViewModel 之间的直接依赖。

## 核心类

### Store
全局状态存储类，包含应用级别的响应式属性。

**属性：**
- `statusText`: 状态文本（StringProperty）— 绑定到状态栏标签，各组件设置状态提示
- `selectedModel`: 当前选择的模型（ObjectProperty\<ModelTypeEnum\>，默认值：DEEPSEEK）— 双向绑定到模型选择器，切换会话时同步更新
- `currentSessionId`: 当前会话 ID（StringProperty）— 会话切换时更新，SideBarController 监听以刷新历史列表
- `currentAgent`: 当前智能体（ObjectProperty\<String\>，默认值："default"）— 双向绑定到智能体选择器
- `isStreaming`: 流式生成状态（BooleanProperty，默认值：false）— 控制发送/暂停按钮的显示切换
- `isPaused`: 暂停状态（BooleanProperty，默认值：false）— 与 isStreaming 配套控制 UI 状态
- `currentRoute`: 当前路由路径（StringProperty）— Router 导航时更新，其他组件可监听响应路由变化
- `refreshHistory`: 侧边栏历史列表刷新信号（BooleanProperty，默认值：false）— 翻转此值触发 SideBarController 刷新历史列表，用于聊天过程中更新会话标题

**使用位置：**
- `ButtonBarController` — 绑定 statusText 到状态栏标签
- `HomePageViewModel` — 设置所有属性（statusText、selectedModel、currentSessionId、isStreaming、isPaused）
- `HomePageController` — 双向绑定 selectedModel、currentAgent；监听 isStreaming、isPaused
- `SideBarController` — 监听 currentSessionId 刷新历史列表；监听 refreshHistory 刷新历史列表；读取 currentAgent、selectedModel 创建会话
- `SettingsPageViewModel` — 设置 statusText
- `Router` — 设置 currentRoute、statusText

### ToolUIBridge
工具UI桥接组件，实现工具处理器与 JavaFX 聊天容器之间的通信。

**功能：**
- `setOnNodeAdded(Consumer<Node>)`: 注册节点添加回调（由 HomePageController 调用）
- `showQuestions(questionsJson, answerFuture)`: 创建 QuestionCard 并添加到聊天容器，阻塞等待用户回答
- `onQuestionAnswered(questionId, answersJson)`: QuestionCard 回调，用户回答问题时触发
- `showTodos(todosJson)`: 创建 TodoCard 并添加到聊天容器
- `createTaskCard(taskId, title)`: 创建 TaskCard 并添加到聊天容器

**通信机制：**
- 工具处理器 → ToolUIBridge: 调用 showQuestions/showTodos/createTaskCard 方法
- ToolUIBridge → 聊天容器: 通过 onNodeAdded 回调添加 JavaFX 节点
- QuestionCard → ToolUIBridge: 用户回答问题时直接调用 onQuestionAnswered

**线程安全：**
- 节点创建通过 `Platform.runLater()` 确保在 JavaFX 应用线程执行
- 使用 `CompletableFuture` 实现跨线程等待
- `pendingQuestions` 使用 `ConcurrentHashMap` 支持并发访问

## 使用示例

### 设置状态文本
```java
Store.statusText.set("正在处理...");
Store.statusText.set("就绪");
```

### 绑定状态到 UI
```java
statusBarLabel.textProperty().bind(Store.statusText);
```

### 监听状态变化
```java
Store.statusText.addListener((obs, oldVal, newVal) -> {
    System.out.println("状态变化: " + oldVal + " -> " + newVal);
});
```

### 获取当前选择的模型
```java
ModelTypeEnum model = Store.selectedModel.get();
```

### 监听模型选择变化
```java
Store.selectedModel.addListener((obs, oldVal, newVal) -> {
    System.out.println("模型切换: " + oldVal + " -> " + newVal);
});
```

### 双向绑定模型选择器
```java
modelSelector.valueProperty().bindBidirectional(Store.selectedModel);
```

### 监听会话切换
```java
Store.currentSessionId.addListener((obs, oldVal, newVal) -> {
    // 刷新历史列表等
});
```

### 监听路由变化
```java
Store.currentRoute.addListener((obs, oldVal, newVal) -> {
    // 响应路由变化，如更新侧边栏高亮
});
```

### 监听流式生成状态
```java
Store.isStreaming.addListener((obs, oldVal, newVal) -> {
    boolean streaming = newVal != null && newVal;
    // 切换发送/暂停按钮显示
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

### 跨组件通信
```java
// HomePageViewModel 中设置状态
Store.isStreaming.set(true);

// HomePageController 中监听状态（无需直接依赖 ViewModel 的属性）
Store.isStreaming.addListener((obs, oldVal, newVal) -> {
    // 更新 UI
});
```

## 设计模式
- 单例模式：静态属性，全局访问
- 观察者模式：属性变化通知监听器
- 真相源模式：Store 是全局 UI 状态的唯一真相源，避免 ViewModel 之间的直接依赖

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
4. 线程安全：JavaFX 属性变化监听器在触发 set() 的线程上执行，跨线程更新 UI 需使用 `Platform.runLater()`
5. Store 仅存储 UI 状态，业务逻辑（如会话管理、消息处理）仍由 ViewModel 负责
