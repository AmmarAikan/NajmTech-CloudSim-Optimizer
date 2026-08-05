$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $bundledMaven = "C:\Program Files\NetBeans-18\netbeans\java\maven\bin\mvn.cmd"
    if (Test-Path -LiteralPath $bundledMaven) {
        $maven = Get-Item -LiteralPath $bundledMaven
    }
}

if (-not $maven) {
    throw "Apache Maven 3.9+ is required. Install Maven or open the project in NetBeans."
}

Push-Location $projectRoot
try {
    & $maven.FullName --batch-mode --no-transfer-progress clean verify
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed."
    }
    java -jar "target\najmtech-cloudsim-optimizer-1.0.0.jar"
    if ($LASTEXITCODE -ne 0) {
        throw "Simulation execution failed."
    }
} finally {
    Pop-Location
}
