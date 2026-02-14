$ErrorActionPreference = "Stop"

# 仅清理可再生文件，避免误删源码与配置。
$targets = @(
    ".\app\build",
    ".\tmp_src_test",
    ".\tmp-gradle-plugin",
    ".\.tmp-wrapper"
)

Write-Host "开始清理项目冗余目录..."

foreach ($path in $targets) {
    if (Test-Path $path) {
        Remove-Item -Path $path -Recurse -Force
        Write-Host "已删除: $path"
    } else {
        Write-Host "跳过(不存在): $path"
    }
}

Write-Host "清理完成。"
