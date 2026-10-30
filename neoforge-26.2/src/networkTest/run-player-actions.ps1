param([int] $Port = 25581)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid loopback test port' }
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$root = Join-Path $project ('build\player-action-run-' + [guid]::NewGuid().ToString('N') + '\build\player-action-acceptance')
$utf8 = New-Object Text.UTF8Encoding($false)
foreach ($directory in @('server', 'client\config')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $root $directory)
}
foreach ($relative in @('resources\networkTest', 'resources\persistenceTest', 'resources\visualTest',
        'common\resources\main')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $project "build\$relative") -Force
}
$launches = @{}
foreach ($side in @('server', 'client')) {
    $name = 'NetworkAcceptance' + (Get-Culture).TextInfo.ToTitleCase($side)
    $export = Join-Path $project "build\acceptance-launches\$name"
    $metadata = Get-Content "$export.json" -Raw | ConvertFrom-Json
    if ($metadata.schemaVersion -ne 2 -or !$metadata.exportedOnly -or
            [IO.Path]::GetFileName($metadata.executable) -ne 'java.exe' -or
            !(Test-Path -LiteralPath $metadata.executable -PathType Leaf)) {
        throw "Missing or invalid prepared dev launch: $name"
    }
    $argsText = [IO.File]::ReadAllText("$export.args")
    if ($argsText -notmatch 'net\.neoforged\.devlaunch\.Main' -or
            $argsText -notmatch '-Dfarmupcraft\.networkAcceptance=true') {
        throw "Unexpected launch definition: $name"
    }
    $oldPeer = Join-Path $project "build\network-acceptance\$side"
    $newPeer = Join-Path $root $side
    $argsText = $argsText.Replace('-Dfarmupcraft.networkAcceptance=true',
            '-Dfarmupcraft.networkAcceptance=false')
    $argsText = $argsText.Replace($oldPeer.Replace('\', '\\'), $newPeer.Replace('\', '\\'))
    $argsText = $argsText.Replace($oldPeer, $newPeer)
    if ($side -eq 'client') {
        if ($argsText -notmatch '127\.0\.0\.1:25576') { throw 'Unexpected client QuickPlay endpoint' }
        $argsText = $argsText.Replace('127.0.0.1:25576', "127.0.0.1:$Port")
    }
    $properties = @(
        '"-Dfarmupcraft.playerActionAcceptance=true"'
        ('"-Dfarmupcraft.playerActionAcceptanceRoot=' + $root.Replace('\', '\\') + '"')
        '"-Dmax.bg.threads=1"'
    ) -join "`n"
    $argsPath = Join-Path $root "$side.args"
    [IO.File]::WriteAllText($argsPath, $properties + "`n" + $argsText, $utf8)
    $launches[$side] = @{ executable = $metadata.executable; arguments = $argsPath }
}
$settings = @(
    'server-ip=127.0.0.1'
    "server-port=$Port"
    'online-mode=false'
    'level-name=player-action-world'
    'level-type=minecraft:flat'
    'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"lakes":false,"features":false,"structure_overrides":[]}'
    'generate-structures=false'
    'spawn-protection=0'
    'gamemode=survival'
    'view-distance=2'
    'simulation-distance=2'
    'pause-when-empty-seconds=0'
) -join "`n"
[IO.File]::WriteAllText((Join-Path $root 'server\server.properties'), $settings + "`n", $utf8)
[IO.File]::WriteAllText((Join-Path $root 'client\config\neoforge-client.toml'), "showLoadWarnings = false`n", $utf8)
[IO.File]::WriteAllText((Join-Path $root 'client\options.txt'), "onboardAccessibility:false`n", $utf8)
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
try { $listener.Start() } finally { $listener.Stop() }
$owned = @()
$clock = [Diagnostics.Stopwatch]::StartNew()
function StartPeer([string] $Side) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $launches[$Side].executable
    $info.Arguments = '"@' + $launches[$Side].arguments + '"'
    $info.WorkingDirectory = Join-Path $root $Side
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($key in @('CLASSPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
            'MOD_CLASSES', 'MOD_FOLDERS')) {
        $info.EnvironmentVariables.Remove($key)
    }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    if (!$process.Start()) { throw "Cannot start $Side" }
    Write-Output "STARTED $Side PID=$($process.Id)" | Out-Host
    return @{ side = $Side; process = $process
        stdout = $process.StandardOutput.ReadToEndAsync()
        stderr = $process.StandardError.ReadToEndAsync() }
}
function CheckDeadline {
    if ($clock.Elapsed.TotalSeconds -gt 300) { throw 'Absolute 300-second runtime deadline exceeded' }
}
Write-Output "ACCEPTANCE_ROOT=$root"
try {
    $server = StartPeer 'server'; $owned += $server
    do {
        CheckDeadline
        if ($server.process.HasExited) { throw 'Server exited before readiness' }
        Start-Sleep -Seconds 1
        $log = Join-Path $root 'server\logs\latest.log'
        $ready = (Test-Path $log) -and (Select-String -LiteralPath $log -Pattern 'Done \(.+\)! For help' -Quiet)
    } until ($ready)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Where-Object OwningProcess -eq $server.process.Id)
    if ($listeners.Count -ne 1) { throw 'Ready server does not own the expected listener' }
    $client = StartPeer 'client'; $owned += $client
    do {
        CheckDeadline
        foreach ($peer in $owned) {
            $receipt = Join-Path $root "$($peer.side)\player-action-receipt.txt"
            if ((Test-Path $receipt) -and (Select-String $receipt -Pattern '^FAIL ' -Quiet)) {
                throw "$($peer.side) reported failed acceptance"
            }
            if ($peer.process.HasExited -and $peer.process.ExitCode -ne 0) {
                throw "$($peer.side) exited nonzero"
            }
        }
        if ($server.process.HasExited -and !$client.process.HasExited) {
            throw 'Server exited before the client'
        }
        Start-Sleep -Seconds 1
    } until ($server.process.HasExited -and $client.process.HasExited)
    foreach ($side in @('server', 'client')) {
        $receipt = Join-Path $root "$side\player-action-receipt.txt"
        if (!(Test-Path $receipt) -or !(Select-String $receipt -Pattern '^PASS ' -Quiet)) {
            throw "$side missing final acceptance receipt"
        }
        Get-Content $receipt
    }
    Write-Output 'VERIFIED separate peers exit0; authoritative non-op survival movement/break/place'
} finally {
    foreach ($peer in $owned) {
        $process = $peer.process
        if (!$process.HasExited) {
            if ($peer.side -eq 'server') {
                $process.StandardInput.WriteLine('stop')
                $process.StandardInput.Flush()
            } else {
                $null = $process.CloseMainWindow()
            }
            if (!$process.WaitForExit(10000)) {
                Stop-Process -Id $process.Id -Force -ErrorAction Stop
                $process.WaitForExit()
                Write-Warning "Forced cleanup of owned $($peer.side) PID=$($process.Id)"
            }
        }
        [IO.File]::WriteAllText((Join-Path $root "$($peer.side)\console.txt"),
                $peer.stdout.GetAwaiter().GetResult() + $peer.stderr.GetAwaiter().GetResult(), $utf8)
        $process.Dispose()
    }
}
