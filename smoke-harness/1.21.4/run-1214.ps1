# Real 1.21.4 live smoke harness (v26.8-Alpha.9)
# Launches the genuine obfuscated 1.21.4 client under the Aprism agent with
# intermediary + official mapping assets and a content-registering smoke mod.
# Uses a Java @argfile so no token can be split by shell re-quoting.
# GitHub@NDBlockConnect | BlockConnect@StarsailsClover
param(
    [int]$PollSeconds = 210,
    [string]$JavaHome = 'C:\Users\Sails\Java\jdk-25.0.3+9'
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $java)) { throw "java.exe not found at $java" }

$agentJar = [System.IO.Path]::GetFullPath((Join-Path $here '..\..\..\aprism-loader-core\build\libs\Aprism-v26.8-Alpha.9-JE-26.2.jar'))
if (-not (Test-Path $agentJar)) { throw "agent jar not found: $agentJar" }

# Classpath: every library jar under the remap env plus the obfuscated client.
$cp = (Get-ChildItem $here -Recurse -Filter *.jar |
        Where-Object { $_.FullName -notmatch '\\gamedir' } |
        ForEach-Object { $_.FullName }) -join ';'
$cp = "$cp;$(Join-Path $here 'client-1.21.4.jar')"

$agentArgs = 'aprismVersion=v26.8-Alpha.9;mcEdit=JE;mcVersion=1.21.4;' +
    "gameRoot=$(Join-Path $here 'gamedir-1214');" +
    "mappings=$(Join-Path $here '1.21.4.tiny');" +
    "officialMappings=$(Join-Path $here '1.21.4-client.txt')"

$libPathArg = '-Djava.library.path=' + (Join-Path $here 'natives')
$lines = @(
    '-Xmx4G',
    $libPathArg,
    '--enable-native-access=ALL-UNNAMED',
    "-javaagent:$agentJar=$agentArgs",
    '-cp',
    $cp,
    'net.minecraft.client.main.Main',
    '--gameDir', (Join-Path $here 'gamedir-1214'),
    '--assetsDir', (Join-Path $here 'assets'),
    '--assetIndex', '19',
    '--username', 'Player407',
    '--version', '1.21.4',
    '--accessToken', '0',
    '--userProperties', '{}'
)
$argFile = Join-Path $here 'java-args.txt'
Set-Content -Path $argFile -Value $lines -Encoding Ascii

$outLog = Join-Path $here 'run-a9-out.log'
$errLog = Join-Path $here 'run-a9-err.log'
Remove-Item $outLog, $errLog -ErrorAction SilentlyContinue

$proc = Start-Process -FilePath $java -ArgumentList "@$argFile" `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -WorkingDirectory $here -PassThru
"PID $($proc.Id) launched; polling $PollSeconds s for binding evidence..."

$deadline = (Get-Date).AddSeconds($PollSeconds)
$done = $false
while ((Get-Date) -lt $deadline -and -not $done) {
    Start-Sleep -Seconds 5
    if (Test-Path $errLog) {
        $probe = Select-String -Path $errLog -Pattern 'gate\]|Content binding|PROFILE_UNSUPPORTED|TARGET_UNRESOLVED|RealSmokeMod' -ErrorAction SilentlyContinue
        if ($probe) { $probe | ForEach-Object { $_.Line } }
        if ($probe | Where-Object { $_.Line -match 'Content binding' }) { $done = $true }
    }
    if ($proc.HasExited) { "process exited code $($proc.ExitCode)"; break }
}

if (-not $proc.HasExited) {
    "stopping PID $($proc.Id)"
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
}
'--- ERR evidence ---'
Select-String -Path $errLog -Pattern 'gate\]|Content binding|PROFILE_UNSUPPORTED|TARGET_UNRESOLVED|RealSmokeMod|Load Report|OK  \] mod' -ErrorAction SilentlyContinue | ForEach-Object { $_.Line }
'--- OUT tail ---'
Get-Content $outLog -Tail 6 -ErrorAction SilentlyContinue
