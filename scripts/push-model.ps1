<#
.SYNOPSIS
    Copies an AI model (.litertlm) from this PC to a phone, and checks the copy.
.DESCRIPTION
    Mavick has no internet permission, so its AI model arrives as a file (docs/PLAN.md section 5.3).
    Download it on the PC first: gemma3-1b-it-int4.litertlm from
    https://huggingface.co/litert-community/Gemma3-1B-IT (accept the Gemma terms there first).

    By default the model goes to the phone's Download folder. Then, in Mavick:
    Settings > Suggestions > Import model, and pick it. Mavick keeps its own copy and offers to
    delete this one.

    With -ForTests it goes to /data/local/tmp/mavick instead, for the on-phone tests and
    .\scripts\eval.ps1. Only test apps read it there.
.PARAMETER Model
    The .litertlm file on this PC.
.PARAMETER Phone
    "pixel", "poco" or a serial. Needed only when more than one phone is connected.
.PARAMETER ForTests
    Copy it for the on-phone tests and the accuracy check, not for the app.
.EXAMPLE
    .\scripts\push-model.ps1 -Model $HOME\Downloads\gemma3-1b-it-int4.litertlm -Phone pixel
.EXAMPLE
    .\scripts\push-model.ps1 -Model $HOME\Downloads\gemma3-1b-it-int4.litertlm -Phone pixel -ForTests
#>
param(
    [Parameter(Mandatory = $true)] [string] $Model,
    [string] $Phone = '',
    [switch] $ForTests
)
. "$PSScriptRoot\_common.ps1"

if (-not (Test-Path $Model -PathType Leaf)) { throw "No file at $Model" }
$file = Get-Item $Model
if ($file.Extension -ne '.litertlm') {
    Write-Warning "$($file.Name) doesn't end in .litertlm. Mavick refuses a file that isn't a LiteRT-LM model."
}

$target = Select-OnePhone -Name $Phone
$serial = $target.Serial
if ($ForTests) { $phonePath = $PhoneTestModel } else { $phonePath = "/sdcard/Download/$($file.Name)" }

Write-Step ('Checksum of {0} ({1:N0} MB)' -f $file.Name, ($file.Length / 1MB))
$hash = (Get-FileHash -Algorithm SHA256 -Path $file.FullName).Hash.ToLowerInvariant()
Write-Host "SHA-256: $hash"

Write-Step "Copying to $($target.DisplayName): $phonePath (a minute or two for 0.5 GB)"
if ($ForTests) { Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'mkdir', '-p', $PhoneTestFolder) | Out-Null }
Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'push', $file.FullName, $phonePath) | Out-Null
# Test apps run as their own user, so the file must be readable by others.
if ($ForTests) { Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'chmod', '644', $phonePath) | Out-Null }

Write-Step 'Checking the copy on the phone'
$check = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'sha256sum', $phonePath)
$phoneHash = (($check.Output -join ' ').Trim() -split '\s+')[0].ToLowerInvariant()
if ($phoneHash -ne $hash) {
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'rm', '-f', $phonePath) -AllowFailure | Out-Null
    throw "The copy on the phone doesn't match this PC's file, so it was deleted. Run this again."
}
Write-Host 'The copy matches.' -ForegroundColor Green

if ($ForTests) {
    Write-Host 'Ready for .\scripts\test.ps1 -OnPhone and .\scripts\eval.ps1.'
} else {
    Write-Host "Now in Mavick: Settings > Suggestions > Import model, and pick $($file.Name) in Downloads"
    Write-Host '(or open the menu, choose the phone, then the Download folder).'
}
