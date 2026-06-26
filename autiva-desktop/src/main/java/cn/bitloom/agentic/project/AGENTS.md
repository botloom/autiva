# Project 包

## 概述
本包实现了项目管理系统，用于编码智能体（coder）场景下的项目注册、查询和 Git 信息读取。

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

## 设计模式
- Repository 模式：ProjectRegistry 管理项目列表的增删改查
- 服务模式：GitService 封装 Git 命令调用
- 持久化模式：JSON 文件持久化

## 注意事项
1. ProjectRegistry 在构造时自动加载持久化数据
2. GitService 使用 ProcessBuilder 执行 git 命令，不依赖 JGit
3. 项目路径验证：registerLocal 时检查路径是否为有效目录
4. 线程安全：ProjectRegistry 使用 CopyOnWriteArrayList
