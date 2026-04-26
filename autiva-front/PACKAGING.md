# Autiva Front jpackage 打包说明

## 一、环境要求

| 工具 | 版本 | 路径（示例） | 说明 |
|------|------|-------------|------|
| JDK | 17+ | `D:\jdk-17` | 需包含 jpackage 和 jlink |
| Maven | 3.9+ | `D:\apache-maven-3.9.12` | 构建 Spring Boot fat JAR |
| Inno Setup 6 | 6.x | `C:\Program Files (x86)\Inno Setup 6` | 可选，生成 exe 安装程序 |

验证环境：

```powershell
java -version                    # 确认 JDK 17+
D:\jdk-17\bin\jpackage --version # 确认 jpackage 可用
D:\jdk-17\bin\jlink --version    # 确认 jlink 可用
```

## 二、打包步骤

### 步骤 1：构建 Spring Boot Fat JAR

```powershell
cd D:\project\autiva

D:\apache-maven-3.9.12\bin\mvn.cmd package -pl autiva-front -am -DskipTests org.springframework.boot:spring-boot-maven-plugin:3.5.12:repackage
```

产物：`autiva-front/target/autiva-front-1.0-SNAPSHOT.jar`（约 293MB）

### 步骤 2：使用 jlink 裁剪 JRE

jdeps 分析依赖模块：

```powershell
D:\jdk-17\bin\jdeps --ignore-missing-deps -q --multi-release 17 --print-module-deps autiva-front\target\autiva-front-1.0-SNAPSHOT.jar
```

输出：`java.base,java.desktop,java.net.http,jdk.jsobject`

根据实际运行验证，需要补充以下模块：

| 模块 | 来源 | 用途 |
|------|------|------|
| `java.base` | jdeps | 基础类库 |
| `java.desktop` | jdeps | AWT/Swing（JavaFX 依赖） |
| `java.net.http` | jdeps | HTTP 客户端（WebFlux/MCP） |
| `jdk.jsobject` | jdeps | JavaScript 对象桥接（JavaFX WebView） |
| `java.sql` | 手动补充 | 数据库访问 |
| `java.naming` | 手动补充 | JNDI 命名服务 |
| `java.management` | 手动补充 | JMX 管理 |
| `java.instrument` | 手动补充 | Java Agent 支持 |
| `java.scripting` | 手动补充 | javax.script 脚本引擎（**必须**，缺失会导致启动失败） |
| `jdk.unsupported` | 手动补充 | sun.misc.Unsafe 等内部 API |
| `jdk.crypto.ec` | 手动补充 | HTTPS/TLS 加密 |

执行 jlink：

```powershell
D:\jdk-17\bin\jlink ^
  --add-modules java.base,java.desktop,java.net.http,java.sql,java.naming,java.management,java.instrument,java.scripting,jdk.jsobject,jdk.unsupported,jdk.crypto.ec ^
  --output autiva-front\target\custom-jre ^
  --strip-debug ^
  --compress 2 ^
  --no-header-files ^
  --no-man-pages
```

产物：`autiva-front/target/custom-jre/`（约 40MB）

### 步骤 3：准备 jpackage 输入目录

```powershell
New-Item -ItemType Directory -Force -Path autiva-front\target\jpackage-input
Copy-Item autiva-front\target\autiva-front-1.0-SNAPSHOT.jar autiva-front\target\jpackage-input\
```

### 步骤 4：生成 ICO 图标

jpackage 在 Windows 上需要 `.ico` 格式图标。从项目现有的 PNG 图标转换：

```powershell
Add-Type -AssemblyName System.Drawing
$png = [System.Drawing.Image]::FromFile("autiva-front\src\main\resources\cn\bitloom\images\icon.png")
$icon = [System.Drawing.Icon]::FromHandle(([System.Drawing.Bitmap]::new($png, 256, 256)).GetHicon())
$stream = [System.IO.File]::Create("autiva-front\target\jpackage-input\icon.ico")
$icon.Save($stream)
$stream.Close()
$png.Dispose()
```

> 也可使用在线工具（如 convertio.co/png-ico）手动转换，建议包含 16x16、32x32、48x48、256x256 多尺寸。

### 步骤 5：执行 jpackage

#### 方式 A：生成便携版（app-image）

无需额外安装工具，生成可直接运行的目录：

