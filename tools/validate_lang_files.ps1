param(
    [string]$MinecraftVersion = "1.20.1",
    [int]$MaxSuspiciousPercent = 35,
    [int]$MaxSameAsEnglishPercent = 15,
    [switch]$FailOnSuspiciousFallbacks = $true
)

$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    return (Get-Location).Path
}

function Read-JsonObject([string]$path) {
    $raw = Get-Content -Raw -Path $path
    return ConvertFrom-Json -InputObject $raw
}

function To-Hashtable($psObj) {
    $h = @{}
    if ($null -eq $psObj) {
        return $h
    }

    foreach ($p in $psObj.PSObject.Properties) {
        $h[$p.Name] = $p.Value
    }

    return $h
}

function Test-SequenceEqual($left, $right) {
    if ($left.Count -ne $right.Count) {
        return $false
    }

    for ($i = 0; $i -lt $left.Count; $i++) {
        if ($left[$i] -ne $right[$i]) {
            return $false
        }
    }

    return $true
}

function Test-SuspiciousSameValue([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $false
    }

    if ($value -notmatch '[A-Za-z]') {
        return $false
    }

    $allowedExact = @(
        'Bass Shaker Telemetry',
        'Sound Scape',
        'Sound Scape (7.1)',
        'Smart Volume',
        'Auto',
        'Audio',
        'UI',
        'JSON',
        'Modded',
        'Test'
    )

    if ($allowedExact -contains $value) {
        return $false
    }

    if ($value -match '^[A-Z0-9\s._:+\-/%()<>]+$') {
        return $false
    }

    return $true
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

$failures = New-Object System.Collections.Generic.List[string]
$results = New-Object System.Collections.Generic.List[object]

Get-ChildItem $langDir -Filter *.json | Where-Object { $_.Name -ne 'en_us.json' } | Sort-Object Name | ForEach-Object {
    $localePath = $_.FullName
    $localeName = $_.Name
    $localeObj = Read-JsonObject -path $localePath
    $localeOrder = @($localeObj.PSObject.Properties.Name)
    $localeMap = To-Hashtable $localeObj

    $missingKeys = @($enOrder | Where-Object { -not $localeMap.ContainsKey($_) })
    $extraKeys = @($localeOrder | Where-Object { -not $enMap.ContainsKey($_) })
    $orderMatches = Test-SequenceEqual $localeOrder $enOrder

    $sameAsEnglish = 0
    $suspiciousSame = 0

    foreach ($key in $enOrder) {
        if (-not $localeMap.ContainsKey($key)) {
            continue
        }

        $value = [string]$localeMap[$key]
        $enValue = [string]$enMap[$key]
        if ($value -eq $enValue) {
            $sameAsEnglish++
            if (Test-SuspiciousSameValue -value $value) {
                $suspiciousSame++
            }
        }
    }

    $keyCount = [math]::Max($enOrder.Count, 1)
    $samePercent = [math]::Round(($sameAsEnglish / $keyCount) * 100, 1)
    $suspiciousPercent = [math]::Round(($suspiciousSame / $keyCount) * 100, 1)

    $results.Add([pscustomobject]@{
        File = $localeName
        SameAsEnglish = $sameAsEnglish
        SamePercent = $samePercent
        SuspiciousSame = $suspiciousSame
        SuspiciousPercent = $suspiciousPercent
    })

    if ($missingKeys.Count -gt 0) {
        $failures.Add("$localeName is missing $($missingKeys.Count) keys from en_us.json.")
    }

    if ($extraKeys.Count -gt 0) {
        $failures.Add("$localeName has $($extraKeys.Count) extra keys not present in en_us.json.")
    }

    if (-not $orderMatches) {
        $failures.Add("$localeName does not preserve the en_us.json key order.")
    }

    if ($samePercent -ge $MaxSameAsEnglishPercent) {
        $failures.Add("$localeName appears to still be English fallback content ($samePercent% identical to en_us.json, threshold $MaxSameAsEnglishPercent%).")
    }

    if ($FailOnSuspiciousFallbacks -and $suspiciousPercent -ge $MaxSuspiciousPercent) {
        $failures.Add("$localeName appears to contain too much English fallback content ($suspiciousPercent% suspiciously unchanged strings, threshold $MaxSuspiciousPercent%).")
    }
}

Write-Host 'Locale validation summary:'
$results |
    Sort-Object -Property @(
        @{ Expression = 'SuspiciousPercent'; Descending = $true },
        @{ Expression = 'SamePercent'; Descending = $true }
    ) |
    Select-Object -First 30 |
    Format-Table -AutoSize |
    Out-String |
    Write-Host

if ($failures.Count -gt 0) {
    $message = ($failures -join [Environment]::NewLine)
    throw "Locale validation failed:`n$message"
}

Write-Host 'Locale validation passed.'