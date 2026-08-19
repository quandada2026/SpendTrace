$project = "C:\Users\AIM\WorkBuddy\2026-08-18-09-05-10\auto-ledger-android"
$sdk = "C:\Users\AIM\AndroidSdk"
$java = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:JAVA_HOME = $java
$env:ANDROID_HOME = $sdk

Write-Host "[1/6] cmdline-tools ..."
if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    $zip = "$env:TEMP\cmdtools.zip"
    curl.exe -L -f -o $zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    Expand-Archive -Force $zip "$sdk\cmdline-tools\_tmp"
    Move-Item "$sdk\cmdline-tools\_tmp\cmdline-tools" "$sdk\cmdline-tools\latest" -Force
}

Write-Host "[2/6] licenses ..."
$yes = (1..80 | ForEach-Object { "y" }) -join "`n"
$yes | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses

Write-Host "[3/6] install components ..."
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
$ok = (Test-Path "$sdk\platforms\android-34") -and (Test-Path "$sdk\build-tools\34.0.0")
Write-Host "components present: $ok"

Write-Host "[4/6] download Gradle 8.9 ..."
$gh = "$project\gradle-home"
$gzip = "$env:TEMP\gradle.zip"
if (-not (Test-Path "$gh\gradle-8.9\bin\gradle.bat")) {
    New-Item -ItemType Directory -Force -Path $gh | Out-Null
    for ($i=1; $i -le 6; $i++) {
        curl.exe -L -f -o $gzip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
        if ($LASTEXITCODE -eq 0 -and (Test-Path $gzip) -and ((Get-Item $gzip).Length -gt 1MB)) { break }
        Write-Host "gradle download attempt $i failed, retry..."; Start-Sleep -Seconds 3
    }
    Expand-Archive -Force $gzip "$gh"
}

Write-Host "[5/6] local.properties ..."
"sdk.dir=$($sdk -replace '\\','/')" | Set-Content "$project\local.properties" -Encoding ASCII

Write-Host "[6/6] build ..."
Set-Location $project
& "$gh\gradle-8.9\bin\gradle.bat" assembleDebug --stacktrace --no-daemon

Write-Host "BUILD_DONE"
$apk = "$project\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) { Copy-Item $apk "$project\app-debug.apk" -Force; Write-Host "APK_OK" } else { Write-Host "APK_MISSING" }
