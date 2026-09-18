$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$content = Get-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Raw
if ($content -notmatch '(?m)^splitpay.demoToken=([a-zA-Z0-9]+)') { throw 'Configure demo first.' }
$headers = @{ Authorization = 'Bearer ' + $Matches[1] }
$baseUrl = 'http://127.0.0.1:8080'
$health = Invoke-RestMethod -Uri "$baseUrl/api/health" -TimeoutSec 15
if ($health.status -ne 'UP') { throw 'Health check failed.' }
$anonymousStatus = 0
try { Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/users/me" -TimeoutSec 15 | Out-Null }
catch { if ($_.Exception.Response) { $anonymousStatus = [int]$_.Exception.Response.StatusCode } else { throw } }
if ($anonymousStatus -ne 401) { throw "Expected anonymous HTTP 401, got $anonymousStatus" }
$first = Invoke-RestMethod -Uri "$baseUrl/api/users/me" -Headers $headers -TimeoutSec 15
$second = Invoke-RestMethod -Uri "$baseUrl/api/users/me" -Headers $headers -TimeoutSec 15
if ($first.id -ne $second.id -or $first.email -ne 'pratik@splitpay.demo') { throw 'Profile identity synchronization failed.' }
Write-Output 'PASS: backend health, anonymous rejection, authenticated profile, stable identity.'
