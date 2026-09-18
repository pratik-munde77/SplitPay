$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$externalNames = @('GEMINI_API_KEY','GEMINI_MODEL','PAYPAL_CLIENT_ID','PAYPAL_CLIENT_SECRET','PAYPAL_WEBHOOK_ID','PAYPAL_RETURN_URL','PAYPAL_SANDBOX_INR_PER_USD','FIREBASE_PROJECT_ID','GOOGLE_APPLICATION_CREDENTIALS')
$previousEnvironment = @{}
$envPath = Join-Path $projectRoot 'backend/.env'
if (Test-Path -LiteralPath $envPath) {
    foreach ($line in Get-Content -LiteralPath $envPath) {
        if ($line -match '^([A-Z_]+)=(.*)$' -and $Matches[1] -in $externalNames) {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}
$content = Get-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Raw
if ($content -notmatch '(?m)^splitpay.demoToken=([a-zA-Z0-9]+)') { throw 'Run scripts/configure-demo.ps1 first.' }
$env:SPLITPAY_DEMO_TOKEN = $Matches[1]
$env:SPRING_PROFILES_ACTIVE = 'demo'
Push-Location (Join-Path $projectRoot 'backend')
try { & java -jar build/libs/splitpay-backend-0.1.0.jar } finally {
    Pop-Location
    Remove-Item Env:SPLITPAY_DEMO_TOKEN
    Remove-Item Env:SPRING_PROFILES_ACTIVE
    foreach ($name in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
}
