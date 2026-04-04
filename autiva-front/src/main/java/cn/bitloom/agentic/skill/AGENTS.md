# Skill 包

## 概述
本包实现了技能管理系统，支持动态加载和管理 AI 技能（专业知识）。支持通过ZIP包导入技能。

## 核心类

### Skill
技能实体类。

**字段：**
- `name`: 技能名称（必填，小写字母、数字、连字符，最长64字符）
- `description`: 技能描述（必填，最长1024字符）
- `license`: 许可证（可选）
- `compatibility`: 兼容性说明（可选，最长500字符）
- `metadata`: 元数据键值对（可选）
- `content`: 技能内容（Markdown 格式）
- `filePath`: 技能文件路径

### SkillManager
技能管理器，负责技能的加载、管理和持久化。

**功能：**
- 扫描技能目录（`~/.autiva/skills/`）
- 解析 SKILL.md 文件（YAML frontmatter格式）
- 提取技能元数据和内容
- 支持从ZIP文件导入技能
- 支持从URL下载ZIP并导入技能
- 验证技能名称和描述格式
- 管理已加载技能的内存缓存

**文件结构：**
```
~/.autiva/skills/
├── skill-name-1/
│   ├── SKILL.md          # 必需：元数据 + 指令
│   ├── scripts/          # 可选：可执行代码
│   ├── references/       # 可选：文档
│   ├── assets/           # 可选：模板、资源
│   └── ...               # 其他文件或目录
├── skill-name-2/
│   └── SKILL.md
└── ...
```

**SKILL.md 格式：**
```markdown
---
name: skill-name
description: "技能描述。说明技能功能和何时使用。"
license: Apache-2.0
compatibility: "需要Python 3.8+"
metadata:
  author: example-org
  version: "1.0"
---

# 技能标题

技能内容...
```

**Frontmatter 字段：**

| 字段 | 必需 | 约束 |
|------|------|------|
| name | 是 | 最长64字符。小写字母、数字、连字符。不能以连字符开头或结尾。 |
| description | 是 | 最长1024字符。非空。描述技能功能和何时使用。 |
| license | 否 | 许可证名称或引用捆绑的许可证文件。 |
| compatibility | 否 | 最长500字符。环境要求（产品、系统包、网络访问等）。 |
| metadata | 否 | 额外元数据的键值映射。 |

**ZIP 导入：**
- ZIP文件必须包含SKILL.md文件
- 支持ZIP根目录或子目录中包含SKILL.md
- 自动解压到技能目录
- 验证SKILL.md格式

**核心方法：**
- `loadSkills()`: 重新加载所有技能
- `getAllSkills()`: 获取所有技能列表
- `getDescription()`: 获取所有技能的描述摘要
- `getContent(name)`: 获取指定技能的完整内容
- `importSkillFromZip(Path)`: 从本地ZIP文件导入
- `importSkillFromUrl(String)`: 从URL下载ZIP并导入
- `saveSkill(Skill)`: 保存技能
- `deleteSkill(String)`: 删除技能

## 使用示例

### 获取技能描述
```java
String descriptions = skillManager.getDescription();
// 返回：
// Available skills:
// - skill1: 技能1描述
// - skill2: 技能2描述
```

### 获取技能内容
```java
String content = skillManager.getContent("skill-name");
// 返回完整的技能内容
```

### 在智能体中使用
```java
@Override
protected String getSystemPrompt() {
    return """
        你是智能体，可以使用以下技能：
        
        $skill$
        """;
}

// 在 ChatClient 中注入
.prompt()
.system(s -> s.text(this.getSystemPrompt())
    .param("skill", skillManager.getDescription()))
```

### 从ZIP导入技能
```java
Path zipPath = Paths.get("skill-package.zip");
Skill skill = skillManager.importSkillFromZip(zipPath);
```

### 从URL下载并导入技能
```java
String url = "https://example.com/skill.zip";
Skill skill = skillManager.importSkillFromUrl(url);
```

## 技能创建流程

### 通过ZIP导入（推荐）
1. 点击"导入"按钮
2. 选择本地ZIP包文件
3. 系统自动解压并验证

### 手动创建
1. 在 `~/.autiva/skills/` 下创建目录
2. 目录名即为技能名
3. 创建 SKILL.md 文件
4. 添加 frontmatter 和内容
5. 调用 `skillManager.loadSkills()` 刷新

## 注意事项
1. 技能目录必须包含 SKILL.md 文件
2. frontmatter 必须使用 `---` 包围
3. name 和 description 是必填字段
4. 技能名称必须符合命名规范
5. 技能内容使用 Markdown 格式
6. 修改技能后需要重新加载
