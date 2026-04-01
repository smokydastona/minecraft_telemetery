param(
    [string]$MinecraftVersion = "1.20.1",
    [switch]$PruneExtraKeys = $true,
    [switch]$OverwriteEnglishFallbacks = $false
)

$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    $here = Get-Location
    return $here.Path
}

function Get-MinecraftClientJarUrl([string]$version) {
    $manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    $manifest = Invoke-RestMethod -Uri $manifestUrl
    $ver = $manifest.versions | Where-Object { $_.id -eq $version } | Select-Object -First 1
    if (-not $ver) {
        throw "Minecraft version '$version' not found in manifest."
    }

    $verJson = Invoke-RestMethod -Uri $ver.url
    $clientUrl = $verJson.downloads.client.url
    if (-not $clientUrl) {
        throw "No client download URL found for '$version'."
    }

    return $clientUrl
}

function Get-MinecraftLocaleCodesFromAssetIndex([string]$version) {
    $manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    $manifest = Invoke-RestMethod -Uri $manifestUrl
    $ver = $manifest.versions | Where-Object { $_.id -eq $version } | Select-Object -First 1
    if (-not $ver) {
        throw "Minecraft version '$version' not found in manifest."
    }

    $verJson = Invoke-RestMethod -Uri $ver.url
    $assetIndexUrl = $verJson.assetIndex.url
    if (-not $assetIndexUrl) {
        throw "No assetIndex.url found for '$version'."
    }

    $assetIndex = Invoke-RestMethod -Uri $assetIndexUrl
    if (-not $assetIndex.objects) {
        throw "Asset index for '$version' did not contain an 'objects' map."
    }

    $codes = @()
    foreach ($p in $assetIndex.objects.PSObject.Properties) {
        $name = $p.Name
        if ($name -like 'minecraft/lang/*.json') {
            $codes += [System.IO.Path]::GetFileNameWithoutExtension($name)
        }
    }

    return ($codes | Sort-Object -Unique)
}

function Read-JsonObject([string]$path) {
    $raw = Get-Content -Raw -Path $path
    return ConvertFrom-Json -InputObject $raw
}

function To-Hashtable($psObj) {
    $h = @{}
    if ($null -eq $psObj) { return $h }

    foreach ($p in $psObj.PSObject.Properties) {
        $h[$p.Name] = $p.Value
    }
    return $h
}

function Write-OrderedJson([string]$path, $orderedMap) {
    $json = $orderedMap | ConvertTo-Json -Depth 20
    # Ensure trailing newline for clean diffs
    if (-not $json.EndsWith("`n")) { $json += "`n" }
    Set-Content -Path $path -Value $json -Encoding UTF8
}

$repoRoot = Get-RepoRoot
$langDir = Join-Path $repoRoot 'src/main/resources/assets/bassshakertelemetry/lang'
$enPath = Join-Path $langDir 'en_us.json'

if (-not (Test-Path $enPath)) {
    throw "Missing $enPath (source of truth)."
}

$enObj = Read-JsonObject -path $enPath
$enOrder = @($enObj.PSObject.Properties.Name)
$enMap = To-Hashtable $enObj

$localeCodes = Get-MinecraftLocaleCodesFromAssetIndex -version $MinecraftVersion
if (-not $localeCodes -or $localeCodes.Count -eq 0) {
    throw "Failed to detect any locale codes from Minecraft $MinecraftVersion asset index."
}

$created = 0
$updated = 0

foreach ($code in $localeCodes) {
    $destPath = Join-Path $langDir ("{0}.json" -f $code)

    if (-not (Test-Path $destPath)) {
        # Seed new locale files with a full English template.
        Copy-Item -Path $enPath -Destination $destPath
        $created++
        continue
    }

    if ($code -eq 'en_us') {
        continue
    }

    $destObj = Read-JsonObject -path $destPath
    $destMap = To-Hashtable $destObj

    $out = [ordered]@{}

    foreach ($k in $enOrder) {
        if ($destMap.ContainsKey($k)) {
            $existing = $destMap[$k]
            if ($OverwriteEnglishFallbacks -and $existing -eq $enMap[$k]) {
                $out[$k] = $enMap[$k]
            }
            else {
                $out[$k] = $existing
            }
        }
        else {
            $out[$k] = $enMap[$k]
        }
    }

    if (-not $PruneExtraKeys) {
        foreach ($p in $destObj.PSObject.Properties) {
            if (-not $out.Contains($p.Name)) {
                $out[$p.Name] = $p.Value
            }
        }
    }

    # Only write if something actually changed
    $missingKey = $false
    foreach ($k in $enOrder) {
        if (-not $destMap.ContainsKey($k)) {
            $missingKey = $true
            break
        }
    }

    if ($missingKey) {
        Write-OrderedJson -path $destPath -orderedMap $out
        $updated++
    }
}

Write-Host ("Locales detected: {0}" -f $localeCodes.Count)
Write-Host ("Created: {0}" -f $created)
Write-Host ("Updated: {0}" -f $updated)
