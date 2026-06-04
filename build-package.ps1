$ErrorActionPreference = "Stop"

$JDK = "D:\jdk-17"
$MVN = "D:\apache-maven-3.9.12\bin\mvn.cmd"
$PROJECT = "D:\project\autiva"

$JDK_MODULES = "java.base,java.desktop,java.net.http,java.logging,java.xml,java.sql,java.naming,java.management,java.instrument,java.scripting,java.prefs,java.security.sasl,java.transaction.xa,jdk.jsobject,jdk.unsupported,jdk.crypto.ec,jdk.xml.dom"

Write-Host "=== Step 1: Build thin JAR + copy dependencies ===" -ForegroundColor Green
& $MVN package -pl autiva-front -am -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

& $MVN dependency:copy-dependencies -pl autiva-front -DincludeScope=runtime -DexcludeArtifactIds=lombok
if ($LASTEXITCODE -ne 0) { throw "dependency:copy-dependencies failed" }

Write-Host "=== Step 2: Create custom JRE with jlink ===" -ForegroundColor Green
$jlinkOutput = "$PROJECT\autiva-front\target\custom-jre"
Remove-Item -Recurse -Force $jlinkOutput -ErrorAction SilentlyContinue

$depDir = "$PROJECT\autiva-front\target\dependency"
$javafxJars = (Get-ChildItem "$depDir\javafx-*-win.jar" -ErrorAction SilentlyContinue)
$javafxModulePath = ($javafxJars.FullName -join ";")

$javafxModules = "javafx.controls,javafx.fxml"
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
$jpackageInput = "$PROJECT\autiva-front\target\jpackage-input"
Remove-Item -Recurse -Force $jpackageInput -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $jpackageInput | Out-Null

Copy-Item autiva-front\target\autiva-front-1.0-SNAPSHOT.jar $jpackageInput\ -Force
Get-ChildItem "$depDir\*.jar" | Where-Object { $_.Name -notmatch '^javafx-.*-win\.jar$' } | Copy-Item -Destination $jpackageInput\ -Force

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
$png = [System.Drawing.Image]::FromFile("$PROJECT\autiva-front\src\main\resources\cn\bitloom\images\icon.png")

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
Remove-Item -Recurse -Force autiva-front\target\jpackage-output -ErrorAction SilentlyContinue
& "$JDK\bin\jpackage" `
  --type app-image `
  --name Autiva `
  --input autiva-front\target\jpackage-input `
  --main-jar autiva-front-1.0-SNAPSHOT.jar `
  --main-class cn.bitloom.AutivaApplication `
  --runtime-image $jlinkOutput `
  --java-options "-Xms128m" `
  --java-options "-Xmx512m" `
  --java-options "-XX:SharedArchiveFile=`$APPDIR/app-cds.jsa" `
  --java-options "-XX:+UseCompressedOops" `
  --java-options "-XX:+UseCompressedClassPointers" `
  --java-options "-XX:+UseStringDeduplication" `
  --dest autiva-front\target\jpackage-output `
  --app-version 1.0.0 `
  --vendor "Bitloom" `
  --description "Autiva AI Agent Desktop Application" `
  --icon autiva-front\target\jpackage-input\icon.ico
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Write-Host "=== Step 5: Generate CDS archive ===" -ForegroundColor Green
$appDir = "$PROJECT\autiva-front\target\jpackage-output\Autiva"
$cdsArchive = "$appDir\app-cds.jsa"

$proc = Start-Process "$appDir\Autiva.exe" -ArgumentList "-XX:ArchiveClassesAtExit=$cdsArchive" -PassThru

Write-Host "  Waiting for app to start and load classes..." -ForegroundColor Cyan
Start-Sleep -Seconds 15

if ($proc.HasExited -and $proc.ExitCode -ne 0) {
    Write-Host "  WARNING: App exited with error code $($proc.ExitCode)" -ForegroundColor Yellow
    Write-Host "  CDS archive not generated. Check console output above for errors." -ForegroundColor Yellow
} else {
    if (!$proc.HasExited) {
        Write-Host "  App is running, generating CDS archive..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
        $proc.Kill()
        $proc.WaitForExit(5000)
    }

    if (Test-Path $cdsArchive) {
        $cdsSize = "{0:N2} MB" -f ((Get-Item $cdsArchive).Length / 1MB)
        Write-Host "  CDS archive generated: $cdsSize" -ForegroundColor Cyan
    } else {
        Write-Host "  WARNING: CDS archive not generated" -ForegroundColor Yellow
        Write-Host "  You can generate it manually by running:" -ForegroundColor Yellow
        Write-Host "    $appDir\Autiva.exe -XX:ArchiveClassesAtExit=$cdsArchive" -ForegroundColor Yellow
        Write-Host "  Then close the app normally." -ForegroundColor Yellow
    }
}

$size = "{0:N2} MB" -f ((Get-ChildItem autiva-front\target\jpackage-output\Autiva -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)
Write-Host ""
Write-Host "=== Build Complete ===" -ForegroundColor Green
Write-Host "Output: autiva-front\target\jpackage-output\Autiva\Autiva.exe"
Write-Host "Size: $size"
Write-Host ""
Write-Host "NOTE: Remove --win-console from this script for production builds." -ForegroundColor Yellow
