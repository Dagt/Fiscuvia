$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $path)) { throw 'Generate infra/.env first.' }
$content = [IO.File]::ReadAllText($path)
foreach ($name in @('DB_SESSION_PASSWORD', 'FISCUVIA_ACTOR_PROOF_KEY')) {
    if ($content -notmatch "(?m)^$name=.+$") {
        $value = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        if ($content -match "(?m)^$name=.*$") { $content = [regex]::Replace($content, "(?m)^$name=.*$", "$name=$value") }
        else { $content = $content.TrimEnd() + "`n$name=$value`n" }
    }
}
[IO.File]::WriteAllText($path, $content, [Text.UTF8Encoding]::new($false))
Write-Output 'Private local identity settings prepared; existing values preserved. Recreate PostgreSQL and run the role upgrade before V3.'
