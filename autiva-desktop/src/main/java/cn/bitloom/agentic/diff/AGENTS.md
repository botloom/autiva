# Diff 包

## 概述
本包实现了 Diff 生成和管理功能，用于编码智能体的文件修改审核与撤销。

## 核心类

### FileDiff
文件 Diff 数据模型（record 类型）。

**字段：**
- `id`: Diff 唯一ID（UUID）
- `filePath`: 文件路径
- `hunks`: Diff Hunk 列表
- `isCreate`: 是否是新建文件
- `isDelete`: 是否是删除文件
- `oldContent`: 修改前的文件内容（用于撤销时回滚；新建文件时为空字符串）

**嵌套类型：**
- `Hunk`: Diff Hunk（包含行号范围和 Diff 行列表）
- `DiffLine`: Diff 行（包含类型和内容）
- `Type`: 行类型枚举（ADD/REMOVE/CONTEXT）

### DiffService
Diff 服务（Spring `@Component`），生成和管理文件 Diff，使用 JGit HistogramDiff 算法生成真正的行级 diff。

**核心方法：**
- `FileDiff generateDiff(Path filePath, String oldContent, String newContent)`: 生成 Diff（oldContent=null 表示新建文件；newContent=null 表示删除文件），将 oldContent 存入 FileDiff 用于撤销，并通过 `EventBus.publishOut` 发布 `DiffEvent` 通知 UI 直接使用事件中的 FileDiff 数据追加卡片
- `List<FileDiff> getPendingDiffs()`: 获取待审核 Diff 列表
- `void approveDiff(String diffId)`: 批准 Diff（仅从待审核列表移除，不修改文件）
- `void rejectDiff(String diffId)`: 拒绝 Diff 并回滚文件内容（新建文件 → 删除；修改文件 → 用 oldContent 覆盖恢复）
- `FileDiff recomputeDiff(FileDiff original)`: 重新计算 Diff（基于当前文件内容与 original.oldContent 用 JGit 重新生成 hunks）。**当前 diff 显示不再调用此方法**（直接使用 DiffEvent 原始 diff），方法保留供进化功能或其他场景使用。不存储/发布事件，读取失败时返回原始 diff
- `List<FileDiff> scanWorkingTreeDiffs(Path projectPath)`: 扫描 Git 工作区未提交变更。用 JGit status 获取 modified/untracked/missing 文件，对比 HEAD 版本生成 FileDiff 列表。不存入 pendingDiffs。**当前 diff 显示不再调用此方法**（改用 DiffEvent 数据源），方法保留仅供进化功能（L4 自优化）使用。非 Git 仓库返回空列表
- `void approveFileDiff(FileDiff diff)`: 批准文件 Diff（直接基于 FileDiff 对象，从 pendingDiffs 移除，不修改文件）
- `void rejectFileDiff(FileDiff diff)`: 拒绝文件 Diff 并回滚文件内容（直接基于 FileDiff 对象，新建文件删除，修改文件用 oldContent 覆盖）
- `readHeadContent(Repository, ObjectId, String)`: 私有方法，读取 HEAD 版本中指定文件的内容

**设计：**
- 使用 `ConcurrentHashMap` 存储待审核 Diff
- `generateHunks` 使用 JGit `HistogramDiff` 算法（项目已依赖 org.eclipse.jgit:7.1.0），对代码重构场景输出比 MyersDiff 更可读，无需引入额外 diff 库
- DiffEvent 通过 EventBus 发布，由 `HomePageController.subscribeDiffEvents` 订阅，UI 直接使用事件中的 FileDiff 数据追加卡片（不再扫描 git 工作区）

## 设计模式
- 服务模式：DiffService 封装 Diff 生成逻辑
- 数据模型模式：FileDiff 使用 record 不可变模型
- 事件驱动模式：通过 EventBus 发布 DiffEvent 解耦工具层与 UI 层

## 注意事项
1. DiffService 使用 JGit HistogramDiff 生成行级 diff，REPLACE 类型的 Edit 会被拆成 REMOVE + ADD 两段以匹配既有数据模型
2. 待审核 Diff 存储在内存中，应用重启后丢失
3. 撤销操作通过 `ContentPanelController.showDiffView` 的"撤销"按钮触发，调用 `rejectDiff` 自动回滚文件；`DiffReviewCard`（聊天卡片式审核）保留为可选组件，当前未接入
4. WriteTool/EditTool 在写文件**之后**调用 `generateDiff`（非阻塞），diff 失败不影响写入结果
