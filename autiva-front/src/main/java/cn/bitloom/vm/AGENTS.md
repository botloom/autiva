# VM 包 (ViewModel)

## 概述
本包实现了 MVVM 架构中的 ViewModel 层，负责管理视图状态和业务逻辑。

## 核心类

### HomePageViewModel
主页视图模型，管理聊天交互状态和消息来源。

**属性：**
- `sourceProperty`: 消息来源标识（StringProperty），如 "desktop-app"、"wechat" 等
- `assistantProperty`: 助手响应内容（StringProperty）
- `messagesProperty`: 历史消息内容（StringProperty）
- `isTypingProperty`: 是否正在输入（BooleanProperty）

**核心方法：**
- `init()`: 初始化时根据 source 查找或创建对应 Session，加载历史消息
- `sendMessage(message)`: 发送消息给智能体系统
- `loadHistoricalMessages()`: 从 Session 加载历史消息
- `clear()`: 清除状态

**消息来源设计：**
- 每个消息来源（desktop-app、wechat 等）对应一个独立的 Session
- 启动时通过 `sessionManager.getOrCreateForSource(source)` 获取或创建 Session
- 历史消息自动加载到 `messagesProperty` 供 UI 显示

**事件处理：**
- 监听来自 EventBus 的响应事件
- 流式处理 ChatResponse
- 更新 UI 状态

**使用示例：**
```java
// 绑定属性
assistantLabel.textProperty().bind(viewModel.getAssistantProperty());
messagesTextArea.textProperty().bind(viewModel.getMessagesProperty());

// 发送消息
viewModel.sendMessage("你好");

// 清除状态
viewModel.clear();
```

## 多消息来源架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      消息来源 (Sources)                          │
├─────────────────┬─────────────────┬─────────────────┬───────────┤
│  desktop-app    │     wechat      │    telegram     │   ...     │
└────────┬────────┴────────┬────────┴────────┬────────┴───┬───────┘
         │                │                │            │
         ▼                ▼                ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SessionManager                                │
│  findBySource(source) / getOrCreateForSource(source)             │
└────────┬────────────────┬────────────────┬─────────────────────┘
         │                │                │
         ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Session 层                                 │
│  desktop-app session | wechat session | telegram session | ...  │
└─────────────────────────────────────────────────────────────────┘
```

## 与其他组件的关系

1. **SessionManager**: 根据 source 管理和查找 Session
2. **EventBus**: 订阅智能体事件，更新状态
3. **Store**: 更新全局状态

## 设计模式
- MVVM 模式：分离视图和业务逻辑
- 响应式编程：使用 JavaFX 属性
- 观察者模式：属性变化通知
- 工厂模式：通过 `getOrCreateForSource` 获取 Session

## 注意事项
1. UI 更新必须在 JavaFX 应用线程执行
2. 使用 StringBuilder 累积流式响应
3. 属性绑定是双向的，注意循环更新
4. 消息来源标识需要全局唯一
5. 新增消息来源时只需设置 `sourceProperty`