# Skill 包

## 概述
本包实现了技能管理系统，采用 spring-ai-agent-utils 的 Skill record 设计。支持动态加载和管理 AI 技能（专业知识），支持通过 ZIP 包导入技能，支持从文件系统/JAR/类路径加载技能。

## 核心类

### Skill (record)
技能实体类，采用 record 设计，包含 frontMatter 和 content。

**字段：**
- `basePath`: 技能所在目录路径
- `frontMatter`: YAML 前置元数据（Map<String, Object>）
- `content`: 技能内容（Markdown 格式）

**派生方法：**
- `name()`: 从 frontMatter 获取技能名称
- `description()`: 从 frontMatter 获取技能描述
- `toXml()`: 将 frontMatter 和 basePath 格式化为 XML（用于 AI 工具描述，便于模型定位技能根目录）

### SkillManager
技能管理器，统一负责技能的加载、管理、持久化和 AI 工具回调构建。

**加载功能：**
- `loadSkills()`: 重新加载所有技能（从默认技能目录）
- `loadDirectory(String)`: 从文件系统目录递归加载 SKILL.md
- `loadDirectories(List<String>)`: 从多个目录加载技能
- `loadResource(Resource...)`: 从 Spring Resource 加载技能（支持文件系统和 JAR）
- `loadResources(List<Resource>)`: 从多个 Resource 加载技能
- 支持从 JAR 包内加载技能（自动扫描 classpath）
- 使用 MarkdownParser 解析 YAML frontmatter

**管理功能：**
- `getAllSkills()`: 获取所有技能列表
- `getSkill(name)`: 获取指定技能
- `getDescription()`: 获取所有技能的描述摘要
- `getContent(name)`: 获取指定技能的完整内容
- `importSkillFromZip(Path)`: 从本地 ZIP 文件导入
- `importSkillFromUrl(String)`: 从 URL 下载 ZIP 并导入
- `saveSkill(Skill)`: 保存技能
- `deleteSkill(String)`: 删除技能

**AI 工具功能：**
- `buildToolCallback()`: 构建 FunctionToolCallback，将技能作为 AI 工具提供给智能体
- 内部类 `SkillsFunction`: 实现 Function<SkillsInput, String>，处理技能调用
- 内部类 `SkillsInput`: 工具输入 record，包含 command（技能名称）

## AI 工具功能

技能通过 ToolCallbacks 模式注册，由 SkillManager 直接提供：

- `buildToolCallback()`: 构建 FunctionToolCallback，将技能作为 AI 工具提供给智能体
- 内部类 `SkillsFunction`: 实现 Function<SkillsInput, String>，处理技能调用
- 内部类 `SkillsInput`: 工具输入 record，包含 command（技能名称）

在 MainAgent 中通过 `skillManager.buildToolCallback()` 获取 ToolCallback 并注册到 ChatClient。

## SKILL.md 格式
```markdown
---
name: skill-name
description: "技能描述"
license: Apache-2.0
compatibility: "需要Python 3.8+"
metadata:
  author: example-org
  version: "1.0"
---

# 技能标题

技能内容...
```

## 注意事项
1. 技能目录必须包含 SKILL.md 文件
2. frontmatter 必须使用 `---` 包围
3. name 和 description 是必填字段
4. 修改技能后需要重新加载
5. MarkdownParser 替代了原来的 SnakeYAML 解析
