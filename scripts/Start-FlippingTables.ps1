param(
    [string]$ApiAccessFile,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$buildConfiguration = Get-Content -LiteralPath (Join-Path $projectDirectory 'build.gradle') -Raw
$versionMatch = [regex]::Match($buildConfiguration, "(?m)^version = '([0-9]+\.[0-9]+\.[0-9]+)'\r?$")
if (-not $versionMatch.Success) {
    throw 'Cannot read the client version from build.gradle.'
}
$jarPath = Join-Path $projectDirectory "build/libs/flippingtables-$($versionMatch.Groups[1].Value)-all.jar"
$runtimeDirectory = Join-Path $env:LOCALAPPDATA 'RuneLite/jre/bin'
$javaPath = Join-Path $runtimeDirectory 'java.exe'

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Build the personal client first with .\gradlew.bat test shadowJar.'
}
if (-not (Test-Path -LiteralPath $javaPath)) {
    throw 'Install the current official Windows RuneLite launcher, which includes the required Java runtime.'
}

$previousToken = $env:FLIPPING_TABLES_API_TOKEN
try {
    if ($ApiAccessFile) {
        $tokenLines = @(Get-Content -LiteralPath $ApiAccessFile | Where-Object { $_.StartsWith('API_BEARER_TOKEN=') })
        if ($tokenLines.Count -ne 1) {
            throw 'The access file must contain exactly one API_BEARER_TOKEN entry.'
        }
        $env:FLIPPING_TABLES_API_TOKEN = $tokenLines[0].Substring('API_BEARER_TOKEN='.Length)
    }
    $arguments = @(
        '-ea',
        '-jar',
        ('"' + $jarPath + '"')
    )
    if ($CheckOnly) {
        & $javaPath '-ea' '-jar' $jarPath '--help'
        if ($LASTEXITCODE -ne 0) {
            throw "RuneLite launcher check failed with exit code $LASTEXITCODE."
        }
    } else {
        $arguments += '--developer-mode'
        $javaWindowPath = Join-Path $runtimeDirectory 'javaw.exe'
        Start-Process -FilePath $javaWindowPath -ArgumentList $arguments -WorkingDirectory $projectDirectory -WindowStyle Hidden | Out-Null
    }
} finally {
    if ($null -eq $previousToken) {
        Remove-Item Env:FLIPPING_TABLES_API_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:FLIPPING_TABLES_API_TOKEN = $previousToken
    }
}
