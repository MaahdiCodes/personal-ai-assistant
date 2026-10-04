<#
.SYNOPSIS
    Builds Mavick and installs it on your connected phone(s).
.DESCRIPTION
    Installs the optimized release build, signed with your own key. The build fails before
    anything is installed if the app requests an unexpected permission or grows past its size
    budget.
.PARAMETER Phone
    Part of the phone's name ("pixel", "poco") or its serial. Leave out to install on every
    connected phone.
.PARAMETER DebugBuild
    Install the separate "Mavick Debug" app instead: slower, with its own data, for development.
.PARAMETER BuildOnly
    Build and check the APK, but don't install it.
.EXAMPLE
    .\scripts\install.ps1 -Phone pixel
#>
param(
    [string] $Phone = '',
    [switch] $DebugBuild,
    [switch] $BuildOnly
)
. "$PSScriptRoot\_common.ps1"

function Get-InstallFailureAdvice([string] $Output, [string] $Package, [string] $Serial) {
    if ($Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        return ("The Mavick already on this phone was signed with a different key, so Android won't update it. " +
            "Uninstalling it deletes its data. Only if that copy holds nothing you need, run:`n" +
            "  & '$Adb' -s $Serial uninstall $Package")
    }
    if ($Output -match 'INSTALL_FAILED_USER_RESTRICTED') {
        return ("The phone blocked the install. On Xiaomi/Poco: Settings > Additional settings > Developer options > " +
            "turn on 'Install via USB', then run this again and tap 'Install' on the phone.")
    }
    if ($Output -match 'INSTALL_FAILED_INSUFFICIENT_STORAGE') {
        return 'The phone is out of storage. Free some space, then run this again.'
    }
    if ($Output -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
        return 'The phone has a newer Mavick than this build. Update your code (git pull), then run this again.'
    }
    return "Install failed:`n$Output"
}

if ($DebugBuild) {
    $variant = 'debug'
    $package = $DebugAppPackage
} else {
    $variant = 'release'
    $package = $AppPackage
    if (-not (Test-Path (Join-Path $RepoRoot 'keystore.properties'))) {
        throw 'No signing key yet. Run .\scripts\new-signing-key.ps1 once first (docs/PLAN.md section 5.7).'
    }
}

# Check the phones before building, so a missing cable is reported straight away.
$targets = @()
if (-not $BuildOnly) { $targets = @(Select-Phones -Name $Phone) }

Write-Step "Building the $variant app (includes the permission and size checks)"
Invoke-Gradle -Tasks @(":app:assemble$((Get-Culture).TextInfo.ToTitleCase($variant))")

$apk = Join-Path $RepoRoot "app\build\outputs\apk\$variant\app-$variant.apk"
if (-not (Test-Path $apk)) { throw "The build finished but $apk is missing. Is the release build signed (keystore.properties)?" }
Write-Host ('APK: {0} ({1:N1} MB)' -f $apk, ((Get-Item $apk).Length / 1MB))
if ($BuildOnly) { return }

$failures = @()
foreach ($target in $targets) {
    Write-Step "Installing on $($target.DisplayName)"
    if ($target.IsXiaomi) { Write-Host "Watch the phone's screen: Xiaomi/Poco may ask you to tap 'Install'." }
    $result = Invoke-Program -Path $Adb -Arguments @('-s', $target.Serial, 'install', '-r', $apk) -AllowFailure
    $text = $result.Output -join "`n"
    if ($result.ExitCode -ne 0 -or $text -notmatch 'Success') {
        $advice = Get-InstallFailureAdvice -Output $text -Package $package -Serial $target.Serial
        Write-Host $advice -ForegroundColor Red
        $failures += $target.DisplayName
        continue
    }
    Invoke-Program -Path $Adb -Arguments @('-s', $target.Serial, 'shell', 'am', 'start', '-n', "$package/$MainActivityClass") | Out-Null
    Write-Host "Installed and opened on $($target.DisplayName)." -ForegroundColor Green
}

if ($failures.Count -gt 0) { throw "Install failed on: $($failures -join ', ')" }
Write-Host ''
Write-Host 'Done. To see what Mavick uses on the phone, run .\scripts\usage.ps1' -ForegroundColor Green
