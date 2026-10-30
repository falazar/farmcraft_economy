Add-Type -AssemblyName System.IO.Compression.FileSystem

$outputFile = "$PSScriptRoot\..\Rimfog\COOKING_GOALS.md"
$gradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"

function Convert-Name([string]$base) {
    $n = $base -replace '_', ' '
    return (Get-Culture).TextInfo.ToTitleCase($n.ToLower())
}

function Get-BaseName([string]$jsonName) {
    return $jsonName `
        -replace '\.json$', '' `
        -replace '_(campfire|forge|smoker)$', '' `
        -replace '_x\d+$', '' `
        -replace 'item$', ''
}

# --- Header ---
Set-Content -Encoding UTF8 $outputFile @"
# Pam's HarvestCraft 2 — Cooking Goals

Cook every recipe in HarvestCraft 2! Check them off as you go.

---
"@

# --- pamhc2foodcore ---
Add-Content -Encoding UTF8 $outputFile "`n## Core Food (pamhc2foodcore)`n"
$jar = Get-ChildItem "$gradleCache\curse.maven\pamhc2foodcore*" -Recurse -Filter "*.jar" | Select-Object -First 1
if ($jar) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $seen = @{}
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like "data/pamhc2foodcore/recipes/*" -and $_.Name -like "*.json" }) {
        $base = Get-BaseName $entry.Name
        if ($base -and -not $seen[$base] -and $base -notlike "tool_*" -and $base -notlike "freshmilk*" -and $base -notlike "freshwater*" -and $base -notlike "bread_*" -and $base -notlike "flour_*" -and $base -notlike "sugar_*" -and $base -notlike "batter*" -and $base -notlike "dough*" -and $base -notlike "stock*" -and $base -notlike "vinegar*" -and $base -notlike "cookingoil*") {
            $seen[$base] = $true
            Add-Content -Encoding UTF8 $outputFile "- [ ] $(Convert-Name $base)"
        }
    }
    $zip.Dispose()
    Write-Host "foodcore done."
}

# --- pamhc2foodextended ---
Add-Content -Encoding UTF8 $outputFile "`n## Extended Food (pamhc2foodextended)`n"
$jar = Get-ChildItem "$gradleCache\curse.maven\pamhc2foodextended*" -Recurse -Filter "*.jar" | Select-Object -First 1
if ($jar) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $seen = @{}
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like "data/pamhc2foodextended/recipes/*" -and $_.Name -like "*.json" }) {
        $base = Get-BaseName $entry.Name
        # skip raw tofu/cooked tof* variants (just keep one), skip pizza slices (those are outputs of other recipes)
        if ($base -and -not $seen[$base] -and $base -notlike "rawtof*" -and $base -notlike "pizzaslice*" -and $base -notlike "charcoal_*" -and $base -notlike "cornmeal*" -and $base -notlike "misopaste*" -and $base -notlike "soymilk*" -and $base -notlike "firmtofu*" -and $base -notlike "silkentof*" -and $base -notlike "vanilla" -and $base -notlike "salt*" -and $base -notlike "pepper*" -and $base -notlike "mustard*" -and $base -notlike "ketchup*" -and $base -notlike "mayo*") {
            $seen[$base] = $true
            Add-Content -Encoding UTF8 $outputFile "- [ ] $(Convert-Name $base)"
        }
    }
    $zip.Dispose()
    Write-Host "foodextended done."
}

# --- pamhc2crops (baked/roasted only from local data) ---
Add-Content -Encoding UTF8 $outputFile "`n## Baked & Roasted Crops (pamhc2crops)`n"
$cropsDir = "$PSScriptRoot\..\data\pamhc2crops\recipes"
$seen = @{}
Get-ChildItem $cropsDir -Filter "*.json" | ForEach-Object {
    $base = Get-BaseName $_.Name
    if ($base -and -not $seen[$base] -and ($base -like "baked*" -or $base -like "roasted*" -or $base -like "hot*")) {
        $seen[$base] = $true
        Add-Content -Encoding UTF8 $outputFile "- [ ] $(Convert-Name $base)"
    }
}
Write-Host "crops done."

Write-Host "`nAll done! Written to: $outputFile"
