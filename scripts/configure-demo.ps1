$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $projectRoot 'local.properties'
if (Test-Path -LiteralPath $propertiesPath) {
    $content = Get-Content -LiteralPath $propertiesPath -Raw
} else {
    $sdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (!(Test-Path -LiteralPath $sdkPath)) { throw 'Open this project in Android Studio and configure the Android SDK first.' }
    $content = 'sdk.dir=' + $sdkPath.Replace('\','/') + "`n"
}
if ($content -notmatch '(?m)^splitpay.demoToken=([a-zA-Z0-9]+)') {
    $demoToken = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $content = $content.TrimEnd() + "`nsplitpay.demoToken=$demoToken`n"
}
$content = [regex]::Replace($content, '(?m)^splitpay.apiBaseUrl=.*\r?\n?', '')
$content = $content.TrimEnd() + "`nsplitpay.apiBaseUrl=http://127.0.0.1:8080/`n"
[IO.File]::WriteAllText($propertiesPath, $content)
Write-Output 'Local demo configured. The token remains in ignored local.properties. Rebuild Android and run scripts/run-demo.ps1.'
