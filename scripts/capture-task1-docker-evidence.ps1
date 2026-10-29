[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repoRoot

$evidenceDirectory = Join-Path $repoRoot 'docs/evidence/task1-docker'
New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$evidencePath = Join-Path $evidenceDirectory "task1-docker-$timestamp.txt"
$lines = [System.Collections.Generic.List[string]]::new()

function Add-CommandEvidence {
    param([Parameter(Mandatory)][string]$Label, [Parameter(Mandatory)][string[]]$Command)

    $script:lines.Add("===== $Label =====")
    $script:lines.Add("Command: $($Command -join ' ')")
    # Docker Compose may write normal progress messages to stderr. PowerShell 7
    # can promote native stderr to terminating errors when the preference is
    # Stop, before the output and Docker exit code have been recorded.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker @Command 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    foreach ($line in $output) { $script:lines.Add([string]$line) }
    $script:lines.Add("Exit code: $exitCode")
    $script:lines.Add('')
    $script:lines | Set-Content -LiteralPath $script:evidencePath -Encoding utf8

    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode. Evidence: $script:evidencePath"
    }
}

$lines.Add("Task 1 Docker evidence captured: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$lines.Add("Repository: $repoRoot")
$lines.Add('No environment variables or secret values are recorded.')
$lines.Add('')
$lines | Set-Content -LiteralPath $evidencePath -Encoding utf8

try {
    Add-CommandEvidence -Label 'IMAGE BUILD OUTPUT (docker compose build)' -Command @('compose', 'build')
    Add-CommandEvidence -Label 'CONTAINER STARTUP OUTPUT (docker compose up -d)' -Command @('compose', 'up', '-d')
    Add-CommandEvidence -Label 'CONTAINER STATUS (docker compose ps)' -Command @('compose', 'ps')
    Add-CommandEvidence -Label 'DOCKER IMAGE LIST (docker image ls)' -Command @('image', 'ls')
    Write-Host "Docker Task 1 evidence saved to: $evidencePath"
}
catch {
    Write-Error $_
    exit 1
}
