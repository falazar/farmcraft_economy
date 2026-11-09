param(
    [string]$ModsDirectory = 'D:\MinecraftInstances\NeoForge-26.2\mods',
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\reports\crop-recipe-counts.txt')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-JsonEntry($Entry) {
    $reader = [IO.StreamReader]::new($Entry.Open())
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
    finally { $reader.Dispose() }
}

$crops = @{}
$tags = @{}
$recipes = @{}
$sources = @()
foreach ($mod in @('pamhc2crops', 'pamhc2trees', 'pamhc2foodcore', 'pamhc2foodextended')) {
    $jars = @(Get-ChildItem -LiteralPath $ModsDirectory -Filter "$mod-*.jar")
    if ($jars.Count -ne 1) { throw "Expected exactly one $mod archive; found $($jars.Count)." }
    $sources += $jars[0].Name
    $zip = [IO.Compression.ZipFile]::OpenRead($jars[0].FullName)
    try {
        if ($mod -in @('pamhc2crops', 'pamhc2trees')) {
            $lang = Read-JsonEntry $zip.GetEntry("assets/$mod/lang/en_us.json")
            foreach ($property in $lang.PSObject.Properties) {
                $stem = $null
                if ($mod -eq 'pamhc2crops' -and $property.Name -match '^item\.pamhc2crops\.pam(.+)crop$') {
                    $stem = $Matches[1]
                }
                if ($stem) {
                    $id = "${mod}:${stem}item"
                    $nameProperty = $lang.PSObject.Properties["item.$mod.${stem}item"]
                    if (-not $nameProperty) { throw "Missing produce name for $id." }
                    $crops[$id] = $nameProperty.Value
                }
            }
        }
        foreach ($entry in $zip.Entries) {
            if ($mod -eq 'pamhc2trees' -and $entry.FullName -match '^data/pamhc2trees/loot_table/blocks/pam[^/]+\.json$') {
                $loot = Read-JsonEntry $entry
                foreach ($pool in $loot.pools) {
                    $mature = @($pool.conditions | Where-Object {
                        $_.condition -eq 'minecraft:block_state_property' -and $_.properties.age -eq '7'
                    })
                    if ($mature.Count -eq 0) { continue }
                    foreach ($produce in $pool.entries) {
                        if ($produce.type -ne 'minecraft:item') { throw "Unsupported mature tree loot in $($entry.FullName)." }
                        $id = $produce.name
                        if ($id.StartsWith('minecraft:')) {
                            $crops[$id] = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase(
                                ($id.Substring(10) -replace '_', ' ')) + ' (vanilla)'
                            continue
                        }
                        $nameProperty = $lang.PSObject.Properties[('item.' + ($id -replace ':', '.'))]
                        if (-not $nameProperty) { throw "Missing harvested produce name for $id." }
                        $crops[$id] = $nameProperty.Value
                    }
                }
            }
            if ($entry.FullName -match '^data/([^/]+)/tags/item/(.+)\.json$') {
                $id = "$($Matches[1]):$($Matches[2])"
                $tag = Read-JsonEntry $entry
                if ($tag.replace -or -not $tags.ContainsKey($id)) { $tags[$id] = @() }
                $tags[$id] += @($tag.values)
            } elseif ($entry.FullName -match '^data/([^/]+)/recipe/(.+)\.json$') {
                $id = "$($Matches[1]):$($Matches[2])"
                if ($recipes.ContainsKey($id)) { throw "Duplicate recipe ID: $id." }
                $recipes[$id] = Read-JsonEntry $entry
            }
        }
    } finally { $zip.Dispose() }
}

