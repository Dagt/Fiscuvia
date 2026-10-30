$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
function New-LocalPassword {
    [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
}
$content = "DB_PORT=55433`nDB_BOOTSTRAP_PASSWORD=$(New-LocalPassword)`nDB_MIGRATION_PASSWORD=$(New-LocalPassword)`nDB_RUNTIME_PASSWORD=$(New-LocalPassword)`n"
# CreateNew prevents accidental replacement of credentials for an existing volume.
$stream = [IO.File]::Open($envFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($content)
    $stream.Write($bytes, 0, $bytes.Length)
} finally { $stream.Dispose() }
Write-Output 'Created infra/.env with synthetic local credentials. Keep this ignored file private.'
