#Requires -Version 5.1
<#
.SYNOPSIS
    Re-add the UTF-8 BOM to PowerShell scripts that lost it.

.DESCRIPTION
    Windows PowerShell 5.1 reads a .ps1 file as ANSI unless it starts with a
    UTF-8 BOM. Every non-ASCII character (all the Chinese log/error strings in
    this repo) is then decoded wrongly and the parser reports bogus
    "Unexpected token" errors.

    The `edit` tool used by the coding agent rewrites files without the BOM, so
    after editing any .ps1 in this repo, run this script before invoking it.

    It is deliberately ASCII-only itself, so it never needs a BOM.

.PARAMETER Path
    One or more .ps1 files. Defaults to every .ps1 in the repository root.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\add-ps1-bom.ps1
    Fix every root-level script.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\add-ps1-bom.ps1 build-debug.ps1
    Fix one script and verify it parses.
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
if (-not $Path -or $Path.Count -eq 0) {
    $Path = @(Get-ChildItem -Path $root -Filter *.ps1 -File | Select-Object -ExpandProperty FullName)
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Bom   = New-Object System.Text.UTF8Encoding($true)

foreach ($item in $Path) {
    $file = if ([System.IO.Path]::IsPathRooted($item)) { $item } else { Join-Path $root $item }
    if (-not (Test-Path $file)) { Write-Warning "not found: $file"; continue }

    $bytes = [System.IO.File]::ReadAllBytes($file)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF

    if ($hasBom) {
        Write-Host ("  [ok]   {0} (BOM already present)" -f (Split-Path -Leaf $file)) -ForegroundColor DarkGray
    } else {
        $text = [System.IO.File]::ReadAllText($file, $utf8NoBom)
        [System.IO.File]::WriteAllText($file, $text, $utf8Bom)
        Write-Host ("  [fix]  {0} (BOM added)" -f (Split-Path -Leaf $file)) -ForegroundColor Yellow
    }

    $errs = $null
    [System.Management.Automation.Language.Parser]::ParseFile($file, [ref]$null, [ref]$errs) | Out-Null
    if ($errs.Count) {
        Write-Host ("         {0} parse error(s):" -f $errs.Count) -ForegroundColor Red
        $errs | ForEach-Object { Write-Host ("         " + $_.Message) -ForegroundColor Red }
    } else {
        Write-Host '         parse OK' -ForegroundColor Green
    }
}
