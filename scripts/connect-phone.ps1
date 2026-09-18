param([int]$BackendPort = 8081)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    $adb = (Get-Command adb -ErrorAction Stop).Source
}
$health = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/health" -TimeoutSec 10
if ($health.status -ne 'UP') { throw 'Backend health check failed.' }
& $adb reverse tcp:8080 "tcp:$BackendPort"
if ($LASTEXITCODE -ne 0) { throw 'USB forwarding failed. Connect one phone and authorize USB debugging.' }
Write-Output "Phone localhost:8080 now forwards to PC localhost:$BackendPort. Keep USB connected."
