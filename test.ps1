param(
    [string]$JdkHome = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ClassesDir = Join-Path $ProjectDir "build\test-classes"
$CoreExe = Join-Path $ProjectDir "core\free-turn-proxy.exe"
$Javac = Join-Path $JdkHome "bin\javac.exe"
# javaw: у приложения нет консоли, и тест остановки ядра должен идти в тех же условиях.
$Javaw = Join-Path $JdkHome "bin\javaw.exe"

if (-not (Test-Path $Javac)) { throw "javac.exe not found: $Javac" }

if (Test-Path $ClassesDir) { Remove-Item -LiteralPath $ClassesDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

$Sources = Get-ChildItem -Path (Join-Path $ProjectDir "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }

& $Javac --release 17 -encoding UTF-8 -d $ClassesDir @Sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$TestArgs = @()
if (Test-Path $CoreExe) {
    $TestArgs += $CoreExe
} else {
    Write-Host "Core binary not found, skipping tests that run it: $CoreExe"
}

& $Javaw "-Dfile.encoding=UTF-8" -cp $ClassesDir com.freeturn.lite.CoreConfigTest @TestArgs | Out-Host
exit $LASTEXITCODE
