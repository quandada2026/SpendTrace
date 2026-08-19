# ============================================
# 自动记账 App 一键构建脚本（规避本机环境坑）
# 用法：powershell -ExecutionPolicy Bypass -File build_fixed.ps1
# 说明：gradle.properties 已内置 native/watch/parallel/caching 规避配置，
#       本脚本负责清理失败构建遗留的 stale lock 缓存，然后 clean 全量构建。
# ============================================
$root = "C:\Users\AIM\WorkBuddy\2026-08-18-09-05-10\auto-ledger-android"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:ANDROID_HOME = "C:\Users\AIM\AndroidSdk"
$py = "C:\Users\AIM\.workbuddy\binaries\python\versions\3.13.12\python.exe"

Write-Host "[1/3] 清理 Gradle 缓存（stale lock 会卡死启动）..."
& $py "$root\cleanup_cache.py"

Write-Host "[2/3] clean assembleDebug ..."
Set-Location $root
& "$root\gradle-home\gradle-8.9\bin\gradle.bat" clean assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Host "构建失败，退出码 $LASTEXITCODE"; exit 1 }

Write-Host "[3/3] 复制 APK 到项目根目录..."
$apk = "$root\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Copy-Item $apk "$root\app-debug.apk" -Force
    Write-Host "APK_OK: $root\app-debug.apk"
} else {
    Write-Host "APK_MISSING"
}
