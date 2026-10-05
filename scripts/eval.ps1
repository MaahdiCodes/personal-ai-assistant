<#
.SYNOPSIS
    The accuracy check: measures how well Mavick's suggestions match your own labels, on a phone.
.DESCRIPTION
    See eval/README.md and docs/PLAN.md section 8. Three steps:
      1. In Mavick: Settings > Suggestions > Export messages for an accuracy check, saved to Downloads.
      2. .\scripts\eval.ps1 -Fetch copies the newest export to eval\private (never committed) and
         deletes it from the phone. Label it on this PC (eval/README.md).
      3. .\scripts\eval.ps1 runs the check on the phone with the model pushed by
         .\scripts\push-model.ps1 -ForTests, and saves the report in eval\private\reports.
    The report holds numbers only. details.csv names each message by its id and holds the AI's
    titles: keep it private too.
.PARAMETER Phone
    "pixel", "poco" or a serial. Needed only when more than one phone is connected.
.PARAMETER Set
    The labelled file. Default: the newest .csv directly in eval\private.
.PARAMETER Fetch
    Copy the newest export from the phone's Download folder into eval\private, then delete it there.
.EXAMPLE
    .\scripts\eval.ps1 -Fetch -Phone pixel
.EXAMPLE
    .\scripts\eval.ps1 -Phone pixel
.EXAMPLE
    .\scripts\eval.ps1 -Phone pixel -Set eval\sample.csv
#>
param(
    [string] $Phone = '',
    [string] $Set = '',
    [switch] $Fetch
)
. "$PSScriptRoot\_common.ps1"

$privateFolder = Join-Path $RepoRoot 'eval\private'
$target = Select-OnePhone -Name $Phone
$serial = $target.Serial

if ($Fetch) {
    $listing = (Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'ls', '-t', '/sdcard/Download/') -AllowFailure).Output
    $export = @($listing | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^mavick-messages-.*\.csv$' }) | Select-Object -First 1
    if (-not $export) {
        throw "No mavick-messages-*.csv in the Download folder of $($target.DisplayName). Export it in Mavick first (Settings > Suggestions)."
    }
    New-Item -ItemType Directory -Force -Path $privateFolder | Out-Null
    $destination = Join-Path $privateFolder $export
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'pull', "/sdcard/Download/$export", $destination) | Out-Null
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'rm', '-f', "/sdcard/Download/$export") | Out-Null
    Write-Host "Saved to $destination and deleted from the phone." -ForegroundColor Green
    Write-Host 'It holds your messages: it stays in eval\private, which git never commits.'
    Write-Host 'Label it (eval/README.md), save it as "CSV UTF-8", then run .\scripts\eval.ps1 again without -Fetch.'
    return
}

if (-not $Set) {
    $newest = Get-ChildItem -Path $privateFolder -Filter '*.csv' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $newest) { throw 'No labelled file in eval\private. Run with -Fetch first and label the file, or give -Set.' }
    $Set = $newest.FullName
}
if (-not (Test-Path $Set -PathType Leaf)) { throw "No file at $Set" }

$modelCheck = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'ls', $PhoneTestModel) -AllowFailure
if ($modelCheck.ExitCode -ne 0) {
    throw 'No AI model for tests on the phone. Run .\scripts\push-model.ps1 -Model <file> -ForTests first.'
}

Write-Step 'Building Mavick Debug and its test app'
Invoke-Gradle -Tasks @(':app:assembleDebug', ':app:assembleDebugAndroidTest')
$appApk = Join-Path $RepoRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $RepoRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'

Write-Step "Installing them on $($target.DisplayName)"
if ($target.IsXiaomi) { Write-Host "Watch the phone's screen: Xiaomi/Poco may ask you to tap 'Install' twice." }
foreach ($apk in @($appApk, $testApk)) {
    $result = Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'install', '-r', '-t', $apk) -AllowFailure
    if ($result.ExitCode -ne 0 -or ($result.Output -join "`n") -notmatch 'Success') {
        throw "Install failed:`n$($result.Output -join "`n")"
    }
}

$phoneSet = "$PhoneTestFolder/eval.csv"
$phoneReports = "/sdcard/Android/data/$DebugAppPackage/files/eval"
try {
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'mkdir', '-p', $PhoneTestFolder) | Out-Null
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'push', (Resolve-Path $Set).Path, $phoneSet) | Out-Null
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'chmod', '644', $phoneSet) | Out-Null

    Write-Step 'Running the check: seconds per message. Keep the phone unlocked and charging.'
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Adb -s $serial shell am instrument -w -r -e class dev.maahdi.mavick.ai.ExtractionEvalRun `
            -e evalSet $phoneSet -e model $PhoneTestModel "$DebugAppPackage.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 |
            ForEach-Object {
                $line = "$_"
                if ($line -match 'INSTRUMENTATION_STATUS: progress=(.+)') { Write-Host "  $($Matches[1])" }
                elseif ($line -match 'FAILURES!!!|Exception|Error:') { Write-Host $line -ForegroundColor Red }
            }
    } finally {
        $ErrorActionPreference = $previous
    }

    $reportFolder = Join-Path $privateFolder ('reports\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Force -Path $reportFolder | Out-Null
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'pull', "$phoneReports/.", $reportFolder) -AllowFailure | Out-Null
    $report = Join-Path $reportFolder 'report.txt'
    if (-not (Test-Path $report)) { throw 'The check made no report. See the lines above, or run .\scripts\logs.ps1.' }
    Write-Host ''
    Get-Content $report -Encoding UTF8 | ForEach-Object { Write-Host $_ }
    Write-Host ''
    Write-Host "Saved to $reportFolder. details.csv lists each message by id: keep it private." -ForegroundColor Green
} finally {
    # The labelled file holds your messages: never leave it on the phone.
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'rm', '-f', $phoneSet) -AllowFailure | Out-Null
    Invoke-Program -Path $Adb -Arguments @('-s', $serial, 'shell', 'rm', '-rf', $phoneReports) -AllowFailure | Out-Null
}
