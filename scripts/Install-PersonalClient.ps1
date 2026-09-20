param(
    [string]$ApiAccessFile,
    [string]$InstallDirectory = (Join-Path $env:LOCALAPPDATA 'FlippingTables')
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$buildConfiguration = Get-Content -LiteralPath (Join-Path $projectDirectory 'build.gradle') -Raw
$versionMatch = [regex]::Match($buildConfiguration, "(?m)^version = '([0-9]+\.[0-9]+\.[0-9]+)'\r?$")
if (-not $versionMatch.Success) {
    throw 'Cannot read the client version from build.gradle.'
}
$version = $versionMatch.Groups[1].Value
$jarName = "flippingtables-$version-all.jar"
$sourceJar = Join-Path $projectDirectory "build/libs/$jarName"
if (-not (Test-Path -LiteralPath $sourceJar)) {
    throw 'Build the personal client first with .\gradlew.bat test shadowJar.'
}
if ($ApiAccessFile -and -not (Test-Path -LiteralPath $ApiAccessFile)) {
    throw 'The specified API access file does not exist.'
}
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$outputDirectory = Join-Path $projectDirectory 'build/windows-launcher'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$compiledLauncher = Join-Path $outputDirectory 'FlippingTables.exe'
& $compiler /nologo /target:winexe /optimize+ /reference:System.Windows.Forms.dll "/out:$compiledLauncher" (Join-Path $projectDirectory 'launcher/FlippingTablesLauncher.cs')
if ($LASTEXITCODE -ne 0) {
    throw 'The Windows launcher did not compile.'
}
$installedLibraries = Join-Path $InstallDirectory 'build/libs'
New-Item -ItemType Directory -Path $installedLibraries -Force | Out-Null
Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $installedLibraries $jarName) -Force
Copy-Item -LiteralPath $compiledLauncher -Destination (Join-Path $InstallDirectory 'FlippingTables.exe') -Force
Set-Content -LiteralPath (Join-Path $InstallDirectory 'client-version.txt') -Value $version -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $projectDirectory 'README.md') -Destination (Join-Path $InstallDirectory 'README.md') -Force
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Flipping Tables (RuneLite).lnk'
$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut($shortcutPath)
$existingArguments = $shortcut.Arguments
$shortcut.TargetPath = Join-Path $InstallDirectory 'FlippingTables.exe'
$shortcut.WorkingDirectory = $InstallDirectory
if ($ApiAccessFile) {
    $shortcut.Arguments = '--api-access-file "' + [IO.Path]::GetFullPath($ApiAccessFile) + '"'
} elseif ($existingArguments -match '(?:-ApiAccessFile|--api-access-file)\s+"([^"\r\n]+)"') {
    $shortcut.Arguments = '--api-access-file "' + $matches[1] + '"'
} else {
    $shortcut.Arguments = ''
}
$shortcut.IconLocation = Join-Path $InstallDirectory 'FlippingTables.exe'
$shortcut.Save()
Write-Output "Installed Flipping Tables $version with its native Windows launcher."
