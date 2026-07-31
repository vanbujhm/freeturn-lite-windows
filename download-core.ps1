param(
    [string]$Version = "v1.7.2"
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CoreDir = Join-Path $ProjectDir "core"
$CoreExe = Join-Path $CoreDir "free-turn-proxy.exe"
$Checksums = Join-Path $CoreDir "checksums.txt"
$BaseUrl = "https://github.com/samosvalishe/free-turn-proxy/releases/download/$Version"

New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null

Invoke-WebRequest -Uri "$BaseUrl/client-windows-amd64.exe" -OutFile $CoreExe
Invoke-WebRequest -Uri "$BaseUrl/checksums.txt" -OutFile $Checksums

$expectedLine = Select-String -Path $Checksums -Pattern "client-windows-amd64\.exe" |
    Select-Object -First 1

if (-not $expectedLine) {
    throw "client-windows-amd64.exe checksum not found in checksums.txt"
}

$expected = $expectedLine.Line.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -Path $CoreExe).Hash.ToLowerInvariant()

if ($actual -ne $expected) {
    throw "Checksum mismatch: expected $expected actual $actual"
}

Write-Host "Downloaded: $CoreExe"
Write-Host "SHA256: $actual"