# Include vanilla produce referenced by HarvestCraft's crop-specific tags.
foreach ($id in @('minecraft:apple', 'minecraft:beetroot', 'minecraft:carrot',
    'minecraft:chorus_fruit', 'minecraft:cocoa_beans', 'minecraft:glow_berries',
    'minecraft:melon_slice', 'minecraft:potato', 'minecraft:pumpkin',
    'minecraft:sweet_berries', 'minecraft:wheat')) {
    $crops[$id] = (($id -replace '^minecraft:', '' -replace '_', ' ') |
        ForEach-Object { [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase($_) }) + ' (vanilla)'
}

function Resolve-TagItems([string]$Id, [string[]]$Visited = @()) {
    if ($Id -in $Visited) { throw "Cyclic ingredient tag: $Id." }
    if (-not $tags.ContainsKey($Id)) { throw "Missing HarvestCraft tag definition: $Id." }
    foreach ($value in $tags[$Id]) {
        $name = if ($value -is [string]) { $value } else { $value.id }
        if (-not $name) { throw "Unsupported value in tag $Id." }
        if ($name.StartsWith('#')) {
            Resolve-TagItems $name.Substring(1) ($Visited + $Id)
        } else { $name }
    }
}

function Get-ExactCrops($Ingredient) {
    if ($Ingredient -is [array]) {
        # A slot with alternatives does not require any one specific crop.
        if ($Ingredient.Count -eq 1) { Get-ExactCrops $Ingredient[0] }
        return
    }
    if ($Ingredient -isnot [string]) { throw 'Unsupported ingredient representation.' }
    if (-not $Ingredient.StartsWith('#')) {
        if ($crops.ContainsKey($Ingredient)) { $Ingredient }
        return
    }
    $tagId = $Ingredient.Substring(1)
    if ($tagId -notmatch '^c:(crops|fruits)/') { return }
    $items = @(Resolve-TagItems $tagId | Sort-Object -Unique)
    foreach ($item in $items) {
        if ($crops.ContainsKey($item)) { $item }
    }
}

$counts = @{}
foreach ($id in $crops.Keys) { $counts[$id] = 0 }
$matchesByRecipe = @{}
foreach ($id in $recipes.Keys) {
    $recipe = $recipes[$id]
    $ingredients = [Collections.Generic.List[object]]::new()
    switch ($recipe.type) {
        'minecraft:crafting_shapeless' {
            foreach ($slot in $recipe.ingredients) { $ingredients.Add($slot) }
        }
        'minecraft:crafting_shaped' {
            $symbols = ($recipe.pattern -join '').ToCharArray() | Sort-Object -Unique
            foreach ($symbol in $symbols) {
                if ($symbol -ne ' ') { $ingredients.Add($recipe.key.PSObject.Properties["$symbol"].Value) }
            }
        }
        { $_ -in @('minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking') } {
            $ingredients.Add($recipe.ingredient)
        }
        default { throw "Unsupported recipe type $($recipe.type) in $id." }
    }
    $used = @(foreach ($ingredient in $ingredients) { Get-ExactCrops $ingredient }) |
        Sort-Object -Unique
    $matchesByRecipe[$id] = @($used)
    foreach ($crop in $used) { $counts[$crop]++ }
}

$rows = @(
    foreach ($id in $crops.Keys) {
        [pscustomobject]@{ Crop = $crops[$id]; Recipes = $counts[$id]; Item = $id }
    }
) | Sort-Object @{ Expression = 'Recipes'; Descending = $true }, Crop, Item

$lines = @(
    'HARVESTCRAFT EXACT-CROP RECIPE COUNTS'
    "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
    'Sources:'
    $sources | ForEach-Object { "  $_" }
    ''
    "Scope: $($crops.Count) raw crop/tree produce items, including vanilla produce used by HarvestCraft."
    "Scanned: $($recipes.Count) recipe JSON definitions from the four installed HarvestCraft jars."
    'Counts are distinct recipe IDs, not ingredient quantities or output quantities.'
    'Includes seed/sapling crafting and separate smelting/smoking/campfire recipes.'
    'Includes direct raw-item inputs and crop-specific c:crops/* or c:fruits/* tags.'
    'Excludes broad category tags, interchangeable ingredient arrays, and processed derivatives.'
    'Crop-specific tags may accept equivalent same-crop items from other mods.'
    'Tree produce IDs come from mature-harvest loot tables, not potentially stale translations.'
    'Pam apple trees harvest minecraft:apple; maple trees harvest pamhc2trees:maplesyrupitem.'
    'These are bundled definitions, not a live recipe-manager snapshot; pack overrides are not applied.'
    'Sorted by recipe count descending, then crop name and item ID. Zero counts are included.'
    ''
    ('{0,7}  {1,-25}  {2}' -f 'Recipes', 'Crop', 'Item ID')
    ('-' * 100)
    $rows | ForEach-Object { '{0,7}  {1,-25}  {2}' -f $_.Recipes, $_.Crop, $_.Item }
)
if ($rows.Count -ne $crops.Count) { throw 'Report dropped a crop.' }
for ($i = 1; $i -lt $rows.Count; $i++) {
    if ($rows[$i].Recipes -gt $rows[$i - 1].Recipes) { throw 'Report is not sorted descending.' }
}
$directory = Split-Path -Parent $OutputPath
[void][IO.Directory]::CreateDirectory($directory)
$lines | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output "Wrote $($rows.Count) crops from $($recipes.Count) recipes to $OutputPath"
$rows | Select-Object -First 10 | Format-Table -AutoSize
