param([int] $Port = 25581, [switch] $SpeechBubbles, [switch] $GemRender, [switch] $SoundRetry, [switch] $MapFilter, [string] $JourneyMapJar)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid loopback test port' }
if ($SpeechBubbles -and $GemRender) { throw 'Choose only one visual probe' }
if ($SoundRetry -and !$GemRender) { throw 'SoundRetry uses the GemRender client fixture' }
if ($MapFilter -and !$GemRender) { throw 'MapFilter uses the GemRender client fixture' }
if ($MapFilter -and ([string]::IsNullOrWhiteSpace($JourneyMapJar) -or !(Test-Path -LiteralPath $JourneyMapJar -PathType Leaf))) {
    throw 'MapFilter requires a JourneyMapJar path'
}
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$root = Join-Path $project ('build\player-action-run-' + [guid]::NewGuid().ToString('N') + '\build\player-action-acceptance')
$utf8 = New-Object Text.UTF8Encoding($false)
foreach ($directory in @('server', 'client\config')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $root $directory)
}
if ($MapFilter) {
    $mods = Join-Path $root 'client\mods'
    $null = New-Item -ItemType Directory -Path $mods
    Copy-Item -LiteralPath $JourneyMapJar -Destination $mods
    $discoveries = Join-Path $root 'server\player-action-world\farmupcraft\scans\minecraft\overworld'
    $null = New-Item -ItemType Directory -Path $discoveries -Force
    [IO.File]::WriteAllText((Join-Path $discoveries 'findings.json'),
        '[{"type":"lake","name":"LoginProbe-100","block_count":100,"center":{"x":32,"z":36},"bounding_box":{"min_x":27,"max_x":37,"min_z":31,"max_z":41}}]', $utf8)
    $mapConfig = Join-Path $root 'client\journeymap\config\6.0'
    $null = New-Item -ItemType Directory -Path $mapConfig -Force
    [IO.File]::WriteAllText((Join-Path $mapConfig 'journeymap.fullmap.config'),
        '{"showWaypointLabels":"true","showWaypoints":"true","fontScale":"4.97","waypointLabelScale":"3.49","waypointIconScale":"2.1","zoomLevel":"4096"}', $utf8)
    [IO.File]::WriteAllText((Join-Path $mapConfig 'journeymap.core.config'),
        '{"splashViewed":"6.0.10","optionsManagerViewed":"6.0.10","checkUpdates":"false"}', $utf8)
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
        $(if ($GemRender) { '"-Dfarmupcraft.gemRenderAcceptance=true"' }
          elseif ($SpeechBubbles) { '"-Dfarmupcraft.speechBubbleAcceptance=true"' }
          else { '"-Dfarmupcraft.playerActionAcceptance=true"' })
        ('"-Dfarmupcraft.playerActionAcceptanceRoot=' + $root.Replace('\', '\\') + '"')
        '"-Dmax.bg.threads=1"'
        $(if ($SoundRetry) { '"-Dfarmupcraft.soundRetryAcceptance=true"' })
        $(if ($MapFilter) { '"-Dfarmupcraft.mapFilterAcceptance=true"' })
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
[IO.File]::WriteAllText((Join-Path $root 'client\options.txt'), "onboardAccessibility:false`npauseOnLostFocus:false`n", $utf8)
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
    $stopRequested = $false
    $renameRequested = $false
    do {
        CheckDeadline
        foreach ($peer in $owned) {
            $receipt = Join-Path $root "$($peer.side)\player-action-receipt.txt"
            if ((Test-Path $receipt) -and (Select-String $receipt -Pattern '^FAIL ' -Quiet)) {
                throw "$($peer.side) reported failed acceptance"
            }
            if ($MapFilter -and !$renameRequested) {
                $receipt = Join-Path $root 'client\player-action-receipt.txt'
                if ((Test-Path $receipt) -and (Select-String -LiteralPath $receipt -Pattern '^PASS map filter transformed client:' -Quiet)) {
                    $server.process.StandardInput.WriteLine('scan rename-lakes confirm')
                    $server.process.StandardInput.Flush()
                    $renameRequested = $true
                }
            }
            if ($peer.process.HasExited -and $peer.process.ExitCode -ne 0) {
                throw "$($peer.side) exited nonzero"
            }
        }
        if ($server.process.HasExited -and !$client.process.HasExited) {
            throw 'Server exited before the client'
        }
        if (($SpeechBubbles -or $GemRender) -and $client.process.HasExited -and !$server.process.HasExited -and !$stopRequested) {
            $server.process.StandardInput.WriteLine('stop')
            $server.process.StandardInput.Flush()
            $stopRequested = $true
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
    if ($GemRender) {
        if ($SoundRetry -and !(Select-String (Join-Path $root 'client\player-action-receipt.txt') -Pattern '^PASS sound retry transformed client:' -Quiet)) {
            throw 'Client missing sound cooldown acceptance receipt'
        }
        if ($MapFilter -and !(Select-String (Join-Path $root 'client\player-action-receipt.txt') -Pattern '^PASS map filter transformed client:' -Quiet)) {
            throw 'Client missing map filter acceptance receipt'
        }
        if ($MapFilter) {
            $backups = @(Get-ChildItem -LiteralPath $discoveries -Filter 'findings-before-rename-*.json' -File)
            if (!$renameRequested -or $backups.Count -ne 1) { throw 'Rename did not create exactly one backup' }
            $before = Get-Content -LiteralPath ('\\?\' + $backups[0].FullName) -Raw | ConvertFrom-Json
            $after = Get-Content -LiteralPath ('\\?\' + (Join-Path $discoveries 'findings.json')) -Raw | ConvertFrom-Json
            if ($before.Count -ne 1 -or $after.Count -ne 1 -or $before[0].name -ne 'LoginProbe-100' -or
                    $before[0].name -eq $after[0].name) { throw 'Rename did not persist a new discovery name' }
            $after[0].name = $before[0].name
            if (($before | ConvertTo-Json -Depth 10 -Compress) -ne ($after | ConvertTo-Json -Depth 10 -Compress)) {
                throw 'Rename changed discovery geometry or metadata'
            }
            if (!(Select-String (Join-Path $root 'client\player-action-receipt.txt') -Pattern '^PASS map fullscreen:' -Quiet)) {
                throw 'Client missing actual fullscreen label acceptance receipt'
            }
            Write-Output 'VERIFIED rename command: original backup, changed name, preserved geometry, persisted findings, live client label refresh'
        }
        Write-Output 'VERIFIED separate peers exit0; gem item/block rendering and framebuffer screenshot'
    } elseif ($SpeechBubbles) {
        Write-Output 'VERIFIED separate peers exit0; speech packet/render lifecycle and screenshots'
    } else {
        Write-Output 'VERIFIED separate peers exit0; authoritative non-op survival movement/break/place'
    }
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
