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
[System.IO.File]::WriteAllBytes("$PROJECT\autiva-front\target\jpackage-input\icon.ico", $icoStream.ToArray())
$bw.Dispose()
$icoStream.Dispose()

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