```powershell
D:\jdk-17\bin\jpackage ^
  --type app-image ^
  --name Autiva ^
  --input autiva-front\target\jpackage-input ^
  --main-jar autiva-front-1.0-SNAPSHOT.jar ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --runtime-image autiva-front\target\custom-jre ^
  --dest autiva-front\target\jpackage-output ^
  --app-version 1.0.0 ^
  --vendor "Bitloom" ^
  --description "Autiva AI Agent Desktop Application" ^
  --icon autiva-front\target\jpackage-input\icon.ico
```

产物：`autiva-front/target/jpackage-output/Autiva/`（约 334MB）

#### 方式 B：生成安装程序（exe）

需先安装 [Inno Setup 6](https://jrsoftware.org/isdl.php)，安装后重启终端。

```powershell
D:\jdk-17\bin\jpackage ^
  --type exe ^
  --name Autiva ^
  --input autiva-front\target\jpackage-input ^
  --main-jar autiva-front-1.0-SNAPSHOT.jar ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --runtime-image autiva-front\target\custom-jre ^
  --dest autiva-front\target\jpackage-output ^
  --app-version 1.0.0 ^
  --vendor "Bitloom" ^
  --description "Autiva AI Agent Desktop Application" ^
  --icon autiva-front\target\jpackage-input\icon.ico ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-menu-group "Autiva"
```

产物：`autiva-front/target/jpackage-output/Autiva-1.0.0.exe`（安装程序）

## 三、产物结构

### app-image 模式

```
Autiva/
├── Autiva.exe                              # 应用启动器（449KB）
├── Autiva.ico                              # 应用图标
├── app/
│   ├── Autiva.cfg                          # 启动配置（主类、JVM 参数）
│   └── autiva-front-1.0-SNAPSHOT.jar      # Spring Boot Fat JAR（293MB）
└── runtime/                                # 裁剪后的 JRE（40MB）
    ├── bin/
    │   ├── java.exe
    │   └── ...
    ├── conf/
    ├── legal/
    └── lib/
        ├── jvm.dll
        ├── modules
        └── ...
```

### Autiva.cfg 配置文件

```ini
[Application]
app.classpath=$APPDIR\autiva-front-1.0-SNAPSHOT.jar
app.mainclass=org.springframework.boot.loader.launch.JarLauncher

[JavaOptions]
java-options=-Djpackage.app-version=1.0.0
```

如需添加 JVM 参数，可手动编辑此文件，在 `[JavaOptions]` 下添加：

```ini
java-options=-Xmx2g
java-options=-Dfile.encoding=UTF-8
```

## 四、体积分析

| 组件 | 大小 | 占比 |
|------|------|------|
| Fat JAR（含所有依赖 + JavaFX） | 292.60 MB | 87.7% |
| 裁剪后 JRE | 40.48 MB | 12.1% |
| 启动器 + 图标 | ~0.5 MB | 0.2% |
| **总计** | **~334 MB** | 100% |

对比使用完整 JDK JRE（~200MB），裁剪 JRE 节省约 **160MB**。

## 五、常见问题

### 1. 双击 exe 出现 "failed to launch jvm"

**原因**：裁剪的 JRE 缺少必要的 Java 模块。

**排查方法**：用命令行运行查看详细错误：

```powershell
cd autiva-front\target\jpackage-output\Autiva
.\runtime\bin\java.exe -jar .\app\autiva-front-1.0-SNAPSHOT.jar
```

**常见缺失模块**：

| 错误信息 | 缺失模块 |
|---------|---------|
| `javax/script/Bindings` not found | `java.scripting` |
| `javax/sql/DataSource` not found | `java.sql` |
| `javax/naming/...` not found | `java.naming` |
| SSL/HTTPS 连接失败 | `jdk.crypto.ec` |

**修复**：在 jlink 的 `--add-modules` 中添加缺失模块，重新生成 JRE 和打包。

### 2. JavaFX 警告 "Unsupported JavaFX configuration: classes were loaded from 'unnamed module'"

**原因**：JavaFX 从 classpath（fat JAR）加载，而非模块路径。这是 Spring Boot + JavaFX 的正常行为，不影响功能。

**说明**：由于 JavaFX 在 unnamed module 中，`--add-opens=javafx.graphics/...=ALL-UNNAMED` 参数无效（会产生 `Unknown module` 警告），因此打包时不需要添加此参数。

### 3. Maven clean 失败 "Failed to delete ... icon.png"

**原因**：文件被其他进程占用（如 IDE 或正在运行的应用）。

**解决**：关闭占用进程后重试，或跳过 clean 直接 package：

```powershell
D:\apache-maven-3.9.12\bin\mvn.cmd package -pl autiva-front -am -DskipTests org.springframework.boot:spring-boot-maven-plugin:3.5.12:repackage
```

### 4. jpackage 输出目录删除失败

**原因**：当前工作目录在输出目录内。

**解决**：先切换到其他目录再删除：

```powershell
cd D:\project\autiva
Remove-Item -Recurse -Force autiva-front\target\jpackage-output
```

### 5. Playwright 运行时错误

**说明**：Playwright 需要浏览器驱动，打包后的应用首次使用需执行：

```powershell
npx playwright install
```

### 6. whisper-jni 运行时错误

**说明**：whisper-jni 需要原生 DLL 库，首次使用需下载模型文件到 `~/.autiva/models/`。

## 六、一键打包脚本

将以下内容保存为 `build-package.ps1`，放在项目根目录：

```powershell
$ErrorActionPreference = "Stop"

$JDK = "D:\jdk-17"
$MVN = "D:\apache-maven-3.9.12\bin\mvn.cmd"
$PROJECT = "D:\project\autiva"
$MODULES = "java.base,java.desktop,java.net.http,java.sql,java.naming,java.management,java.instrument,java.scripting,jdk.jsobject,jdk.unsupported,jdk.crypto.ec"

Write-Host "=== Step 1: Build Spring Boot Fat JAR ===" -ForegroundColor Green
& $MVN package -pl autiva-front -am -DskipTests org.springframework.boot:spring-boot-maven-plugin:3.5.12:repackage
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

Write-Host "=== Step 2: Create custom JRE with jlink ===" -ForegroundColor Green
Remove-Item -Recurse -Force autiva-front\target\custom-jre -ErrorAction SilentlyContinue
& "$JDK\bin\jlink" --add-modules $MODULES --output autiva-front\target\custom-jre --strip-debug --compress 2 --no-header-files --no-man-pages
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

Write-Host "=== Step 3: Prepare jpackage input ===" -ForegroundColor Green
New-Item -ItemType Directory -Force -Path autiva-front\target\jpackage-input | Out-Null
Copy-Item autiva-front\target\autiva-front-1.0-SNAPSHOT.jar autiva-front\target\jpackage-input\ -Force

Add-Type -AssemblyName System.Drawing
$png = [System.Drawing.Image]::FromFile("$PROJECT\autiva-front\src\main\resources\cn\bitloom\images\icon.png")
$icon = [System.Drawing.Icon]::FromHandle(([System.Drawing.Bitmap]::new($png, 256, 256)).GetHicon())
$stream = [System.IO.File]::Create("$PROJECT\autiva-front\target\jpackage-input\icon.ico")
$icon.Save($stream)
$stream.Close()
$png.Dispose()

Write-Host "=== Step 4: Run jpackage ===" -ForegroundColor Green
Remove-Item -Recurse -Force autiva-front\target\jpackage-output -ErrorAction SilentlyContinue
& "$JDK\bin\jpackage" `
  --type app-image `
  --name Autiva `
  --input autiva-front\target\jpackage-input `
  --main-jar autiva-front-1.0-SNAPSHOT.jar `
  --main-class org.springframework.boot.loader.launch.JarLauncher `
  --runtime-image autiva-front\target\custom-jre `
  --dest autiva-front\target\jpackage-output `
  --app-version 1.0.0 `
  --vendor "Bitloom" `
  --description "Autiva AI Agent Desktop Application" `
  --icon autiva-front\target\jpackage-input\icon.ico
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$size = "{0:N2} MB" -f ((Get-ChildItem autiva-front\target\jpackage-output\Autiva -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)
Write-Host ""
Write-Host "=== Build Complete ===" -ForegroundColor Green
Write-Host "Output: autiva-front\target\jpackage-output\Autiva\Autiva.exe"
Write-Host "Size: $size"
```

使用方式：

```powershell
cd D:\project\autiva
.\build-package.ps1
```

## 七、版本升级注意事项

1. **升级依赖后**：重新运行 `jdeps` 分析是否需要新的 Java 模块，并更新 jlink 的 `--add-modules`
2. **升级 Spring Boot 版本后**：确认 `JarLauncher` 的包路径是否变化（Spring Boot 3.x 使用 `org.springframework.boot.loader.launch.JarLauncher`）
3. **升级 JDK 版本后**：重新执行 jlink 和 jpackage，确保 JRE 与 JDK 版本匹配
4. **升级 JavaFX 版本后**：JavaFX 已打包在 fat JAR 中，无需额外处理
