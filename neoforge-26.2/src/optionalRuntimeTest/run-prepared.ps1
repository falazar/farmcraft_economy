param([Parameter(Mandatory = $true)][string] $AcceptanceRoot)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$root = [IO.Path]::GetFullPath($AcceptanceRoot).TrimEnd('\')
if ($root -notmatch ('^' + [regex]::Escape("$project\build\optional-runtime-") + '[0-9a-f]{32}\\build\\network-acceptance$')) {
    throw 'Only prepared optional-runtime roots are allowed'
}
for ($path = $root; $path -ne $project; $path = Split-Path $path -Parent) {
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Redirected preparation root: $path"
    }
}
if (@(Get-ChildItem -LiteralPath $root -Recurse -Force |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) {
    throw 'Disposable inputs must not contain links/junctions'
}
$manifest = Get-Content (Join-Path $root 'prepared.json') -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1 -or $manifest.root -ne $root -or
        $manifest.port -lt 1024 -or $manifest.port -gt 65535 -or @($manifest.launches).Count -ne 2) {
    throw 'Invalid preparation manifest'
}
if (Test-Path (Join-Path $root 'execution.json')) { throw 'Never reuse an attempted root' }
foreach ($side in 'server', 'client') {
    foreach ($old in 'logs', 'server-receipt.txt', 'client-receipt.txt', 'saves', 'world', 'optional-runtime-world', 'mods') {
        if (Test-Path (Join-Path $root "$side\$old")) { throw "Root is not fresh: $side\$old" }
    }
    $plans = @($manifest.launches | Where-Object { $_.side -eq $side })
    if ($plans.Count -ne 1 -or $plans[0].argsPath -ne (Join-Path $root "$side.args") -or
            [IO.Path]::GetFileName($plans[0].executable) -ne 'java.exe' -or
            (Get-FileHash -LiteralPath $plans[0].argsPath -Algorithm SHA256).Hash -ne $plans[0].argsHash) {
        throw "Invalid/changed prepared $side arguments"
    }
    $plan = $plans[0]
    if ((Get-FileHash -LiteralPath $plan.metadata -Algorithm SHA256).Hash -ne $plan.metadataHash) {
        throw "Export changed after preparation: $side"
    }
    foreach ($inputFile in $plan.argumentInputs) {
        if ((Get-FileHash -LiteralPath $inputFile.path -Algorithm SHA256).Hash -ne $inputFile.hash) {
            throw "ModDev input changed after preparation: $($inputFile.path)"
        }
    }
}
$properties = Get-Content (Join-Path $root 'server\server.properties') -Raw
foreach ($setting in @('server-ip=127.0.0.1', "server-port=$($manifest.port)", 'online-mode=false',
        'level-name=optional-runtime-world', 'allow-nether=true', 'level-type=minecraft:flat',
        'generate-structures=false', 'spawn-protection=0', 'gamemode=creative',
        'view-distance=2', 'simulation-distance=2')) {
    $key = $setting.Split('=')[0]
    if (@([regex]::Matches($properties, '(?m)^' + [regex]::Escape($setting) + '\r?$')).Count -ne 1 -or
            @([regex]::Matches($properties, '(?m)^\s*' + [regex]::Escape($key) + '\s*[:= ]')).Count -ne 1) {
        throw "Parent must prepare isolated server property: $setting"
    }
}
if ((Get-Content (Join-Path $root 'client\config\neoforge-client.toml') -Raw) -notmatch '(?m)^showLoadWarnings\s*=\s*false\s*$' -or
        (Get-Content (Join-Path $root 'client\options.txt') -Raw) -notmatch '(?m)^onboardAccessibility:false\r?$') {
    throw 'Parent must prepare disposable-client warning/accessibility prompt settings'
}
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, [int]$manifest.port)
try { $listener.Start() } finally { $listener.Stop() }
$started = [DateTime]::UtcNow
$clock = [Diagnostics.Stopwatch]::StartNew()
$owned = @()
$server = $null; $client = $null
$completed = $false; $forced = $false
$serverCode = $null; $clientCode = $null
function StartPeer([string] $Side) {
    $plans = @($manifest.launches | Where-Object { $_.side -eq $Side })
    if ($plans.Count -ne 1) { throw "Missing unique $Side launch" }
    $plan = $plans[0]
    if ((Get-FileHash $plan.argsPath -Algorithm SHA256).Hash -ne $plan.argsHash) { throw 'Prepared arguments changed' }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $plan.executable
    $info.Arguments = '"@' + $plan.argsPath + '"'
    $info.WorkingDirectory = Join-Path $root $Side
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    foreach ($key in 'CLASSPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MOD_CLASSES', 'MOD_FOLDERS') {
        $info.EnvironmentVariables.Remove($key)
    }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "Could not start $Side" }
    $process
}
function CheckDeadline {
    if ($clock.Elapsed.TotalSeconds -gt 350) { throw 'Absolute observation deadline exceeded' }
}
# Claim the root atomically before entering cleanup, so a failed claim cannot overwrite another attempt.
$marker = [IO.File]::Open((Join-Path $root 'execution.json'), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
$marker.Dispose()
try {
    $server = StartPeer 'server'; $owned += $server
    do {
        CheckDeadline
        if ($server.HasExited) { throw 'Server exited before readiness' }
        Start-Sleep -Seconds 1
        $log = Join-Path $root 'server\logs\latest.log'
        $ready = (Test-Path $log) -and (Select-String -LiteralPath $log -Pattern 'Done \(.+\)! For help, type "help"' -Quiet)
    } until ($ready)
    $client = StartPeer 'client'; $owned += $client
    do {
        CheckDeadline
        if ($server.HasExited -or $client.HasExited) { throw 'Peer exited before connected acceptance' }
        Start-Sleep -Seconds 1
        foreach ($side in 'server', 'client') {
            $receipt = Join-Path $root "$side\$side-receipt.txt"
            if ((Test-Path $receipt) -and (Select-String $receipt -Pattern '^FAIL ' -Quiet)) { throw "$side recorded FAIL" }
        }
        $clientReceipt = Join-Path $root 'client\client-receipt.txt'
        $passed = (Test-Path $clientReceipt) -and (Select-String $clientReceipt -Pattern '^PASS connected-receipts dimension-new-cache logout-cache-cleared$' -Quiet)
    } until ($passed)
    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    if (-not $client.CloseMainWindow()) { throw 'No client window available for normal shutdown' }
    if (-not $client.WaitForExit(30000) -or -not $server.WaitForExit(30000)) { throw 'Normal shutdown exceeded bound' }
    $serverCode = $server.ExitCode; $clientCode = $client.ExitCode
    if ($serverCode -ne 0 -or $clientCode -ne 0) { throw 'Nonzero peer exit' }
    $completed = $true
} finally {
    # Try normal shutdown on failures too; force only exact owned processes as a last resort.
    foreach ($process in $owned) {
        if (-not $process.HasExited) {
            try {
                if ($process -eq $server) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
                else { $null = $process.CloseMainWindow() }
            } catch { }
        }
    }
    foreach ($process in $owned) {
        if (-not $process.HasExited -and -not $process.WaitForExit(5000)) {
            $forced = $true
            try { Stop-Process -Id $process.Id -Force -ErrorAction Stop } catch {
                if (-not $process.HasExited) { Write-Warning "Could not reap owned PID $($process.Id): $_" }
            }
            $null = $process.WaitForExit(5000)
        }
    }
    $result = [pscustomobject]@{
        root = $root; startedUtc = $started.ToString('o'); elapsedSeconds = $clock.Elapsed.TotalSeconds
        completed = $completed; forcedTermination = $forced
        serverPid = $(if ($null -ne $server) { $server.Id } else { $null })
        clientPid = $(if ($null -ne $client) { $client.Id } else { $null })
        serverExitCode = $serverCode; clientExitCode = $clientCode
    }
    [IO.File]::WriteAllText((Join-Path $root 'execution.json'), ($result | ConvertTo-Json))
}
& (Join-Path $PSScriptRoot 'verify-runtime.ps1') -AcceptanceRoot $root
