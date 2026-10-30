param([Parameter(Mandatory = $true)][string] $AcceptanceRoot)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$root = [IO.Path]::GetFullPath($AcceptanceRoot).TrimEnd('\')
if ($root -notmatch ('^' + [regex]::Escape("$project\build\optional-runtime-") + '[0-9a-f]{32}\\build\\network-acceptance$')) {
    throw 'Only new isolated optional-runtime roots are accepted'
}
for ($path = $root; $path -ne $project; $path = Split-Path $path -Parent) {
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Redirected preparation root: $path"
    }
}
if (@(Get-ChildItem -LiteralPath $root -Recurse -Force |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) {
    throw 'Runtime evidence must not contain links/junctions'
}
$manifest = Get-Content (Join-Path $root 'prepared.json') -Raw | ConvertFrom-Json
$run = Get-Content (Join-Path $root 'execution.json') -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1 -or $manifest.root -ne $root -or $run.root -ne $root -or
        $run.completed -ne $true -or $run.forcedTermination -ne $false -or
        $run.serverExitCode -ne 0 -or $run.clientExitCode -ne 0 -or
        $run.elapsedSeconds -gt 420 -or $run.elapsedSeconds -le 0 -or
        $run.serverPid -le 0 -or $run.clientPid -le 0 -or $run.serverPid -eq $run.clientPid -or
        @($manifest.launches).Count -ne 2) {
    throw 'Missing clean, bounded, dual-process completion'
}
foreach ($side in 'server', 'client') {
    $plan = @($manifest.launches | Where-Object { $_.side -eq $side })
    if ($plan.Count -ne 1 -or $plan[0].argsPath -ne (Join-Path $root "$side.args") -or
            (Get-FileHash $plan[0].argsPath -Algorithm SHA256).Hash -ne $plan[0].argsHash) {
        throw "Launch arguments changed: $side"
    }
    $log = Join-Path $root "$side\logs\latest.log"
    foreach ($evidence in $log, (Join-Path $root "$side\$side-receipt.txt")) {
        if ((Get-Item -LiteralPath $evidence).LastWriteTimeUtc -lt [DateTime]::Parse($run.startedUtc).ToUniversalTime()) {
            throw "Stale $side evidence: $evidence"
        }
    }
    $lines = @(Get-Content -LiteralPath $log)
    $headers = @(); for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s+Mod List:\s*$') { $headers += $i }
    }
    if ($headers.Count -ne 1 -or $headers[0] -eq 0 -or $headers[0] + 1 -ge $lines.Count -or
            $lines[$headers[0] - 1] -notmatch 'net\.neoforged\.fml\.loading\.moddiscovery\.ModDiscoverer/' -or
            $lines[$headers[0] + 1] -notmatch '^\s+Name Version \(Mod Id\)\s*$') {
        throw "Missing actual FML discovery inventory: $side"
    }
    $ids = @()
    $ended = $false
    for ($i = $headers[0] + 2; $i -lt $lines.Count; $i++) {
        if ($lines[$i].StartsWith('[')) { $ended = $true; break }
        if ([string]::IsNullOrWhiteSpace($lines[$i])) { continue }
        if ($lines[$i] -notmatch '^\s+.+ \(([a-z0-9_]+)\)\s*$') { throw "Malformed FML inventory row: $side" }
        $ids += $Matches[1]
    }
    if (-not $ended -or @($ids | Select-Object -Unique).Count -ne $ids.Count) { throw "Incomplete FML inventory: $side" }
    foreach ($required in 'farmupcraft', 'minecraft', 'neoforge', 'biomesoplenty', 'terrablender', 'glitchcore',
            'pamhc2crops', 'pamhc2trees', 'pamhc2foodcore', 'pamhc2foodextended') {
        if ($ids -notcontains $required) { throw "$side FML inventory missing $required" }
    }
    if (@($ids | Where-Object { $_ -match '^(jei|journeymap|journeymap_api)$' }).Count -ne 0) {
        throw "$side loaded an optional mod/API"
    }
    if (@($lines | Where-Object { $_ -match 'NoClassDefFoundError|ClassNotFoundException|ModLoadingException|Failed to load mods|FATAL' }).Count -ne 0) {
        throw "$side logged a class-loading/bootstrap failure"
    }
    [IO.File]::WriteAllLines((Join-Path $root "$side-discovered-mods.txt"), [string[]]$ids)
}
& (Join-Path $project 'src\networkTest\verify-receipts.ps1') -AcceptanceRoot $root
'PASS: real dedicated/client FML discovery excluded JEI, JourneyMap and JourneyMap API; retained FarmUpCraft/content; connected production payloads and clean bounded shutdown verified.'
