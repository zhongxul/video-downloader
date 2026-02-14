$env:JAVA_HOME = "D:\AndroidStudioJBR"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:GRADLE_USER_HOME = "D:\gradle-home"

if (!(Test-Path $env:GRADLE_USER_HOME)) {
    New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME | Out-Null
}

$sdkTools = @(
    "D:\Android\Sdk\platform-tools",
    "D:\Android\Sdk\emulator",
    "D:\Android\Sdk\cmdline-tools\latest\bin"
)

foreach ($p in $sdkTools) {
    if (Test-Path $p) {
        $env:Path = "$p;$env:Path"
    }
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "环境变量已注入当前PowerShell会话。"
