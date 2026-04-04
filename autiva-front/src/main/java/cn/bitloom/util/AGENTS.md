# Util 包

## 概述
本包提供了各种工具类，包括浏览器管理、线程池管理和 Spring 上下文工具。

## 核心类

### BrowserManager
浏览器管理器，封装 Playwright 浏览器操作。

**功能：**
- 启动 Chrome 浏览器（CDP 模式）
- 打开新页面并导航
- 关闭浏览器

**核心方法：**
- `open(url)`: 打开 URL，返回 Page 对象
- `close()`: 关闭浏览器和相关资源

**配置：**
- 使用 CDP 协议连接 Chrome
- 默认端口：9222
- 浏览器路径从 Store.browserPath 获取

**使用示例：**
```java
Page page = BrowserManager.open("https://example.com");
// 操作页面...
BrowserManager.close();
```

### ExecutorManager
线程池管理器，提供应用级别的线程池。

**线程池：**
- `platformTaskExecutor`: 平台任务线程池（5 线程）
- `teammateExecutor`: 子智能体线程池（5 线程）

**核心方法：**
- `getPlatformTaskExecutor()`: 获取平台任务线程池
- `getTeammateExecutor()`: 获取子智能体线程池
- `close()`: 关闭所有线程池

**使用示例：**
```java
ExecutorService executor = ExecutorManager.getPlatformTaskExecutor();
executor.submit(() -> {
    // 执行任务...
});

// 应用关闭时
ExecutorManager.close();
```

### SpringContextUtil
Spring 上下文工具，提供静态方法获取 Bean。

**核心方法：**
- `getBean(Class<T>)`: 按类型获取 Bean
- `getBean(String)`: 按名称获取 Bean
- `getBean(String, Class<T>)`: 按名称和类型获取 Bean

**使用示例：**
```java
MyService service = SpringContextUtil.getBean(MyService.class);
Object bean = SpringContextUtil.getBean("myBean");
MyService service2 = SpringContextUtil.getBean("myService", MyService.class);
```

**实现原理：**
- 实现 ApplicationContextAware 接口
- Spring 自动注入 ApplicationContext
- 提供静态方法访问

## 设计模式
- 工具类模式：静态方法，无需实例化
- 单例模式：ApplicationContext 全局唯一

## 注意事项
1. BrowserManager 使用静态初始化块，应用启动时自动初始化
2. ExecutorManager 线程池需要在应用关闭时手动关闭
3. SpringContextUtil 只能在 Spring 容器初始化后使用
4. 浏览器操作是阻塞的，注意线程管理