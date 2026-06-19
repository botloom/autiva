# Pet 包

## 概述
本包实现了桌面萌宠功能，当用户关闭主窗口时显示一个 Canvas 伪3D 的萌芽桌面宠物，随着聊天消息增加逐渐长大，根据用户聊天风格长成不同类型的植物。

## 核心类

### PetType
植物类型枚举，定义6种植物及其风格特征。

**枚举值：**
- `SUNFLOWER`（向日葵）— 活泼、emoji多、轻松
- `CACTUS`（仙人掌）— 简洁、技术向、代码多
- `IVY`（常春藤）— 创意、长消息、话题广
- `BAMBOO`（竹子）— 高频、短消息、高效
- `ROSE`（玫瑰）— 情感丰富、表达多样
- `BONSAI`（盆景）— 深思、哲学、长对话

### GrowthStage
生长阶段枚举，根据消息数量映射生长阶段。

**枚举值：**
- `SEED`（种子）— 0~5条消息
- `SPROUT`（萌芽）— 5~20条消息
- `SEEDLING`（幼苗）— 20~50条消息
- `YOUNG`（少年）— 50~100条消息
- `MATURE`（成熟）— 100+条消息

**核心方法：**
- `fromMessageCount(int count)`: 根据消息数量获取生长阶段
- `getProgress(int messageCount)`: 计算当前阶段内进度（0.0~1.0）

### ChatStyleAnalyzer
聊天风格分析器，基于5个维度分析用户聊天风格并映射到植物类型。

**维度：**
- `avgLength`: 平均消息长度（短0 → 长1）
- `codeRatio`: 代码比例（无代码0 → 大量代码1）
- `emojiRate`: emoji使用率（无emoji0 → 大量emoji1）
- `frequency`: 消息频率（低频0 → 高频1）
- `diversity`: 词汇多样性（重复0 → 多样1）

**核心方法：**
- `analyze(List<Message> messages)`: 分析消息列表，返回最匹配的 PetType
- `getDimensionScores(List<String> userTexts)`: 计算5个维度的得分（0~1）

**映射规则：**
每种植物有预设的维度偏好权重，计算加权得分，选最高的。例如：
- 代码比例高 + 消息短 → CACTUS
- emoji多 + 消息频率高 → SUNFLOWER
- 消息长 + 词汇多样性高 → IVY

### PetState
萌宠状态数据类，持久化到 `~/.autiva/pet/state.json`。

**字段：**
- `petType`: 植物类型（PetType）
- `totalMessages`: 总消息数
- `growthProgress`: 全局生长进度（0~1）
- `createdAt`: 创建时间戳
- `posX/posY`: 窗口位置
- `avgLength/codeRatio/emojiRate/frequency/diversity`: 风格维度得分（不持久化）

### PetStateManager
萌宠状态管理器（Spring Bean），负责加载/保存状态、监听消息更新、定期分析聊天风格。

**Spring 注解：** `@Component`

**核心方法：**
- `init()`: 加载持久化状态
- `onMessagesAdded(int messageCount)`: 通知新消息，更新状态
- `forceAnalyze()`: 强制重新分析聊天风格
- `reset()`: 重置萌宠状态
- `savePosition(double x, double y)`: 保存窗口位置

**聊天风格分析间隔：** 每10条消息重新分析一次

### PetRenderer
多植物 Canvas 渲染器，支持6种植物类型的5个生长阶段渲染。

**核心方法：**
- `render(GraphicsContext gc, PetType type, GrowthStage stage, double progress, double swayAngle)`: 渲染植物

**渲染架构：**
- 种子和萌芽阶段：所有植物通用
- 幼苗阶段开始分化：每种植物有独立的绘制方法
- 共用组件：`drawSoil()`, `drawLeafShape()`, `drawWideLeaf()`, `drawHeartLeaf()`
- 伪3D效果：通过渐变、阴影、大小透视模拟深度感

**视觉比例调整：**
- 植物整体缩放1.4倍（通过 `gc.scale(1.4, 1.4)`），使植物更旺盛、视觉更突出
- 土壤保持原始尺寸不变
- 各阶段植物叶子数量和尺寸增大：向日葵、玫瑰等茎叶植物增加叶片；仙人掌增加侧臂；常春藤增加茎和叶；盆景增加叶团层次

### DesktopPetStage
桌面萌宠窗口，透明置顶，支持丰富交互。

**交互行为：**
- **拖动**: 鼠标按下+拖动移动窗口位置，位置持久化
- **单击**: 恢复主窗口
- **右键菜单**: 恢复主窗口、生长详情、重置萌宠、退出
- **悬停**: Tooltip 显示生长阶段、消息数、植物类型

**动画：**
- 摇曳动画（Timeline，每50ms 更新 swayAngle）

**回调接口：**
- `setOnRestore(Runnable)`: 设置恢复主窗口回调
- `setOnExit(Runnable)`: 设置退出回调

## 与其他包的集成

### Store 集成
Store 新增3个萌宠相关属性：
- `petVisible`: BooleanProperty — 萌宠窗口是否可见
- `currentPetType`: ObjectProperty\<PetType\> — 当前植物类型
- `growthProgress`: DoubleProperty — 全局生长进度

### SessionManager 集成
在 `appendMessage()` 方法中，当向 USER 通道追加消息时：
1. 调用 `petStateManager.onMessagesAdded()` 更新萌宠状态
2. 同步更新 Store 中的 `currentPetType` 和 `growthProgress`

### AutivaApplication 集成
- 初始化 DesktopPetStage 并设置回调
- 关闭主窗口时隐藏主窗口并显示萌宠
- 单击萌宠恢复主窗口
- 启动时同步萌宠状态到 Store

## 持久化

```
~/.autiva/pet/
└── state.json    # 萌宠状态
```

## 设计模式
- 策略模式：ChatStyleAnalyzer 使用加权评分映射植物类型
- 观察者模式：Store 属性变化通知 UI 组件
- 回调模式：DesktopPetStage 通过 Runnable 回调与主窗口交互
