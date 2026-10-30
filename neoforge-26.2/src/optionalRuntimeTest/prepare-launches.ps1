param(
    [ValidateRange(1024, 65535)][int] $Port = 25580
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$build = Join-Path $project 'build'
$utf8 = New-Object Text.UTF8Encoding($false)

function ExistingPath([string] $Path) {
    if (-not [IO.Path]::IsPathRooted($Path) -or -not (Test-Path -LiteralPath $Path)) {
        throw "Missing/nonabsolute launch input: $Path"
    }
    [IO.Path]::GetFullPath($Path)
}
function AssertNoApiDirectory([string] $Path) {
    foreach ($package in 'mezz\jei', 'journeymap\api', 'info\journeymap') {
        if (Test-Path -LiteralPath (Join-Path $Path $package)) { throw "Optional API directory: $Path\$package" }
    }
}
function QuoteArgument([string] $Value) {
    if ($Value -match "[`r`n`"]") { throw "Unsupported argument: $Value" }
    '"' + $Value.Replace('\', '\\') + '"'
}
function ReadArguments([string] $Path) {
    foreach ($line in Get-Content -LiteralPath $Path) {
        $token = $line.Trim()
        if (-not $token -or $token.StartsWith('#')) { continue }
        if ($token.StartsWith('"') -and $token.EndsWith('"')) {
            $token = $token.Substring(1, $token.Length - 2)
        } elseif ($token -match '\s') {
            throw "Expected pinned one-token-per-line ModDev format: $Path"
        }
        $token = $token.Replace('\\', '\')
        if ($token.Contains('"') -or $token.StartsWith('@')) { throw "Unsupported nested argument: $token" }
        $token
    }
}
function ExpandArguments($Arguments, [string] $ExpectedFile) {
    $seen = $false
    foreach ($token in $Arguments) {
        if ($token.StartsWith('@')) {
            $file = ExistingPath ($token.Substring(1).Replace('\\', '\'))
            if (-not $file.StartsWith("$build\moddev\", [StringComparison]::OrdinalIgnoreCase)) {
                throw "Argument reference outside current ModDev root: $file"
            }
            if ($file -ne $ExpectedFile) {
                throw "Unexpected argument file: $file (expected $ExpectedFile)"
            }
            if ($seen) {
                if ($file.EndsWith('RunVmArgs.txt', [StringComparison]::OrdinalIgnoreCase)) { continue }
                throw "Repeated program argument file: $file"
            }
            $seen = $true
            $script:argumentInputs += [pscustomobject]@{
                path = $file; hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
            }
            ReadArguments $file
        } else { $token }
    }
}
function AssertNoApi($Archive, [int] $Depth = 0) {
    if ($Depth -gt 6) { throw 'Unexpected nested-jar depth' }
    foreach ($entry in $Archive.Entries) {
        if ($entry.FullName -match '(^|/)(mezz/jei/|journeymap/api/|info/journeymap/)' -or
                $entry.FullName -match '(^|/)(jei|journeymap)[^/]*\.jar$') {
            throw "Optional API still reachable: $($entry.FullName)"
        }
        if ($entry.FullName.EndsWith('.jar')) {
            $stream = $entry.Open()
            $copy = New-Object IO.MemoryStream
            try {
                $stream.CopyTo($copy)
                $copy.Position = 0
                $nested = New-Object IO.Compression.ZipArchive($copy, [IO.Compression.ZipArchiveMode]::Read, $true)
                try { AssertNoApi $nested ($Depth + 1) } finally { $nested.Dispose() }
            } finally { $stream.Dispose(); $copy.Dispose() }
        }
    }
}

# Require a fresh export after the parent's compile/preparation, not last run's args.
$sources = @(Get-ChildItem (Join-Path $project 'src') -Recurse -File |
    Where-Object { $_.Extension -in '.java', '.json', '.toml' })
$sources += Get-Item (Join-Path $project 'build.gradle')
$commonSources = [IO.Path]::GetFullPath((Join-Path $project '..\common\src'))
if (Test-Path $commonSources) {
    $sources += Get-ChildItem $commonSources -Recurse -File |
        Where-Object { $_.Extension -in '.java', '.json', '.toml' }
}
$newestSource = ($sources | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
$root = Join-Path $build ("optional-runtime-" + [Guid]::NewGuid().ToString('N') + '\build\network-acceptance')
$plans = @()
foreach ($side in 'Server', 'Client') {
    $metadataPath = Join-Path $build "acceptance-launches\NetworkAcceptance$side.json"
    $metadata = Get-Item (ExistingPath $metadataPath)
    if ($metadata.LastWriteTimeUtc -lt $newestSource) { throw "Re-export after current sources: $metadataPath" }
    $launch = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $environment = $launch.PSObject.Properties['environment']
    if ($launch.schemaVersion -ne 2 -or $launch.exportedOnly -ne $true -or
            $launch.task -ne ":runNetworkAcceptance$side" -or
            $launch.mainClass -ne 'net.neoforged.devlaunch.Main' -or
            ($null -ne $environment -and $null -ne $environment.Value)) { throw "Unexpected export contract: $metadataPath" }
    if ([DateTime]::Parse($launch.exportedAt).ToUniversalTime() -lt $newestSource) {
        throw "ExportedAt predates current sources: $metadataPath"
    }
    $java = ExistingPath $launch.executable
    if ([IO.Path]::GetFileName($java) -ne 'java.exe') { throw 'Expected exported java.exe' }
    $oldDirectory = Join-Path $build ('network-acceptance\' + $side.ToLowerInvariant())
    if ($launch.directory -ne $oldDirectory -or $launch.gameDirectory -ne $oldDirectory) {
        throw 'Stale gameDirectory/directory: regenerate exports from this checkout'
    }
    $script:argumentInputs = @()
    $vm = @(ExpandArguments $launch.vmArgs (Join-Path $build "moddev\networkAcceptance${side}RunVmArgs.txt"))
    $program = @(ExpandArguments $launch.programArgs (Join-Path $build "moddev\networkAcceptance${side}RunProgramArgs.txt"))
    if ($argumentInputs.Count -ne 2) { throw 'Expected exactly the two pinned ModDev argument files' }
    if (@($vm | Where-Object { $_ -eq '-Dfarmupcraft.networkAcceptance=true' }).Count -ne 1) {
        throw 'Missing existing network acceptance hooks'
    }
    $folderArgs = @($vm | Where-Object { $_.StartsWith('-Dfml.modFolders=') })
    if ($folderArgs.Count -ne 1) { throw 'Expected one explicit modFolders property' }
    $folders = @($folderArgs[0].Substring('-Dfml.modFolders='.Length).Split(';'))
    foreach ($folder in $folders) {
        if (-not $folder.StartsWith('farmupcraft%%')) { throw "Unexpected mod folder: $folder" }
        $path = ExistingPath $folder.Substring('farmupcraft%%'.Length)
        if (-not $path.StartsWith("$build\", [StringComparison]::OrdinalIgnoreCase)) { throw "Stale mod source root: $path" }
        if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Not a mod output directory: $path" }
        AssertNoApiDirectory $path
        $sourceRoot = $null
        if ($path -match ('^' + [regex]::Escape("$build\") + 'classes\\java\\([^\\]+)$')) {
            $sourceRoot = Join-Path $project "src\$($Matches[1])\java"
        } elseif ($path -eq "$build\common\classes\java\main") {
            $sourceRoot = Join-Path $commonSources 'main\java'
        }
        if ($null -ne $sourceRoot -and (Test-Path -LiteralPath $sourceRoot)) {
            foreach ($sourceFile in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File) {
                $relative = $sourceFile.FullName.Substring($sourceRoot.Length).TrimStart('\') -replace '\.java$', '.class'
                $output = Get-Item -LiteralPath (ExistingPath (Join-Path $path $relative))
                if ($output.LastWriteTimeUtc -lt $sourceFile.LastWriteTimeUtc) {
                    throw "Compile current mod sources before export: $($sourceFile.FullName)"
                }
            }
        }
    }
    foreach ($required in 'classes\java\main', 'common\classes\java\main', 'classes\java\networkTest') {
        if ($folders -notcontains "farmupcraft%%$build\$required") { throw "Missing mod output: $required" }
    }
    foreach ($class in 'classes\java\main\com\falazar\farmupcraft\neoforge\FarmUpCraftNeoForge.class',
            'classes\java\networkTest\com\falazar\farmupcraft\networktest\NetworkAcceptance.class',
            'classes\java\networkTest\com\falazar\farmupcraft\networktest\NetworkAcceptanceClient.class') {
        $compiled = Get-Item (ExistingPath (Join-Path $build $class))
        $relativeSource = $class -replace '^classes\\java\\([^\\]+)\\', 'src\$1\java\' -replace '\.class$', '.java'
        $source = Get-Item (Join-Path $project $relativeSource)
        if ($compiled.LastWriteTimeUtc -lt $source.LastWriteTimeUtc) { throw "Compile current sources before export: $class" }
    }
    $removed = @()
    $classpath = @()
    $contentIds = @()
    foreach ($entry in $launch.classpath) {
        $path = ExistingPath $entry
        if ($path -match '\\maven\.modrinth\\(jei|journeymap)\\|\\info\.journeymap[^\\]*\\|\\(jei|journeymap)[^\\]*\.jar$') {
            $removed += $path
            continue
        }
        if ($path -notlike "$project\*" -and $path -notmatch '\\\.gradle\\') {
            throw "Classpath outside checkout/Gradle cache: $path"
        }
        if ($path -like "$project\*" -and $path -notlike "$build\*") { throw "Stale project classpath: $path" }
        if ($path.StartsWith("$build\common\libs\", [StringComparison]::OrdinalIgnoreCase)) {
            $commonNewest = ($sources | Where-Object { $_.FullName.StartsWith("$commonSources\", [StringComparison]::OrdinalIgnoreCase) } |
                Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
            if ((Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $commonNewest) {
                throw "Rebuild common jar before export: $path"
            }
        }
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            if (-not $path.EndsWith('.jar')) { throw "Unexpected classpath file: $path" }
            $zip = [IO.Compression.ZipFile]::OpenRead($path)
            try {
                AssertNoApi $zip
                $descriptor = $zip.GetEntry('META-INF/neoforge.mods.toml')
                if ($null -ne $descriptor) {
                    $reader = New-Object IO.StreamReader($descriptor.Open())
                    try {
                        $toml = $reader.ReadToEnd()
                        foreach ($match in [regex]::Matches($toml, '(?m)^\s*modId\s*=\s*[''"]([a-z0-9_]+)[''"]')) {
                            $contentIds += $match.Groups[1].Value
                        }
                    } finally { $reader.Dispose() }
                }
            } finally { $zip.Dispose() }
        } else {
            AssertNoApiDirectory $path
        }
        $classpath += $path
    }
    if (@($removed | Where-Object { $_ -match '\\jei\\' }).Count -ne 1 -or
            @($removed | Where-Object { $_ -match '\\journeymap\\' }).Count -ne 1) {
        throw 'Expected pinned top-level JEI and JourneyMap artifacts in export'
    }
    if ($launch.classpathArgument -ne ($launch.classpath -join ';')) { throw 'Inconsistent exported classpath' }
    foreach ($required in 'biomesoplenty', 'terrablender', 'glitchcore', 'pamhc2crops', 'pamhc2trees', 'pamhc2foodcore', 'pamhc2foodextended') {
        if ($contentIds -notcontains $required) { throw "Required content missing: $required" }
    }
    $rootArgs = @($vm | Where-Object { $_.StartsWith('-Dfarmupcraft.networkAcceptanceRoot=') })
    if ($rootArgs.Count -ne 1 -or $rootArgs[0] -ne "-Dfarmupcraft.networkAcceptanceRoot=$build\network-acceptance") {
        throw 'Unexpected network hook root'
    }
    $vm = @($vm | ForEach-Object {
        if ($_.StartsWith('-Dfarmupcraft.networkAcceptanceRoot=')) { "-Dfarmupcraft.networkAcceptanceRoot=$root" }
        else { $_ }
    })
    foreach ($token in $vm) {
        if ($token -match '^-Dlog4j2\.configurationFile=(.*)$') { $null = ExistingPath $Matches[1] }
        if ($token -match '^-D(java\.library\.path|org\.lwjgl\.librarypath)=(.*)$') { $null = ExistingPath $Matches[2] }
        if ($token -match '^(-cp|-classpath|--class-path)(=|$)' -or
                $token -match '^-D(fml\.mods|legacyClassPath|user\.dir)=') {
            throw "Unexpected alternate mod/classpath input: $token"
        }
    }
    if ($program[0] -ne "net.neoforged.fml.startup.$side") { throw 'Unexpected FML entry point' }
    if ($side -eq 'Client') {
        $quick = @(); for ($i = 0; $i -lt $program.Count; $i++) { if ($program[$i] -eq '--quickPlayMultiplayer') { $quick += $i } }
        if ($quick.Count -ne 1 -or $quick[0] + 1 -ge $program.Count -or
                $program[$quick[0] + 1] -ne '127.0.0.1:25576') { throw 'Unexpected QuickPlay export' }
        $program[$quick[0] + 1] = "127.0.0.1:$Port"
        $assets = [Array]::IndexOf($program, '--assetsDir')
        if ($assets -lt 0 -or $assets + 1 -ge $program.Count) { throw 'Missing normal client assets' }
        $null = ExistingPath $program[$assets + 1]
    } elseif ($program -notcontains '--nogui') { throw 'Dedicated server requires --nogui' }
    if ($program -contains '--gameDir') { throw 'Unexpected gameDir override; working directory is authoritative' }
    $plans += [pscustomobject]@{
        side = $side.ToLowerInvariant(); executable = $java; removed = $removed
        classpath = $classpath; vm = $vm; program = $program
        metadata = $metadataPath; metadataHash = (Get-FileHash $metadataPath -Algorithm SHA256).Hash
        argumentInputs = @($argumentInputs)
    }
}

# Nothing is written until both launch inputs pass all checks.
$null = New-Item -ItemType Directory -Path $root
foreach ($plan in $plans) {
    $directory = Join-Path $root $plan.side
    $null = New-Item -ItemType Directory -Path $directory
    $argsPath = Join-Path $root ($plan.side + '.args')
    $arguments = @($plan.vm) + @('-cp', ($plan.classpath -join ';'), 'net.neoforged.devlaunch.Main') + @($plan.program)
    [IO.File]::WriteAllLines($argsPath, [string[]]@($arguments | ForEach-Object { QuoteArgument $_ }), $utf8)
    $plan | Add-Member argsPath $argsPath
    $plan | Add-Member argsHash (Get-FileHash $argsPath -Algorithm SHA256).Hash
}
$manifest = [pscustomobject]@{
    schema = 1; root = $root; port = $Port; preparedUtc = [DateTime]::UtcNow.ToString('o'); launches = $plans
}
[IO.File]::WriteAllText((Join-Path $root 'prepared.json'), ($manifest | ConvertTo-Json -Depth 8), $utf8)
$root
