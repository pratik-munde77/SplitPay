$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Android SDK platform-tools not found. Install it through Android Studio SDK Manager.' }
$devices = @(& $adb devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) { throw 'Connect exactly one USB-debugging phone, accept its authorization prompt, and retry.' }
$apk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Build :app:assembleDebug first.' }
& $adb reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { throw 'Could not configure USB backend access.' }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb shell am start -W -n com.example.splitpay/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
Write-Output 'Installed and launched. Keep scripts/run-demo.ps1 running for backend access.'
