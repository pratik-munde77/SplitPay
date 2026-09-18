param([int]$Port = 8080)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot 'backend/.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Create backend/.env first.' }
$names = @('DATABASE_URL','POSTGRES_USER','POSTGRES_PASSWORD','FIREBASE_PROJECT_ID','GOOGLE_APPLICATION_CREDENTIALS','GEMINI_API_KEY','GEMINI_MODEL','PAYPAL_CLIENT_ID','PAYPAL_CLIENT_SECRET','PAYPAL_WEBHOOK_ID','PAYPAL_RETURN_URL','PAYPAL_SANDBOX_INR_PER_USD')
$previous = @{}
try {
    foreach ($line in Get-Content -LiteralPath $envPath) {
        if ($line -match '^\s*([A-Z_]+)\s*=(.*)$' -and $Matches[1] -in $names) {
            $name = $Matches[1]
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
    if ($env:DATABASE_URL -notmatch '^jdbc:postgresql://') { throw 'DATABASE_URL must be a JDBC PostgreSQL URL.' }
    foreach ($name in @('PORT','SERVER_ADDRESS','SPRING_PROFILES_ACTIVE')) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    $env:PORT = "$Port"
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SPRING_PROFILES_ACTIVE = 'default'
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & java -jar build/libs/splitpay-backend-0.1.0.jar
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
