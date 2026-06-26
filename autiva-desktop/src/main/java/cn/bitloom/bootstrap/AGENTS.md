# Bootstrap 包

## 概述
本包实现了应用启动前的预初始化逻辑和启动画面，在 Spring 上下文启动之前执行。负责创建目录结构、初始化配置文件和智能体工作区，以及提供秒级显示的 Splash 画面。

## 核心类

### AppBootstrap
应用引导程序（纯工具类，非 Spring Bean），提供静态方法执行预初始化。

**核心方法：**
- `initialize()`: 执行四步预初始化，每步独立 try-catch 保证单步失败不影响其他步骤

**初始化步骤：**

1. **`initAppDirs()`**: 创建应用目录结构
   - `~/.autiva/` - 应用根目录
   - `~/.autiva/logs/` - 日志目录
   - `~/.autiva/skills/` - 全局技能目录
   - `~/.autiva/agents/` - 智能体定义和长期配置目录

2. **`initSettingsFile()`**: 创建 `~/.autiva/settings.properties` 配置文件
   - 首次创建时写入默认配置模板（包含 DeepSeek/智谱 AI 默认地址和模型名称）
   - API Key 默认为空，用户需在设置页面填写

3. **`initDefaultAgent()`**: 初始化默认主智能体
   - 创建 `agents/default/` 目录
   - 从 classpath:bootstrap/agent/default-agent.md 复制 agent.md
   - 从 classpath:bootstrap/config.json 复制 config.json
   - 从 classpath:bootstrap/*.md 复制 AGENTS.md、MEMORY.md、BOOTSTRAP.md
   - 创建 `agents/default/memory/` 目录
   - 创建 `workspace/default/` 目录（仅 context/ 和 sessions/）

4. **`initCoderMainAgent()`**: 初始化编码主智能体
   - 创建 `agents/coder/` 目录（如不存在）
   - 从 classpath:bootstrap/agent/coder/* 复制 agent.md、config.json、memory.md
   - 逐个文件检查，已存在的文件跳过，确保新增文件也能被复制到已安装的环境
   - 创建 `workspace/coder/` 目录（context/ 和 sessions/）

5. **`initSubagents()`**: 初始化子智能体
   - 从 classpath:bootstrap/subagent/*.md 复制到 `agents/{name}/agent.md`
   - 例如 `bootstrap/subagent/code.md` → `agents/code/agent.md`

### SplashScreen
启动画面，简洁优雅的 Apple 风格动画，不依赖 FXML 或 Spring，确保秒级显示。

**构造参数：**
- `ImageView iconView`: 图标视图（支持 SvgImageView 等 ImageView 子类）

**核心方法：**
- `show()`: 创建并显示独立的小型 Splash 窗口，返回 Stage 引用
- `close()`: 无需清理

**动画设计（极简高级感）：**
- **图标入场**（0~400ms）：ScaleTransition（0.8→1.0，EASE_OUT）+ FadeTransition（0→1）
- **三点加载指示器**（500ms 延迟后）：三个蓝色圆点依次闪烁，循环播放

**设计要点：**
- 无标题，仅图标和加载指示器，极简设计
- 遵循 Apple 设计规范：白色背景、#0071e3 主色调
- 独立 `StageStyle.TRANSPARENT` 窗口（200×200），圆角 20px + 阴影，屏幕居中

**启动流程：**
1. `AutivaApplication.start()` → `SplashScreen.show()` 创建独立小窗口（秒级）
2. 后台线程 `app-bootstrap` → 执行 `AppBootstrap.initialize()` + `SpringApplication.run(AutivaApplication.class)`
3. Spring 上下文就绪 → `Platform.runLater()` → 关闭 Splash Stage → 配置并显示主 Stage

**注意：** `AutivaApplication` 类必须标注 `@SpringBootApplication` 注解，确保 Spring Boot 自动扫描 `cn.bitloom` 包及其子包下的所有组件（包括 `@Component`、`@Service`、`@Controller` 等）。

## 设计模式
- 模板方法模式：四步初始化流程
- 容错模式：每步独立 try-catch，单步失败不阻塞后续步骤
- 异步加载模式：Splash 秒级显示，Spring 上下文后台加载

## JVM 参数
javafx-maven-plugin 配置了以下 JVM 参数（pom.xml）：
- `--add-opens javafx.graphics/com.sun.javafx.iio=ALL-UNNAMED` — 允许反射访问 JavaFX 内部图像 I/O 包
- `--add-opens javafx.graphics/com.sun.javafx.iio.imageio=ALL-UNNAMED` — 允许反射访问 JavaFX ImageIO 桥接包
- `--enable-native-access=javafx.graphics` — 允许 JavaFX 加载原生库（消除 JavaFX 21+ 原生访问警告）
- `--add-opens java.base/sun.misc=ALL-UNNAMED` — 允许 dingtalk-stream SDK 访问 sun.misc.Unsafe（消除 Unsafe 弃用警告）

## 注意事项
1. AppBootstrap 在 Spring 上下文启动之前执行，不能使用 Spring 依赖注入
2. SplashScreen 不依赖 FXML 和 Spring，确保启动画面能秒级显示
3. 配置文件首次创建时写入默认模板，避免空文件导致 @Value 注入占位符默认值
4. 所有异常都被捕获并记录日志，不会导致应用启动失败
5. 后台加载线程设置为 daemon 线程，不会阻止 JVM 退出
