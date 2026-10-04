# Shared setup for the Mavick scripts. Each script loads it with:  . "$PSScriptRoot\_common.ps1"
# Keep this file ASCII-only: Windows PowerShell 5.1 misreads other characters.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$AppPackage = 'dev.maahdi.mavick'
$DebugAppPackage = "$AppPackage.debug"
$MainActivityClass = 'dev.maahdi.mavick.MainActivity'

function Write-Step([string] $Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Find-JavaHome {
    if ($env:MAVICK_JAVA_HOME -and (Test-Path (Join-Path $env:MAVICK_JAVA_HOME 'bin\java.exe'))) {
        return $env:MAVICK_JAVA_HOME
    }
    # Android Studio's bundled JDK, found through Android Studio's uninstall entry.
    $uninstallKeys = @(
        'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    $entries = Get-ItemProperty $uninstallKeys -ErrorAction SilentlyContinue | Where-Object {
        $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq 'Android Studio' -and $_.PSObject.Properties['DisplayIcon']
    }
    foreach ($entry in $entries) {
        $studioExe = ($entry.DisplayIcon -replace ',\d+$', '').Trim('"')
        $jbr = Join-Path (Split-Path (Split-Path $studioExe -Parent) -Parent) 'jbr'
        if (Test-Path (Join-Path $jbr 'bin\java.exe')) { return $jbr }
    }
    foreach ($candidate in @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr")) {
        if (Test-Path (Join-Path $candidate 'bin\java.exe')) { return $candidate }
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    throw 'No JDK found. Install Android Studio, or set MAVICK_JAVA_HOME to a JDK 17+ folder.'
}

function Find-AndroidSdk {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) { return $candidate }
    }
    throw 'Android SDK not found. Open Android Studio once so it installs the SDK, or set ANDROID_HOME.'
}

$env:JAVA_HOME = Find-JavaHome
$env:ANDROID_HOME = Find-AndroidSdk
$Adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

# Runs a program and returns its exit code and output lines. Windows PowerShell turns a program's
# error output into PowerShell errors, so errors are relaxed here and the exit code is checked.
function Invoke-Program {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [string[]] $Arguments = @(),
        [switch] $AllowFailure
    )
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $Path @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $name = Split-Path $Path -Leaf
        throw "'$name $($Arguments -join ' ')' failed (exit code $exitCode):`n$($output -join "`n")"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
}

# Runs Gradle with its output shown live.
function Invoke-Gradle {
    param([Parameter(Mandatory = $true)] [string[]] $Tasks)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') @Tasks
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0) { throw "Gradle failed: $($Tasks -join ' ')" }
}

function Get-PhoneProperty([string] $Serial, [string] $Property) {
    $result = Invoke-Program -Path $Adb -Arguments @('-s', $Serial, 'shell', 'getprop', $Property) -AllowFailure
    if ($result.ExitCode -ne 0) { return '' }
    return ($result.Output -join '').Trim()
}

# Every phone adb can see, ready or not. Callers wrap the result in @() to always get a list.
function Get-Phones {
    $result = Invoke-Program -Path $Adb -Arguments @('devices', '-l')
    $phones = @()
    foreach ($line in $result.Output) {
        if ($line -notmatch '^(\S+)\s+(device|unauthorized|offline|authorizing|no permissions)\b') { continue }
        $serial = $Matches[1]
        $state = $Matches[2]
        $brand = ''
        $name = $serial
        if ($state -eq 'device') {
            $brand = Get-PhoneProperty -Serial $serial -Property 'ro.product.brand'
            # Xiaomi phones report a code as their model; the marketing name is "POCO X7 Pro".
            $name = Get-PhoneProperty -Serial $serial -Property 'ro.product.marketname'
            if (-not $name) { $name = Get-PhoneProperty -Serial $serial -Property 'ro.product.model' }
        }
        $phones += [pscustomobject]@{
            Serial      = $serial
            State       = $state
            Brand       = $brand
            Name        = $name
            DisplayName = "$name [$serial]"
            IsXiaomi    = ($brand -match '^(xiaomi|poco|redmi)$')
        }
    }
    return $phones
}

# Ready phones matching -Name (part of the brand or name, e.g. "pixel" or "poco", or a serial).
# An empty name selects every ready phone. Callers wrap the result in @() to always get a list.
function Select-Phones([string] $Name = '') {
    $phones = @(Get-Phones)
    if ($phones.Count -eq 0) {
        throw 'No phone found. Connect it with a USB cable, turn on USB debugging (docs/PLAN.md section 6), then run .\scripts\devices.ps1.'
    }
    foreach ($phone in @($phones | Where-Object { $_.State -ne 'device' })) {
        Write-Warning "Phone $($phone.Serial) is '$($phone.State)': unlock it and tap 'Allow' on the USB debugging prompt."
    }
    $ready = @($phones | Where-Object { $_.State -eq 'device' })
    if ($Name) {
        $ready = @($ready | Where-Object { "$($_.Brand) $($_.Name)" -like "*$Name*" -or $_.Serial -eq $Name })
        if ($ready.Count -eq 0) {
            $connected = ($phones | ForEach-Object { $_.DisplayName }) -join ', '
            throw "No ready phone matches '$Name'. Connected: $connected"
        }
    }
    if ($ready.Count -eq 0) { throw 'No phone is ready yet (see the warning above).' }
    return $ready
}

function Select-OnePhone([string] $Name = '') {
    $phones = @(Select-Phones -Name $Name)
    if ($phones.Count -gt 1) {
        $names = ($phones | ForEach-Object { $_.DisplayName }) -join ', '
        throw "More than one phone is connected ($names). Add -Phone pixel or -Phone poco."
    }
    return $phones[0]
}
