<#
.SYNOPSIS
    Lists connected phones: Android version, free storage, memory, and which Mavick apps are installed.
.EXAMPLE
    .\scripts\devices.ps1
#>
. "$PSScriptRoot\_common.ps1"

$phones = @(Get-Phones)
if ($phones.Count -eq 0) {
    Write-Host 'No phone found.' -ForegroundColor Yellow
    Write-Host '  1. Connect the phone with a USB cable (a data cable, not charge-only).'
    Write-Host '  2. Turn on USB debugging (docs/PLAN.md section 6).'
    Write-Host "  3. Unlock the phone and tap 'Allow' on the USB debugging prompt."
    exit 1
}

foreach ($phone in $phones) {
    Write-Host ''
    if ($phone.State -ne 'device') {
        Write-Host "$($phone.Serial): $($phone.State)" -ForegroundColor Yellow
        Write-Host "  Unlock the phone and tap 'Allow' on the USB debugging prompt, then run this again."
        continue
    }

    $serial = $phone.Serial
    $android = Get-PhoneProperty -Serial $serial -Property 'ro.build.version.release'

    $freeStorage = 'unknown'
    $df = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'df', '-k', '/data') -AllowFailure
    $dfLine = $df.Output | Where-Object { $_ -match '^\S+\s+\d+\s+\d+\s+\d+' } | Select-Object -Last 1
    if ($dfLine) { $freeStorage = '{0:N1} GB' -f ([double](($dfLine -split '\s+')[3]) / 1MB) }

    $memory = 'unknown'
    $meminfo = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'cat', '/proc/meminfo') -AllowFailure
    $memLine = $meminfo.Output | Where-Object { $_ -match '^MemTotal:\s+(\d+)' } | Select-Object -First 1
    if ($memLine -and $memLine -match '^MemTotal:\s+(\d+)') { $memory = '{0:N1} GB' -f ([double]$Matches[1] / 1MB) }

    $packages = (Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'pm', 'list', 'packages', $AppPackage) -AllowFailure).Output
    $installed = @()
    if ($packages -contains "package:$AppPackage") { $installed += 'Mavick' }
    if ($packages -contains "package:$DebugAppPackage") { $installed += 'Mavick Debug' }
    if ($installed.Count -eq 0) { $installed = @('none') }

    Write-Host $phone.DisplayName -ForegroundColor Green
    Write-Host "  Android        : $android"
    Write-Host "  Free storage   : $freeStorage"
    Write-Host "  Memory (RAM)   : $memory"
    Write-Host "  Mavick apps    : $($installed -join ', ')"
}
