<#
.SYNOPSIS
    Shows what Mavick uses on a phone: storage, memory, CPU, and anything it runs in the background.
.DESCRIPTION
    Use it any time to check that Mavick stays light. Scheduled jobs should be 0. Background
    services should be 0, or 1 once Notification access is on: the message listener, which Android
    keeps connected and wakes only when a notification arrives. Scheduled alarms should equal your
    pending reminders plus one daily alarm. Memory and CPU apply while Mavick runs (the app is open,
    or the listener is connected).
.PARAMETER Phone
    "pixel", "poco" or a serial. Needed only when more than one phone is connected.
.PARAMETER DebugBuild
    Measure the "Mavick Debug" app instead.
.EXAMPLE
    .\scripts\usage.ps1 -Phone poco
#>
param(
    [string] $Phone = '',
    [switch] $DebugBuild
)
. "$PSScriptRoot\_common.ps1"

$package = if ($DebugBuild) { $DebugAppPackage } else { $AppPackage }
$target = Select-OnePhone -Name $Phone
$serial = $target.Serial
$escapedPackage = [regex]::Escape($package)

function Invoke-PhoneShell([string[]] $Command) {
    return (Invoke-Program -Path $Adb -Arguments (@('-s', $serial, 'shell') + $Command) -AllowFailure).Output
}

function Format-Size([double] $Bytes) {
    if ($Bytes -lt 1MB) { return '{0:N0} KB' -f ($Bytes / 1KB) }
    return '{0:N1} MB' -f ($Bytes / 1MB)
}

# Reads one of the "Label: [a,b,c]" lists printed by dumpsys diskstats.
function Get-DiskStatsList([string[]] $Lines, [string] $Label) {
    $line = $Lines | Where-Object { $_.StartsWith($Label) } | Select-Object -First 1
    if (-not $line) { return @() }
    $inner = $line.Substring($Label.Length).Trim().TrimStart('[').TrimEnd(']')
    return @($inner -split ',' | ForEach-Object { $_.Trim().Trim('"') })
}

$report = [ordered]@{}

# --- Storage ---------------------------------------------------------------------------------
$apkPaths = @(Invoke-PhoneShell @('pm', 'path', $package) | Where-Object { $_.StartsWith('package:') } | ForEach-Object { $_.Substring(8).Trim() })
if ($apkPaths.Count -eq 0) { throw "$package is not installed on $($target.DisplayName). Run .\scripts\install.ps1 first." }

$apkBytes = 0.0
foreach ($path in $apkPaths) {
    $size = ((Invoke-PhoneShell @('stat', '-c', '%s', $path)) -join '').Trim()
    if ($size -match '^\d+$') { $apkBytes += [double]$size }
}
$report['App size'] = Format-Size $apkBytes

$diskStats = Invoke-PhoneShell @('dumpsys', 'diskstats')
$packageNames = Get-DiskStatsList $diskStats 'Package Names:'
$index = [array]::IndexOf($packageNames, $package)
$dataSizes = Get-DiskStatsList $diskStats 'App Data Sizes:'
$cacheSizes = Get-DiskStatsList $diskStats 'Cache Sizes:'
if ($index -ge 0 -and $index -lt $dataSizes.Count -and $index -lt $cacheSizes.Count) {
    $report['Data + cache'] = '{0} + {1} (Android refreshes this about once a day)' -f (Format-Size ([double]$dataSizes[$index])), (Format-Size ([double]$cacheSizes[$index]))
} else {
    $report['Data + cache'] = 'not measured by Android yet (it refreshes this about once a day)'
}

# --- Memory and CPU (only while the app is open) --------------------------------------------
$appPid = ((Invoke-PhoneShell @('pidof', $package)) -join ' ').Trim()
if (-not $appPid) {
    $report['Running now'] = 'No, so it uses no memory and no CPU'
} else {
    $appPid = ($appPid -split '\s+')[0]
    $report['Running now'] = "Yes (process $appPid)"

    $memoryKb = $null
    foreach ($line in (Invoke-PhoneShell @('dumpsys', 'meminfo', $package))) {
        if ($line -match 'TOTAL PSS:\s+(\d+)' -or $line -match '^\s*TOTAL\s+(\d+)') {
            $memoryKb = [double]$Matches[1]
            break
        }
    }
    $report['Memory (PSS)'] = if ($null -ne $memoryKb) { Format-Size ($memoryKb * 1KB) } else { 'unknown' }

    # Two one-second samples; the second one is the current CPU use.
    $cpuLines = Invoke-PhoneShell @('top', '-b', '-q', '-n', '2', '-d', '1', '-p', $appPid, '-o', '%CPU')
    $cpu = $cpuLines | Where-Object { $_ -match '^\s*\d+(\.\d+)?\s*$' } | Select-Object -Last 1
    $report['CPU right now'] = if ($cpu) { "$($cpu.Trim())% of one core" } else { 'unknown' }
}

# --- Background activity --------------------------------------------------------------------
$services = @(Invoke-PhoneShell @('dumpsys', 'activity', 'services', $package) | Where-Object { $_ -match "ServiceRecord\{.*\s$escapedPackage/" }).Count
$jobs = @(Invoke-PhoneShell @('dumpsys', 'jobscheduler', $package) | Where-Object { $_ -match 'JOB #' -and $_ -match "\s$escapedPackage/" }).Count
$alarms = @(Invoke-PhoneShell @('dumpsys', 'alarm') | Where-Object { $_ -match 'Alarm\{' -and $_ -match "\s$escapedPackage\}" }).Count
$report['Background services'] = "$services running"
$report['Scheduled jobs'] = "$jobs"
$report['Scheduled alarms'] = "$alarms"

Write-Host ''
Write-Host "Mavick on $($target.DisplayName)" -ForegroundColor Cyan
foreach ($entry in $report.GetEnumerator()) {
    Write-Host ('  {0,-20} {1}' -f $entry.Key, $entry.Value)
}
