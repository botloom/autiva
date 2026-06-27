$desktop = [System.Environment]::GetFolderPath('Desktop')
Write-Host "桌面路径: $desktop"
Write-Host ""
$items = Get-ChildItem -Path $desktop
foreach ($item in $items) {
    $type = if ($item.PSIsContainer) { "文件夹" } else { "文件" }
    $size = if ($item.PSIsContainer) { "-" } else { "{0:N2} KB" -f ($item.Length / 1KB) }
    Write-Host "$($item.Name)|$type|$size|$($item.LastWriteTime)"
}
