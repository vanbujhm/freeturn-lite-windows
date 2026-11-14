param(
    [string]$Version = "v4.1.5"
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CoreDir = Join-Path $ProjectDir "core"
$CoreExe = Join-Path $CoreDir "free-turn-proxy.exe"
$Download = Join-Path $CoreDir "free-turn-proxy.download"
$Checksums = Join-Path $CoreDir "checksums.txt"
$BaseUrl = "https://github.com/hackdiaz-dev/free-turn-proxy/releases/download/$Version"

New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null

Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/client-windows-amd64.exe" -OutFile $Download
Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/checksums.txt" -OutFile $Checksums

$expectedLine = Select-String -Path $Checksums -Pattern "client-windows-amd64\.exe" |
    Select-Object -First 1

if (-not $expectedLine) {
    throw "client-windows-amd64.exe checksum not found in checksums.txt"
}

$expected = $expectedLine.Line.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -Path $Download).Hash.ToLowerInvariant()

if ($actual -ne $expected) {
    Remove-Item -LiteralPath $Download -Force
    throw "Checksum mismatch: expected $expected actual $actual"
}

Move-Item -Force -LiteralPath $Download -Destination $CoreExe

Write-Host "Downloaded: $CoreExe"
Write-Host "Version: $Version"
Write-Host "SHA256: $actual"
