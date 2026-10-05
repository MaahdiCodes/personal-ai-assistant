<#
.SYNOPSIS
    Runs Mavick's tests.
.DESCRIPTION
    Always runs the PC tests, the permission and read-only checks and Android Lint. With -OnPhone it also runs the on-phone
    tests (real encryption hardware). Those install the temporary "Mavick Debug" app and its test
    app, and Gradle removes both afterwards. Your real Mavick and its data are never touched.
.PARAMETER OnPhone
    Also run the on-phone tests.
.PARAMETER Phone
    With -OnPhone: which phone ("pixel", "poco" or a serial). Leave out to use every connected phone.
.EXAMPLE
    .\scripts\test.ps1 -OnPhone -Phone pixel
#>
param(
    [switch] $OnPhone,
    [string] $Phone = ''
)
. "$PSScriptRoot\_common.ps1"

$targets = @()
if ($OnPhone) { $targets = @(Select-Phones -Name $Phone) }

Write-Step 'Running the PC tests, the permission and read-only checks, and Android Lint'
try {
    Invoke-Gradle -Tasks @(':app:testDebugUnitTest', ':app:checkDebugPermissions', ':app:checkReleasePermissions', ':app:checkReadOnlyNotifications', ':app:lintDebug')
} catch {
    Write-Host "Test report: $RepoRoot\app\build\reports\tests\testDebugUnitTest\index.html" -ForegroundColor Yellow
    Write-Host "Lint report: $RepoRoot\app\build\reports\lint-results-debug.html" -ForegroundColor Yellow
    throw
}

foreach ($target in $targets) {
    Write-Step "Running the on-phone tests on $($target.DisplayName)"
    if ($target.IsXiaomi) {
        Write-Host "Xiaomi/Poco: Developer options > 'USB debugging (Security settings)' must be ON for this test. Turn it off afterwards." -ForegroundColor Yellow
        Write-Host "Watch the phone's screen and tap 'Install' twice: Mavick Debug, then its test app." -ForegroundColor Yellow
    }
    $env:ANDROID_SERIAL = $target.Serial
    try {
        Invoke-Gradle -Tasks @(':app:connectedDebugAndroidTest')
    } catch {
        Write-Host "Test report: $RepoRoot\app\build\reports\androidTests\connected\debug\index.html" -ForegroundColor Yellow
        throw
    } finally {
        Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host 'All tests passed.' -ForegroundColor Green
