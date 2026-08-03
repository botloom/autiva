$ErrorActionPreference = "Stop"

$JDK = "D:\jdk-25"
$MVN = "D:\apache-maven-3.9.12\bin\mvn.cmd"
$PROJECT = "D:\project\autiva"

$JDK_MODULES = "java.base,java.desktop,java.net.http,java.logging,java.xml,java.sql,java.naming,java.management,java.instrument,java.scripting,java.prefs,java.security.sasl,java.transaction.xa,jdk.jsobject,jdk.unsupported,jdk.crypto.ec,jdk.xml.dom"

Write-Host "=== Step 1: Build thin JAR + copy dependencies ===" -ForegroundColor Green
& $MVN package -pl autiva-desktop -am -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

& $MVN dependency:copy-dependencies -pl autiva-desktop -DincludeScope=runtime -DexcludeArtifactIds=lombok
if ($LASTEXITCODE -ne 0) { throw "dependency:copy-dependencies failed" }

Write-Host "=== Step 2: Create custom JRE with jlink ===" -ForegroundColor Green
$jlinkOutput = "$PROJECT\autiva-desktop\target\custom-jre"
Remove-Item -Recurse -Force $jlinkOutput -ErrorAction SilentlyContinue

$depDir = "$PROJECT\autiva-desktop\target\dependency"
# dependency 目录里可能同时存在多个版本的 javafx-*-win.jar（jeditermfx 会传递 javafx-base 19.0.2.1，
# 而项目直接依赖 25.0.3）。jlink 遇到重复模块会行为不确定，这里按 artifactId 去重，只保留版本号最高的。
$javafxJars = Get-ChildItem "$depDir\javafx-*-win.jar" -ErrorAction SilentlyContinue |
    Group-Object { ($_.BaseName -replace '-\d+(\.\d+)*-win$', '') } |
    ForEach-Object { $_.Group | Sort-Object Name -Descending | Select-Object -First 1 }
$javafxModulePath = ($javafxJars.FullName -join ";")

$javafxModules = "javafx.controls,javafx.fxml,javafx.swing"
$allModules = "$JDK_MODULES,$javafxModules"

Write-Host "  JavaFX module path: $javafxModulePath" -ForegroundColor DarkGray
Write-Host "  Adding modules: $javafxModules" -ForegroundColor DarkGray

