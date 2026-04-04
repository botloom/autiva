# Advisor 包

## 概述
本包实现了 Spring AI 的 Advisor 模式，用于在 LLM 请求/响应过程中添加横切关注点。

## 核心类

### LoggingAdvisor
实现 StreamAdvisor 接口，提供 LLM 请求和响应的日志记录功能。

**功能：**
- 记录所有发送给 LLM 的消息（System、User、Assistant、Tool Response）
- 流式记录 LLM 响应内容
- 记录工具调用信息
- 记录错误信息

**日志格式：**
```
══════════════════════════════════════════════════════════
[#1][10:30:15.123] 📥 LLM Request
──────────────────────────────────────────────────  [Request Details]
──────────────────────────────────────────────────  [1] 📌 System Message:
──────────────────────────────────────────────────  你是一个助手...
──────────────────────────────────────────────────  [2] 👤 User Message:
──────────────────────────────────────────────────      你好
──────────────────────────────────────────────────  📊 Total Messages: 2
══════════════════════════════════════════════════════════

[#1][10:30:15.125] 📤 LLM Text Delta: 你
[#1][10:30:15.126] 📤 LLM Text Delta: 好
[#1][10:30:15.130] 📤 LLM Text Delta: ！
[#1][10:30:15.135]      │  └── Tool #1: getWeather({"city":"北京"})

══════════════════════════════════════════════════════════
[#1][10:30:15.500] ✅ LLM Response Completed
──────────────────────────────────────────────────  [Response Summary]
──────────────────────────────────────────────────  📝 Full Text Content:
──────────────────────────────────────────────────  你好！
──────────────────────────────────────────────────  🔧 Tool Calls (1):
      │  └── Tool #1: getWeather({"city":"北京"})
──────────────────────────────────────────────────  ⏱️  Started: 10:30:15.123, Duration: 377ms
══════════════════════════════════════════════════════════
```

**特性：**
- 请求/响应增加序号（#1, #2...）便于追踪
- 消息带时间戳（HH:mm:ss.SSS）
- 流式响应实时打印 text delta
- 清晰的视觉分隔符
- 树形结构展示 Tool Calls
- 请求完成时显示汇总和耗时

**配置：**
- Order: 1（执行顺序）
- 自动注册为 Spring Bean

## 使用方式
LoggingAdvisor 自动注入到 AbstractAgent 的 ChatClient 配置中：
```java
ChatClient chatClient = ChatClient.builder(this.deepSeekChatModel)
    .defaultAdvisors(a -> a.advisors(
        MessageChatMemoryAdvisor.builder(this.chatMemory).build(),
        loggingAdvisor  // 自动添加日志记录
    ))
    .build();
```

## 扩展指南
可以创建其他 Advisor 实现：
1. 实现 StreamAdvisor 接口
2. 在 adviseStream 方法中添加自定义逻辑
3. 注册为 Spring Bean
4. 在 AbstractAgent 中注入并添加到 ChatClient

## 注意事项
1. Advisor 的 Order 决定执行顺序
2. 流式处理需要注意线程安全
3. 日志记录可能包含敏感信息，生产环境需谨慎
