param(
    [Parameter(Mandatory = $true)]
    [string] $AcceptanceRoot
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($AcceptanceRoot).TrimEnd('\')
if (-not $root.EndsWith('\build\network-acceptance', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Only disposable build\network-acceptance results are accepted'
}
$server = @(Get-Content -LiteralPath (Join-Path $root 'server\server-receipt.txt'))
$client = @(Get-Content -LiteralPath (Join-Path $root 'client\client-receipt.txt'))
if (@($server + $client | Where-Object { $_ -match '^FAIL ' }).Count -ne 0) {
    throw 'A peer recorded FAIL; inspect both receipts'
}
foreach ($peer in @(@{ Name = 'server'; Lines = $server; Prefix = 'RECEIVED' },
                    @{ Name = 'client'; Lines = $client; Prefix = 'OBSERVED' })) {
    if (@($peer.Lines | Where-Object { $_ -match '^START ' }).Count -ne 1) {
        throw "$($peer.Name): requires exactly one fresh run"
    }
    $receipts = @($peer.Lines | Where-Object { $_ -match "^$($peer.Prefix) stage=" })
    if ($receipts.Count -ne 4) { throw "$($peer.Name): missing/duplicate connected receipt" }
    for ($i = 0; $i -lt 4; $i++) {
        if ($receipts[$i] -notmatch "^$($peer.Prefix) stage=$i ") {
            throw "$($peer.Name): wrong observation ordering"
        }
    }
    if (@($peer.Lines | Where-Object { $_ -match '^PASS ' }).Count -ne 1) {
        throw "$($peer.Name): missing final PASS"
    }
}
$sent = @($server | Where-Object { $_ -match '^SENT oversized-full encoded-bytes=(\d+)$' })
if ($sent.Count -ne 1 -or $sent[0] -notmatch 'encoded-bytes=(\d+)$' -or
        [int]$Matches[1] -le 1048576) {
    throw 'Missing measured oversized production snapshot'
}
$identity = $null
for ($i = 0; $i -lt 4; $i++) {
    $s = @($server | Where-Object { $_ -match "^RECEIVED stage=$i " })[0]
    $c = @($client | Where-Object { $_ -match "^OBSERVED stage=$i " })[0]
    $pattern = 'stage=(\d+) uuid=([0-9a-f-]+) coins=(\d+) digest=(-?\d+)'
    if ($s -notmatch $pattern) { throw 'Malformed server receipt' }
    $serverFields = $Matches[0]
    $uuid = $Matches[2]
    $coins = [int]$Matches[3]
    if ($c -notmatch $pattern -or $Matches[0] -ne $serverFields) {
        throw "Peer receipt mismatch at stage $i"
    }
    if ($null -eq $identity) { $identity = $uuid }
    if ($uuid -ne $identity -or ($i -eq 0 -and $coins -ne 10) -or
            ($i -gt 0 -and $coins -ne 654321)) {
        throw 'Identity/balance mismatch'
    }
}
if ($server[-1] -ne 'PASS login-full entry-update dimension-resync oversized-full' -or
        $client[-1] -ne 'PASS connected-receipts dimension-new-cache logout-cache-cleared') {
    throw 'Missing complete acceptance boundary'
}
'PASS: separate localhost peers acknowledged login, balance entry, dimension cache replacement, >1MiB full snapshot, and logout cache clearing.'