& "$JDK\bin\jlink" `
  --module-path "$javafxModulePath" `
  --add-modules $allModules `
  --output $jlinkOutput `
  --strip-debug `
  --no-man-pages `
  --no-header-files `
  --compress=2
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

$jreSize = "{0:N2} MB" -f ((Get-ChildItem $jlinkOutput -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)
Write-Host "  Custom JRE size: $jreSize" -ForegroundColor Cyan

Write-Host "=== Step 3: Prepare jpackage input ===" -ForegroundColor Green
$jpackageInput = "$PROJECT\autiva-desktop\target\jpackage-input"
Remove-Item -Recurse -Force $jpackageInput -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $jpackageInput | Out-Null

Copy-Item autiva-desktop\target\autiva-desktop-1.0-SNAPSHOT.jar $jpackageInput\ -Force
# JavaFX jar 分两种：
#   - javafx-*-win.jar：含真实类与 native 库，已被 jlink 打入自定义 JRE（named module）
#   - javafx-*.jar（无 -win）：仅含 META-INF/MANIFEST.MF 的空壳 jar
# 两种都不能放进 classpath：空壳会让 LauncherImpl 加载到无类的 jar，-win 会和 JRE 模块重复。
Get-ChildItem "$depDir\*.jar" | Where-Object { $_.Name -notmatch '^javafx-.*\.jar$' } | Copy-Item -Destination $jpackageInput\ -Force

Write-Host "  Removing platform-irrelevant JARs..." -ForegroundColor Cyan
$removePatterns = @(
    "netty-transport-native-epoll-*.jar",
    "netty-transport-classes-epoll-*.jar",
    "netty-resolver-dns-native-macos-*.jar",
    "netty-resolver-dns-classes-macos-*.jar"
)
$removedSize = 0.0
foreach ($pattern in $removePatterns) {
    $files = Get-ChildItem "$jpackageInput\$pattern" -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $removedSize += $file.Length / 1MB
        Write-Host "    Removed: $($file.Name)" -ForegroundColor DarkGray
        Remove-Item $file.FullName -Force
    }
}
Write-Host "  Freed: $([math]::Round($removedSize, 2)) MB" -ForegroundColor Cyan

Add-Type -AssemblyName System.Drawing

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$png = [System.Drawing.Image]::FromFile("$PROJECT\autiva-desktop\src\main\resources\cn\bitloom\images\icon.png")

$pngDataList = @()
foreach ($size in $sizes) {
    $bmp = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($png, 0, 0, $size, $size)
    $g.Dispose()
    $ms = [System.IO.MemoryStream]::new()
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $pngDataList += ,$ms.ToArray()
    $ms.Dispose()
}
$png.Dispose()

$entryCount = $sizes.Count
$headerSize = 6
$entrySize = 16
$dataOffset = $headerSize + ($entryCount * $entrySize)

$icoStream = [System.IO.MemoryStream]::new()
$bw = [System.IO.BinaryWriter]::new($icoStream)

$bw.Write([UInt16]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]$entryCount)

$currentOffset = $dataOffset
for ($i = 0; $i -lt $entryCount; $i++) {
    $size = $sizes[$i]
    $data = $pngDataList[$i]
    $bw.Write([byte]$(if ($size -ge 256) { 0 } else { $size }))
    $bw.Write([byte]$(if ($size -ge 256) { 0 } else { $size }))
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]32)
    $bw.Write([UInt32]$data.Length)
    $bw.Write([UInt32]$currentOffset)
    $currentOffset += $data.Length
}

for ($i = 0; $i -lt $entryCount; $i++) {
    $bw.Write($pngDataList[$i])
}

$bw.Flush()
[System.IO.File]::WriteAllBytes("$jpackageInput\icon.ico", $icoStream.ToArray())
$bw.Dispose()
$icoStream.Dispose()

Write-Host "=== Step 4: Run jpackage with custom JRE ===" -ForegroundColor Green
Remove-Item -Recurse -Force autiva-desktop\target\jpackage-output -ErrorAction SilentlyContinue
& "$JDK\bin\jpackage" `
  --type app-image `
  --name Autiva `
  --input autiva-desktop\target\jpackage-input `
  --main-jar autiva-desktop-1.0-SNAPSHOT.jar `
  --main-class cn.bitloom.AutivaApplication `
  --runtime-image $jlinkOutput `
  --java-options "-Xms128m" `
  --java-options "-Xmx1024m" `
  --java-options "-XX:+UseCompressedOops" `
  --java-options "-XX:+UseCompressedClassPointers" `
  --java-options "-XX:+UseStringDeduplication" `
  --java-options "-XX:+UseCompactObjectHeaders" `
  --dest autiva-desktop\target\jpackage-output `
  --app-version 1.0.0 `
  --vendor "Bitloom" `
  --description "Autiva AI Agent Desktop Application" `
  --icon autiva-desktop\target\jpackage-input\icon.ico
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Write-Host "=== Step 5: Build MSI installer ===" -ForegroundColor Green
$appImageDir = "$PROJECT\autiva-desktop\target\jpackage-output\Autiva"
$msiOutput = "$PROJECT\autiva-desktop\target\jpackage-output"
Remove-Item "$msiOutput\Autiva-1.0.0.msi" -Force -ErrorAction SilentlyContinue

& "$JDK\bin\jpackage" `
  --type msi `
  --app-image $appImageDir `
  --name Autiva `
  --app-version 1.0.0 `
  --vendor "Bitloom" `
  --description "Autiva AI Agent Desktop Application" `
  --dest $msiOutput `
  --win-menu-group "Autiva" `
  --win-shortcut `
  --win-dir-chooser `
  --win-upgrade-uuid "7307B9E5-7646-314A-868F-FFDA3A0204A2"
if ($LASTEXITCODE -ne 0) { throw "MSI build failed" }

$msiFile = "$msiOutput\Autiva-1.0.0.msi"
if (Test-Path $msiFile) {
    $msiSize = "{0:N2} MB" -f ((Get-Item $msiFile).Length / 1MB)
    Write-Host "  MSI generated: $msiSize" -ForegroundColor Cyan
} else {
    throw "MSI file not found at $msiFile"
}

$imageSize = "{0:N2} MB" -f ((Get-ChildItem $appImageDir -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)
Write-Host ""
Write-Host "=== Build Complete ===" -ForegroundColor Green
Write-Host "MSI Installer : autiva-desktop\target\jpackage-output\Autiva-1.0.0.msi ($msiSize)"
Write-Host "App Image     : autiva-desktop\target\jpackage-output\Autiva\ ($imageSize)"
