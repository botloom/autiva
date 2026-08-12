# Project 包

## 概述
本包实现了项目管理系统，用于项目注册、查询和 Git 信息读取，供 coder 模式下的项目管理功能使用。

## 核心类

### ProjectInfo
项目信息数据模型（record 类型）。

**字段：**
- `id`: 项目唯一ID（UUID）
- `name`: 项目名称
- `path`: 项目本地路径
- `gitBranch`: 当前 Git 分支（可为 null）
- `createdAt`: 创建时间（Instant）

### ProjectRegistry
项目注册表管理器（Spring `@Component`），管理已注册的项目列表。

**持久化：** `~/.autiva/projects/registry.json`

**核心方法：**
- `List<ProjectInfo> listProjects()`: 列出所有已注册项目
- `Optional<ProjectInfo> findById(String id)`: 根据 ID 查找项目
- `ProjectInfo createProject(String name)`: 创建新项目（创建空目录并注册）
- `ProjectInfo registerLocal(String path, String name)`: 注册本地文件夹为项目
- `void removeProject(String id)`: 移除项目（仅从注册表移除，不删除文件）
- `ProjectInfo refreshBranch(String id)`: 刷新项目的 Git 分支信息

**设计：**
- 使用 `CopyOnWriteArrayList` 保证线程安全
- 每次修改后自动持久化到磁盘
- 构造时自动从磁盘加载

### GitService
Git 服务（Spring `@Component`），仅查询 Git 信息，不支持修改操作。

**核心方法：**
- `Optional<String> getCurrentBranch(Path projectPath)`: 获取指定路径的当前 Git 分支
- `boolean isGitRepository(Path projectPath)`: 检查路径是否是 Git 仓库

**实现：**
- 通过执行 `git rev-parse --abbrev-ref HEAD` 命令获取分支
- 5 秒超时保护
- 自动检测 .git 目录存在性

### FileTreeService
文件树构建服务（Spring `@Component`），为编辑器面板构建项目目录树。使用 `LazyTreeItem`（位于 `cn.bitloom.node.project` 包）替代原生 `TreeItem<Path>`，实现正确的目录展开行为和懒加载。

**核心方法：**
- `TreeItem<Path> buildFileTree(Path rootPath)`: 构建文件树根节点（`LazyTreeItem`），并通过 `setExpanded(true)` 触发首次子节点加载
- `private void loadChildren(TreeItem<Path> parent)`: 加载子节点（私有方法，作为 `LazyTreeItem` 的展开回调）

**loadChildren 实现细节：**
- 使用 `Files.list()` 流式读取目录内容
- 排序规则：目录优先（`!Files.isDirectory(p)` 作为首要排序键）+ 文件名字母序（大小写不敏感）
- 通过 `ToolUtils.isIgnoredPath(child)` 过滤忽略路径（如 `target/`、`.git/` 等）
- 为每个子节点创建 `LazyTreeItem`，传入 `this::loadChildren` 作为回调，实现递归懒加载

**相关类：LazyTreeItem**（`cn.bitloom.node.project.LazyTreeItem`）

继承 `javafx.scene.control.TreeItem<Path>`，解决 JavaFX 默认 `TreeItem` 在懒加载场景下深层目录无法展开的问题。

**核心机制：**
- 重写 `isLeaf()`：返回 `!Files.isDirectory(getValue())`，基于文件系统实际类型判断，与 children 加载状态解耦
- 构造时接收 `Consumer<TreeItem<Path>>` 回调（通常为 `FileTreeService::loadChildren`）
- 监听 `expandedProperty`：首次展开时（`isNowExpanded && !loaded`）调用回调加载子节点，`loaded` 标志位避免重复加载

**问题根因（使用原生 TreeItem 的死锁）：**
原生 `TreeItem.isLeaf()` 默认基于 `getChildren().isEmpty()` 判断。在懒加载场景下，非根目录的 children 尚未加载（为空），因此被误判为叶子节点 → TreeView 不渲染展开箭头 → 用户无法点击展开 → `expandedProperty` 监听器无法触发 → children 永远为空。`LazyTreeItem` 通过将 `isLeaf()` 与 children 状态解耦打破此死锁。

