<#
.SYNOPSIS
    Records the raw notifications of WhatsApp, Messenger, Gmail and Keep, to check Mavick's parsers.
.DESCRIPTION
    Works with the "Mavick Debug" app only (install it with .\scripts\install.ps1 -DebugBuild and
    give it Notification access). Use it only with FAKE test messages sent between your phones:
    the files contain the messages' text.

    -Start switches the recorder on for -Minutes (it switches itself off after that).
    -Stop switches it off, copies the files to the "recordings" folder in this project (never
    committed: see .gitignore), and deletes them from the phone.
.PARAMETER Phone
    "pixel", "poco" or a serial. Needed only when more than one phone is connected.
.PARAMETER Minutes
    With -Start: how long to record. Default 60, at most 1440 (a day).
.EXAMPLE
    .\scripts\record-notifications.ps1 -Phone pixel -Start
.EXAMPLE
    .\scripts\record-notifications.ps1 -Phone pixel -Stop
#>
param(
    [string] $Phone = '',
    [switch] $Start,
    [switch] $Stop,
    [int] $Minutes = 60
)
. "$PSScriptRoot\_common.ps1"

if ($Start -eq $Stop) { throw 'Add -Start or -Stop.' }
if ($Minutes -lt 1 -or $Minutes -gt 1440) { throw '-Minutes must be between 1 and 1440.' }

$target = Select-OnePhone -Name $Phone
$serial = $target.Serial
$package = $DebugAppPackage
$receiver = "$package/dev.maahdi.mavick.capture.RecorderControlReceiver"
$phoneFolder = "/sdcard/Android/data/$package/files/recordings"

$installed = (Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'pm', 'path', $package) -AllowFailure).Output -join ''
if ($installed -notmatch 'package:') {
    throw "Mavick Debug is not installed on $($target.DisplayName). Run .\scripts\install.ps1 -DebugBuild -Phone <name> first."
}

function Send-RecorderSwitch([int] $ForMinutes) {
    $result = Invoke-Program -Path $Adb -Arguments @(
        '-s', $serial, 'shell', 'am', 'broadcast', '--include-stopped-packages',
        '-n', $receiver, '-a', 'dev.maahdi.mavick.debug.RECORD', '--ei', 'minutes', "$ForMinutes"
    )
    if (($result.Output -join "`n") -notmatch 'Broadcast completed') {
        throw "The phone did not accept the recorder switch:`n$($result.Output -join "`n")"
    }
}

if ($Start) {
    Send-RecorderSwitch -ForMinutes $Minutes
    Write-Host "Recording on $($target.DisplayName) for $Minutes minutes." -ForegroundColor Green
    Write-Host 'Mavick Debug needs Notification access. Now send FAKE test messages to this phone, including'
    Write-Host 'some long ones (about 300, 1500 and 5000 characters), to every WhatsApp account, Messenger and Gmail.'
    Write-Host 'Then run this script again with -Stop.'
    return
}

Send-RecorderSwitch -ForMinutes 0
$name = ($target.Name -replace '[^A-Za-z0-9]+', '-').Trim('-')
$destination = Join-Path $RepoRoot ("recordings\{0}-{1}" -f $name, (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$pull = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'pull', "$phoneFolder/.", $destination) -AllowFailure
$count = @(Get-ChildItem -Path $destination -Filter '*.json' -ErrorAction SilentlyContinue).Count
if ($pull.ExitCode -ne 0 -and $count -eq 0) {
    Write-Host 'Recorder switched off. Nothing was recorded (no notifications arrived, or Notification access is off).' -ForegroundColor Yellow
    Remove-Item -Path $destination -Force -Recurse
    return
}
Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'rm', '-rf', $phoneFolder) -AllowFailure | Out-Null
Write-Host "Recorder switched off. $count notifications saved to $destination and deleted from the phone." -ForegroundColor Green
Write-Host 'These files are never committed. Give them to Claude to check the parsers.'
