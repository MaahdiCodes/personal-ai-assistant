<#
.SYNOPSIS
    Creates Mavick's release signing key. Run it yourself, once.
.DESCRIPTION
    Creates mavick-signing.p12, protected by a long random password, and keystore.properties
    (git-ignored), which Gradle uses to sign the release build.

    It never replaces an existing key: a new key would stop you from updating Mavick without
    uninstalling it, and uninstalling deletes its data.
.PARAMETER KeyFolder
    Where to create the key file. Default: a ".mavick" folder in your user profile.
.PARAMETER NoExplorer
    Don't open File Explorer at the new key file.
#>
param(
    [string] $KeyFolder = (Join-Path $env:USERPROFILE '.mavick'),
    [switch] $NoExplorer
)
. "$PSScriptRoot\_common.ps1"

$keyFile = Join-Path $KeyFolder 'mavick-signing.p12'
$propertiesFile = Join-Path $RepoRoot 'keystore.properties'

if (Test-Path $keyFile) {
    throw "A signing key already exists at $keyFile, so nothing was changed. If keystore.properties is missing, recreate it as described in README.md."
}
if (Test-Path $propertiesFile) {
    throw "keystore.properties already exists, so a key was already set up. Nothing was changed."
}

# Letters and digits without look-alikes (0/O, 1/l/I), so a paper copy is easy to read back.
function New-RandomPassword([int] $Length) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $limit = 256 - (256 % $alphabet.Length)   # rejection sampling keeps every character equally likely
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buffer = New-Object byte[] 1
    $password = New-Object System.Text.StringBuilder
    try {
        while ($password.Length -lt $Length) {
            $random.GetBytes($buffer)
            if ($buffer[0] -lt $limit) { [void]$password.Append($alphabet[$buffer[0] % $alphabet.Length]) }
        }
    } finally {
        $random.Dispose()
    }
    return $password.ToString()
}

New-Item -ItemType Directory -Force -Path $KeyFolder | Out-Null
$password = New-RandomPassword -Length 32
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$keyCreated = $false

# keytool reads the password from an environment variable, so it never appears on a command line.
$env:MAVICK_SIGNING_PASSWORD = $password
try {
    Write-Step 'Creating the signing key'
    Invoke-Program -Path $keytool -Arguments @(
        '-genkeypair', '-noprompt',
        '-storetype', 'PKCS12', '-keystore', $keyFile,
        '-alias', 'mavick', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '36500',
        '-dname', 'CN=Mavick',
        '-storepass:env', 'MAVICK_SIGNING_PASSWORD', '-keypass:env', 'MAVICK_SIGNING_PASSWORD'
    ) | Out-Null
    $keyCreated = $true

    Set-Content -Path $propertiesFile -Encoding ascii -Value @(
        '# Created by scripts/new-signing-key.ps1. Never commit or share this file.',
        "storeFile=$($keyFile -replace '\\', '/')",
        "storePassword=$password",
        'keyAlias=mavick',
        "keyPassword=$password"
    )

    Invoke-Program -Path $keytool -Arguments @(
        '-list', '-storetype', 'PKCS12', '-keystore', $keyFile, '-storepass:env', 'MAVICK_SIGNING_PASSWORD'
    ) | Out-Null
} catch {
    if ($keyCreated) {
        Write-Warning "The key was created at $keyFile but a later step failed. Save this password now: $password"
    }
    throw
} finally {
    Remove-Item Env:MAVICK_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Signing key created.' -ForegroundColor Green
Write-Host ''
Write-Host "  Key file : $keyFile"
Write-Host "  Password : $password"
Write-Host ''
Write-Host 'Do these three things now:' -ForegroundColor Yellow
Write-Host '  1. Save the password in a password manager, or write it on paper. Do NOT put it in Google Drive.'
Write-Host '  2. Upload the key file to Google Drive: drive.google.com > New > File upload.'
Write-Host '  3. Copy the key file to a USB drive as a second backup.'
Write-Host ''
Write-Host 'Without both the key file and the password, Mavick can never be updated without losing its data.'

if (-not $NoExplorer) {
    Start-Process explorer.exe -ArgumentList "/select,`"$keyFile`""
}
