<#
.SYNOPSIS
    Shows Mavick's live log from a phone (Ctrl+C to stop). Mavick never logs message contents.
.PARAMETER Phone
    "pixel", "poco" or a serial. Needed only when more than one phone is connected.
.PARAMETER DebugBuild
    Show the "Mavick Debug" app's log instead.
#>
param(
    [string] $Phone = '',
    [switch] $DebugBuild
)
. "$PSScriptRoot\_common.ps1"

$package = if ($DebugBuild) { $DebugAppPackage } else { $AppPackage }
$target = Select-OnePhone -Name $Phone

$appPid = ((Invoke-Program -Path $Adb -Arguments @('-s', $target.Serial, 'shell', 'pidof', $package) -AllowFailure).Output -join ' ').Trim()
if (-not $appPid) { throw "Mavick isn't running on $($target.DisplayName). Open it, then run this again." }
$appPid = ($appPid -split '\s+')[0]

Write-Host "Mavick's log on $($target.DisplayName). Press Ctrl+C to stop." -ForegroundColor Cyan
& $Adb -s $target.Serial logcat --pid=$appPid
