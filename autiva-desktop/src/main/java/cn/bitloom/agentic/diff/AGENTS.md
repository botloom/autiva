# Diff 包

## 概述
本包实现了 Diff 生成和管理功能，用于编码智能体的文件修改审核。

## 核心类

### FileDiff
文件 Diff 数据模型（record 类型）。

**字段：**
- `id`: Diff 唯一ID（UUID）
- `filePath`: 文件路径
- `hunks`: Diff Hunk 列表
- `isCreate`: 是否是新建文件
- `isDelete`: 是否是删除文件

**嵌套类型：**
- `Hunk`: Diff Hunk（包含行号范围和 Diff 行列表）
- `DiffLine`: Diff 行（包含类型和内容）
- `Type`: 行类型枚举（ADD/REMOVE/CONTEXT）

### DiffService
Diff 服务（Spring `@Component`），生成和管理文件 Diff。

**核心方法：**
- `FileDiff generateDiff(Path filePath, String oldContent, String newContent)`: 生成 Diff
- `List<FileDiff> getPendingDiffs()`: 获取待审核 Diff 列表
- `void approveDiff(String diffId)`: 批准 Diff
- `void rejectDiff(String diffId)`: 拒绝 Diff

**设计：**
- 使用 `ConcurrentHashMap` 存储待审核 Diff
- 简化的行级 Diff 实现（可后续替换为更精确的算法）

## 设计模式
- 服务模式：DiffService 封装 Diff 生成逻辑
- 数据模型模式：FileDiff 使用 record 不可变模型

## 注意事项
1. DiffService 使用简化的行级 Diff，后续可替换为更精确的算法（如 Myers diff）
2. 待审核 Diff 存储在内存中，应用重启后丢失
3. Diff 生成后需要用户审核（通过 DiffReviewCard）
