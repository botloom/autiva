# Bootstrap 包

## 概述
本包实现了应用启动前的预初始化逻辑和启动画面，在 Spring 上下文启动之前执行。负责创建目录结构、初始化配置文件和智能体工作区，以及提供秒级显示的 Splash 画面。

## 核心类

### AppBootstrap
应用引导程序（纯工具类，非 Spring Bean），提供静态方法执行预初始化。

**核心方法：**
- `initialize()`: 执行四步预初始化，每步独立 try-catch 保证单步失败不影响其他步骤

**初始化步骤：**

1. **`createAppDirsIfNotExist()`**: 创建应用目录结构
   - `~/.autiva/` - 应用根目录
   - `~/.autiva/logs/` - 日志目录
   - `~/.autiva/skills/` - 技能目录
   - `~/.autiva/mcp/` - MCP 配置目录
   - `~/.autiva/workspace/` - 工作区目录
   - `~/.autiva/mcp/mcp-servers.json` - MCP 配置文件（首次创建时写入 `{}` 而非空文件，避免 Spring AI MCP 自动配置反序列化空内容报错）

2. **`createConfigFileIfNotExist()`**: 创建 `~/.autiva/settings.properties` 配置文件
   - 首次创建时写入默认配置模板（包含 DeepSeek/智谱 AI 默认地址和模型名称）
   - API Key 默认为空，用户需在设置页面填写

3. **`initAgentWorkspaces()`**: 初始化主智能体工作区
   - 遍历 AgentIdentityEnum 中所有 MAIN 类型的智能体
   - 为每个主智能体创建工作目录并从 classpath 复制模板文件
   - 单个智能体初始化失败不影响其他智能体

4. **`initSubagentWorkspace()`**: 初始化子智能体工作区
   - 遍历 SUBAGENT 类型的智能体（跳过 A2A）
   - 为每个子智能体创建工作目录并复制模板
   - 单个智能体初始化失败不影响其他智能体

### SplashScreen
启动画面，简洁优雅的 Apple 风格动画，不依赖 FXML 或 Spring，确保秒级显示。

**核心方法：**
- `show()`: 创建并显示独立的小型 Splash 窗口，返回 Stage 引用
- `close()`: 无需清理

**动画设计（极简高级感）：**
- **图标入场**（0~400ms）：ScaleTransition（0.8→1.0，EASE_OUT）+ FadeTransition（0→1）
- **三点加载指示器**（500ms 延迟后）：三个蓝色圆点依次闪烁，循环播放
  - 每个点间隔 200ms 亮起，持续 300ms 后暗下
  - 整个周期 800ms，无限循环
  - 点半径 3px，间距 6px，颜色 #0071e3

**布局结构：**
- VBox（图标 + 三点指示器），垂直居中，间距 20px
- StackPane 包裹，白色背景，圆角 20px，阴影效果

**设计要点：**
- 无标题，仅图标和加载指示器，极简设计
- 图标 64px，适合非轴对称图形（不使用旋转动画）
- 三点指示器类似 iOS 加载风格，视觉简洁
- 遵循 Apple 设计规范：白色背景、#0071e3 主色调
- 独立 `StageStyle.TRANSPARENT` 窗口（200×200），圆角 20px + 阴影，屏幕居中

**启动流程：**
1. `AutivaApplication.start()` → `SplashScreen.show()` 创建独立小窗口（秒级）
2. 后台线程 `app-bootstrap` → 执行 `AppBootstrap.initialize()` + `SpringApplication.run()`
3. Spring 上下文就绪 → `Platform.runLater()` → 关闭 Splash Stage → 配置并显示主 Stage

**默认配置模板：**
```properties
# Autiva Application Settings
app.session.isolation=PER_PEER

# DeepSeek Configuration
spring.ai.deepseek.chat.base-url=https://api.deepseek.com
spring.ai.deepseek.chat.completions-path=/v1/chat/completions
spring.ai.deepseek.chat.api-key=
spring.ai.deepseek.chat.options.model=deepseek-chat

# ZhiPu AI Configuration
spring.ai.zhipuai.chat.base-url=https://open.bigmodel.cn/api/paas/v4
spring.ai.zhipuai.chat.completions-path=/chat/completions
spring.ai.zhipuai.api-key=
spring.ai.zhipuai.chat.options.model=glm-4-flash

# WeChat iLink
weixin.ilink.enabled=false

# Backend
app.backend.url=http://localhost:9527
```

## 设计模式
- 模板方法模式：四步初始化流程
- 容错模式：每步独立 try-catch，单步失败不阻塞后续步骤
- 异步加载模式：Splash 秒级显示，Spring 上下文后台加载

## 注意事项
1. AppBootstrap 在 Spring 上下文启动之前执行，不能使用 Spring 依赖注入
2. SplashScreen 不依赖 FXML 和 Spring，确保启动画面能秒级显示
3. 配置文件首次创建时写入默认模板，避免空文件导致 @Value 注入占位符默认值
4. copyClasspathTemplates() 从 classpath 的 bootstrap/{IDENTITY}/ 目录复制模板，无模板时仅记录警告
5. 所有异常都被捕获并记录日志，不会导致应用启动失败
6. 后台加载线程设置为 daemon 线程，不会阻止 JVM 退出