**优点：**
- Repository 模式：ProjectRegistry 管理项目列表的增删改查
- 服务模式：GitService 封装 Git 命令调用
- 持久化模式：JSON 文件持久化
- 懒加载模式：FileTreeService + LazyTreeItem 实现文件树按需加载，避免一次性加载大型项目所有文件

## Git 状态着色子包（`cn.bitloom.project.git`）

用于目录树与文件内容视图的 Git 工作区状态显示（新增绿 / 修改蓝 / 未跟踪红）及文件变化自动刷新。

### GitFileStatus（枚举）
文件 Git 状态：`ADDED`（暂存新增 A）、`MODIFIED`（修改 M）、`UNTRACKED`（未跟踪 ??）。

### GitStatusService（@Component）
基于 jgit（非 CLI）查询工作区状态。
- `Map<Path,GitFileStatus> queryStatusMap(Path root)`: 返回文件「绝对规范化路径 → 状态」映射；非 Git 仓库返回空 map。状态优先级 ADDED>MODIFIED>UNTRACKED。
- `Set<Path> collectChangedDirs(Map)`: 推导含改动的目录绝对路径集合。
- `Map<Integer,GitFileStatus> diffLineStatus(Path root, Path filePath)`: 计算单文件工作区相对 HEAD 的行级改动（键：工作区 0-based 行号 → 状态）。INSERT→ADDED，REPLACE→MODIFIED，DELETE→删除锚定到紧随其后的行（MODIFIED）；未跟踪新文件视为全部新增。供编辑器行号处按行着色。
- `Set<Path> collectWatchDirs(Path root)`: 递归列出需监听的子目录（过滤忽略目录）。
- `boolean isIgnoredPath(Path)`: 判断是否应忽略的路径（监听事件过滤用）。
- 内置 `IGNORED_DIR_NAMES`：`.git`、`node_modules`、`target`、`build` 等。

### ProjectStatusStore（@Component）
当前展示项目的 Git 状态共享存储（bean）。
- 持有 `projectRoot`、`statusMap`、`changedDirs`。
- `update(root, map)`: 注入新状态并翻转 `refreshSignal`（BooleanProperty，风格同 `Store.refreshHistory`）触发 UI 刷新。
- `GitFileStatus statusOf(Path)`、`boolean isDirChanged(Path)`: 供单元格/视图查询。

### ProjectFileWatcherService（@Component）
基于 `java.nio.file.WatchService` 递归监听项目根目录变化（`watch(root)`），去抖（600ms）后在后台重算 Git 状态并 `projectStatusStore.update(...)` 触发刷新。
- `watch(root)`/`stop()`: 开始/停止监听；仅在打开目录树时启用。
- 增量监听新建目录并补注册；跳过忽略目录。
- `@PreDestroy destroy()`: 应用关闭时释放资源。

## 设计模式
- Repository 模式：ProjectRegistry 管理项目列表的增删改查
- 服务模式：GitService 封装 Git 命令调用
- 持久化模式：JSON 文件持久化
- 懒加载模式：FileTreeService + LazyTreeItem 实现文件树按需加载，避免一次性加载大型项目所有文件

## 注意事项
1. ProjectRegistry 在构造时自动加载持久化数据
2. GitService 使用 ProcessBuilder 执行 git 命令，不依赖 JGit（GitStatusService 用 JGit 走内存计算，二者职责不重叠）
3. 项目路径验证：registerLocal 时检查路径是否为有效目录
4. 线程安全：ProjectRegistry 使用 CopyOnWriteArrayList
5. FileTreeService 的 `loadChildren` 为私有方法，仅通过 `LazyTreeItem` 的展开回调间接调用
6. LazyTreeItem 位于 `cn.bitloom.node.project` 包（不在本包），但因与 FileTreeService 紧密耦合，在此一并说明
7. 目录树着色通过在 `SideBarController.showProjectTree` 设置 `FileTreeCell.setStatusStore(projectStatusStore)` 启用；文件视图（CoderEditorPanelController）订阅 `projectStatusStore.refreshSignal` 同步刷新
