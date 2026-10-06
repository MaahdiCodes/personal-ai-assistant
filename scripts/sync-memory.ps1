<#
.SYNOPSIS
    Copies the assistant's saved notes from the project (docs/agent-memory) to this PC.
.DESCRIPTION
    The AI assistant keeps notes outside the project, in a folder under your user profile. Those
    notes are also kept in docs/agent-memory so they travel with git. Run this after a pull on
    any PC, so the notes match the code. It only copies files; it never deletes any.

    The folder name comes from the project's path, the way the assistant builds it: the drive
    letter in lower case, and ':' and '\' turned into '-'. Check that the folder it prints is
    the one the assistant uses.
.EXAMPLE
    .\scripts\sync-memory.ps1
#>
. "$PSScriptRoot\_common.ps1"

$source = Join-Path $RepoRoot 'docs\agent-memory'
if (-not (Test-Path $source -PathType Container)) { throw "No notes folder at $source" }

$drive = $RepoRoot.Substring(0, 1).ToLowerInvariant()
$slug = ($drive + $RepoRoot.Substring(1)) -replace '[:\\]', '-'
$target = Join-Path $env:USERPROFILE ".claude\projects\$slug\memory"
New-Item -ItemType Directory -Force -Path $target | Out-Null

Get-ChildItem -Path $source -Filter '*.md' -File | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination (Join-Path $target $_.Name) -Force
    Write-Host "Copied $($_.Name)"
}
Write-Host ''
Write-Host "Notes are in $target" -ForegroundColor Green
