# Tool 包

## 概述
本包实现了工具管理系统，为智能体提供可调用的工具集。所有工具实现 Spring AI 的 Tool 注解规范，通过 ToolManager 统一管理。

## 核心接口

### ITool
工具接口，所有工具必须实现。

```java
public interface ITool {
    default Boolean isSafe(String path) {
        return false;
    }
}
```

## 统一返回对象

### ToolResult
所有工具的统一返回对象，封装工具执行结果。

**核心属性：**
- `success`: boolean - 执行是否成功
- `message`: String - 返回消息
- `data`: Object - 返回数据（可选）
- `error`: String - 错误信息（可选）

**静态工厂方法：**
- `ToolResult.success()`: 创建成功结果
- `ToolResult.success(String message)`: 创建带消息的成功结果
- `ToolResult.success(String message, Object data)`: 创建带消息和数据的成功结果
- `ToolResult.failure(String error)`: 创建失败结果
- `ToolResult.failure(String message, String error)`: 创建带消息的失败结果

**使用示例：**
```java
@Tool(name = "myTool", description = "我的工具")
public ToolResult myTool(String param) {
    log.info("[ToolCall] myTool - 执行操作: param={}", param);
    try {
        Object result = doSomething(param);
        return ToolResult.success("操作成功", result);
    } catch (Exception e) {
        log.error("[ToolCall] myTool - 执行失败", e);
        return ToolResult.failure("操作失败: " + e.getMessage());
    }
}
```

## 工具管理

### ToolManager
工具管理器，统一管理所有工具的注册和调用。

**核心属性：**
- `toolSets`: List<ITool> - 自动注入的所有工具实现
- `toolCallbackMap`: Map<String, ToolCallback> - 工具名称到回调的映射

**核心方法：**
- `init()`: 初始化，扫描所有 ITool 实现类并注册
- `getToolCallOption(toolList)`: 获取指定工具的调用选项
- `getToolCallbacks(toolSet)`: 获取工具回调列表
- `getToolDefinitions()`: 获取所有工具定义
- `call(toolName, toolArg)`: 直接调用工具
- `call(toolCall)`: 通过 ToolCall 对象调用工具

**初始化流程：**
```
@PostConstruct init()
       │
       ├── 遍历所有 ITool 实现
       │
       ├── 使用 ToolCallbacks.from() 提取 ToolCallback
       │
       └── 注册到 toolCallbackMap
```

## 工具分类

### System Tools (`cn.bitloom.agentic.tool.system`)
系统级工具，提供文件操作、命令执行、文件搜索等基础能力。详细文档见 [system/AGENTS.md](system/AGENTS.md)

**文件操作工具：**
- `read`: 读取文件内容（支持分页、行号显示）
- `write`: 写入/创建文件
- `edit`: 精确编辑文件（支持 replace_all）

**命令执行工具：**
- `exec`: 执行 shell 命令（支持后台运行、工作目录设置）
- `process_list`: 列出后台进程
- `process_status`: 查看进程详细状态
- `process_log`: 获取进程输出日志
- `process_kill`: 终止进程

**文件搜索工具：**
- `glob`: 使用通配符模式搜索文件（如 `**/*.java`）
- `grep`: 在文件内容中搜索正则表达式

**定时任务工具：**
- `cron_create`: 创建定时任务
- `cron_list`: 列出定时任务
- `cron_delete`: 删除定时任务
- `cron_trigger`: 手动触发定时任务

**网络工具：**
- `web_search`: 网页搜索（支持域名过滤）
- `web_fetch`: 抓取网页内容（支持重定向检测）

**交互工具：**
- `ask_user`: 向用户提问并等待回答

### SkillTool
技能管理工具。

**工具列表：**
- `loadSkill`: 按名称载入专业知识

### TaskTool
任务管理工具。

**工具列表：**
- `taskCreate`: 创建任务
- `taskUpdate`: 更新任务状态或依赖项
- `taskList`: 列出所有任务
- `taskGet`: 获取任务详情
- `scanUnclaimedTasks`: 扫描未认领的任务
- `claimTask`: 认领任务

### WindowTool
窗口管理工具。

**工具列表：**
- `openWindow`: 打开窗口（Md/Web）
- `closeWindow`: 关闭窗口

### BrowserTool
浏览器控制工具。

**工具列表：**
- `navigateBrowser`: 在浏览器窗口中导航到指定 URL

## 日志规范

所有工具调用都会记录日志，格式统一为 `[ToolCall] {工具名} - {操作描述}: {参数信息}`。

**日志格式示例：**
```
[ToolCall] read - 读取文件: filePath=/path/to/file
[ToolCall] read - 读取成功: filePath=/path/to/file, size=1024
[ToolCall] exec - 执行命令: command=ls -la, background=false
[ToolCall] exec - 命令完成: command=ls -la, exitCode=0
[ToolCall] write - 写入文件: filePath=/path/to/file
[ToolCall] write - 写入成功: filePath=/path/to/file, bytes=512
```

**日志级别：**
- `info`: 正常调用和成功结果
- `error`: 调用失败和异常信息

## 使用示例

### 创建自定义工具
```java
@Slf4j
@Component
public class MyTool implements ITool {
    
    @Tool(name = "myTool", description = "我的自定义工具")
    public ToolResult myTool(
        @ToolParam(description = "参数描述") String param
    ) {
        log.info("[ToolCall] myTool - 执行操作: param={}", param);
        try {
            String result = "结果: " + param;
            log.info("[ToolCall] myTool - 执行完成");
            return ToolResult.success("执行成功", result);
        } catch (Exception e) {
            log.error("[ToolCall] myTool - 执行失败", e);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }
}
```

### 在智能体中使用工具
```java
@Override
protected List<String> getToolSet() {
    return List.of("read", "write", "myTool");
}
```

### 直接调用工具
```java
String result = toolManager.call("read", "{\"filePath\": \"/path/to/file\"}");
```

## 设计模式
- 注册器模式：自动扫描和注册工具
- 策略模式：不同工具实现统一接口

## 注意事项
1. 工具必须实现 ITool 接口并添加 @Component 注解
2. 工具方法使用 @Tool 和 @ToolParam 注解
3. 工具名称必须唯一
4. ToolManager 在启动时自动扫描注册所有工具
5. 所有工具调用必须添加日志，格式遵循 `[ToolCall] {工具名} - {操作描述}`
