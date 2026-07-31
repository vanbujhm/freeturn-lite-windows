param(
    [string]$JdkHome = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ProjectDir "build"
$ClassesDir = Join-Path $BuildDir "classes"
$LibsDir = Join-Path $BuildDir "libs"
$ImageDir = Join-Path $BuildDir "jpackage"
$JarPath = Join-Path $LibsDir "freeturn-lite-windows.jar"
$Javac = Join-Path $JdkHome "bin\javac.exe"
$Jar = Join-Path $JdkHome "bin\jar.exe"
$Jpackage = Join-Path $JdkHome "bin\jpackage.exe"

if (-not (Test-Path $Javac)) { throw "javac.exe not found: $Javac" }
if (-not (Test-Path $Jar)) { throw "jar.exe not found: $Jar" }
if (-not (Test-Path $Jpackage)) { throw "jpackage.exe not found: $Jpackage" }

if (Test-Path $BuildDir) { Remove-Item -LiteralPath $BuildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ClassesDir, $LibsDir | Out-Null

$Sources = Get-ChildItem -Path (Join-Path $ProjectDir "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }

& $Javac --release 17 -encoding UTF-8 -d $ClassesDir @Sources
& $Jar --create --file $JarPath -C $ClassesDir .

& $Jpackage `
    --type app-image `
    --name FreeTurnLite `
    --input $LibsDir `
    --main-jar "freeturn-lite-windows.jar" `
    --main-class "com.freeturn.lite.FreeTurnLiteApp" `
    --dest $ImageDir `
    --java-options "-Dfile.encoding=UTF-8"

$CoreSource = Join-Path $ProjectDir "core\free-turn-proxy.exe"
$CoreDestDir = Join-Path $ImageDir "FreeTurnLite\core"
if (Test-Path $CoreSource) {
    New-Item -ItemType Directory -Force -Path $CoreDestDir | Out-Null
    Copy-Item -Force -Path $CoreSource -Destination (Join-Path $CoreDestDir "free-turn-proxy.exe")
}

Write-Host "Built: $(Join-Path $ImageDir 'FreeTurnLite\FreeTurnLite.exe')"
