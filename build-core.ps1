param(
    [string]$Version = "v4.1.5",
    [string]$Repository = "https://github.com/hackdiaz-dev/free-turn-proxy.git"
)

# Собирает ядро из исходников и накладывает патчи из core-patches.
# Релизное ядро не умеет включать продвинутую капчу из командной строки:
# флаг -advanced-captcha добавляет патч 0001-cli-advanced-captcha.patch.

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CoreDir = Join-Path $ProjectDir "core"
$SourceDir = Join-Path $CoreDir "src"
$PatchDir = Join-Path $ProjectDir "core-patches"
$CoreExe = Join-Path $CoreDir "free-turn-proxy.exe"
$Output = Join-Path $CoreDir "free-turn-proxy.build"

function Invoke-Checked {
    param([string]$Step, [scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE" }
}

if (-not (Get-Command go -ErrorAction SilentlyContinue)) { throw "go not found in PATH" }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw "git not found in PATH" }

New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null
if (Test-Path $SourceDir) { Remove-Item -LiteralPath $SourceDir -Recurse -Force }

# autocrlf=false: патчи записаны с LF и иначе не наложатся на CRLF-копию.
Invoke-Checked "git clone" {
    git -c core.autocrlf=false -c advice.detachedHead=false clone --quiet --depth 1 --branch $Version $Repository $SourceDir
}

$Patches = Get-ChildItem -Path $PatchDir -Filter *.patch | Sort-Object Name
foreach ($patch in $Patches) {
    Invoke-Checked "git apply $($patch.Name)" { git -C $SourceDir apply --whitespace=nowarn $patch.FullName }
    Write-Host "Applied: $($patch.Name)"
}

# Без GOTOOLCHAIN=local go пытается скачать нужный тулчейн и может зависнуть;
# пусть лучше сразу скажет, что версия Go мала.
$env:GOTOOLCHAIN = "local"
$env:CGO_ENABLED = "0"
$env:GOOS = "windows"
$env:GOARCH = "amd64"

$Required = (Select-String -Path (Join-Path $SourceDir "go.mod") -Pattern "^go\s+(\S+)").Matches[0].Groups[1].Value
$Installed = (go env GOVERSION) -replace "^go", ""
if ([version]$Installed -lt [version]$Required) {
    throw "Go $Required or newer is required, found $Installed"
}

$CoreVersion = $Version.TrimStart("v") + "+advanced-captcha"
Push-Location $SourceDir
try {
    # Флаги как в .goreleaser.yaml ядра.
    Invoke-Checked "go build" {
        go build -trimpath -ldflags "-s -w -checklinkname=0 -X main.version=$CoreVersion" -o $Output ./cmd/client
    }
} finally {
    Pop-Location
}

Move-Item -Force -LiteralPath $Output -Destination $CoreExe

Write-Host "Built: $CoreExe"
Write-Host "Version: $CoreVersion"
Write-Host "SHA256: $((Get-FileHash -Algorithm SHA256 -Path $CoreExe).Hash.ToLowerInvariant())"
