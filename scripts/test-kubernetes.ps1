
# Kubernetes health and end-to-end booking test
$ErrorActionPreference = "Stop"

$Ports = @{
    appointment  = 18081
    patient      = 18082
    availability = 18083
    notification = 18084
}

$ForwardProcesses = @()
$Passed = 0
$Failed = 0
$Results = [System.Collections.Generic.List[string]]::new()
$RunError = $null
$LogDir = Join-Path (Get-Location) "docs\evidence\test-logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$EvidenceDir = Join-Path (Get-Location) "docs\evidence\kubernetes-test"
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$EvidenceFile = Join-Path $EvidenceDir "test-results.txt"

function Assert-Test($Name, $Condition) {
    if ($Condition) {
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:Results.Add("[PASS] $Name")
        $script:Passed++
    } else {
        Write-Host "[FAIL] $Name" -ForegroundColor Red
        $script:Results.Add("[FAIL] $Name")
        $script:Failed++
        throw "Test failed: $Name"
    }
}

function Wait-Health($Url) {
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $Url -TimeoutSec 2
            if ($r.status -eq "UP") { return $r }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "Health check timed out: $Url"
}

try {
    # Start temporary Kubernetes port forwards.
    foreach ($name in $Ports.Keys) {
        $port = $Ports[$name]
        $log = Join-Path $LogDir "$name-port-forward.log"
        $err = Join-Path $LogDir "$name-port-forward-error.log"

        $p = Start-Process kubectl `
            -ArgumentList @("port-forward", "service/$name-service", "${port}:$(@{appointment=8081;patient=8082;availability=8083;notification=8084}[$name])") `
            -PassThru -WindowStyle Hidden `
            -RedirectStandardOutput $log `
            -RedirectStandardError $err

        $ForwardProcesses += $p
    }

    Start-Sleep -Seconds 3

    # Health checks.
    foreach ($name in $Ports.Keys) {
        $health = Wait-Health "http://localhost:$($Ports[$name])/health"
        Assert-Test "$name health endpoint" ($health.status -eq "UP")
    }

    # Create a unique patient for this run.
    $runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
    $patientBody = @{
        name   = "Kubernetes Test $runId"
        email  = "k8s.$runId@example.com"
        active = $true
    } | ConvertTo-Json

    $patient = Invoke-RestMethod `
        -Method Post `
        -Uri "http://localhost:$($Ports.patient)/api/patients" `
        -ContentType "application/json" -Body $patientBody

    $patientId = [int]$patient.id
    Assert-Test "Create test patient" ($patientId -gt 0)

    # Create a doctor.
    $doctorBody = @{
        name = "Dr. Test $runId"
        specialty = "General Medicine"
    } | ConvertTo-Json

    $doctor = Invoke-RestMethod `
        -Method Post `
        -Uri "http://localhost:$($Ports.availability)/api/v1/doctors" `
        -ContentType "application/json" -Body $doctorBody

    Assert-Test "Create test doctor" ($null -ne $doctor)

    # Create a future appointment slot.
    $start = [DateTime]::UtcNow.AddDays(30)
    $end = $start.AddMinutes(30)

    $slotBody = @{
        doctorId = $doctor.id
        startsAt = $start.ToString("yyyy-MM-ddTHH:mm:ssZ")
        endsAt = $end.ToString("yyyy-MM-ddTHH:mm:ssZ")
        capacity = 5
    } | ConvertTo-Json

    $slot = Invoke-RestMethod `
        -Method Post `
        -Uri "http://localhost:$($Ports.availability)/api/v1/slots" `
        -ContentType "application/json" -Body $slotBody

    $slotId = [int]$slot.id
    Assert-Test "Create appointment slot" ($slotId -gt 0)

    # Book once using a unique idempotency key.
    $key = "k8s-test-$runId"
    $bookingBody = @{
        patientId = $patientId
        slotId = $slotId
    } | ConvertTo-Json

    $headers = @{ "Idempotency-Key" = $key }

    $bookingStatus = 0
    $booking1 = $null
    try {
        $bookingResponse = Invoke-WebRequest `
            -Method Post `
            -Uri "http://localhost:$($Ports.appointment)/api/appointments" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $bookingBody `
            -ErrorAction Stop

        $bookingStatus = [int]$bookingResponse.StatusCode
        $booking1 = $bookingResponse.Content | ConvertFrom-Json

        Write-Host "`nBooking response:" -ForegroundColor Cyan
        $booking1 | ConvertTo-Json -Depth 5

    } catch {
        Write-Host "`nBooking request failed." -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)"

        if ($_.ErrorDetails.Message) {
            Write-Host "API response body:"
            Write-Host $_.ErrorDetails.Message
        }
        if ($_.Exception.Response) {
            try { $bookingStatus = [int]$_.Exception.Response.StatusCode } catch {}
        }
    }

    Assert-Test "Booking request returns HTTP 201 Created" ($bookingStatus -eq 201)
    $bookingId = 0L
    if ($null -ne $booking1 -and $null -ne $booking1.id) {
        try { $bookingId = [long]$booking1.id } catch {}
    }
    Assert-Test "Booking response contains a valid appointment ID" ($bookingId -gt 0)
    Assert-Test "Booking notification status is SENT" ($booking1.notificationStatus -eq "SENT")

    # Replay: must return the same appointment.
    $replayStatus = 0
    $booking2 = $null
    try {
        $replayResponse = Invoke-WebRequest `
            -Method Post `
            -Uri "http://localhost:$($Ports.appointment)/api/appointments" `
            -Headers $headers -ContentType "application/json" -Body $bookingBody `
            -ErrorAction Stop
        $replayStatus = [int]$replayResponse.StatusCode
        $booking2 = $replayResponse.Content | ConvertFrom-Json
    } catch {
        if ($_.Exception.Response) {
            try { $replayStatus = [int]$_.Exception.Response.StatusCode } catch {}
        }
    }

    Assert-Test "Idempotent replay returns HTTP 200 OK" ($replayStatus -eq 200)
    $replayId = 0L
    if ($null -ne $booking2 -and $null -ne $booking2.id) {
        try { $replayId = [long]$booking2.id } catch {}
    }

    Assert-Test "Idempotent replay returns same appointment" (
        ($bookingId -gt 0) -and ($replayId -eq $bookingId)
    )

    # Same key, different payload: expect HTTP 409.
    $differentBody = @{
        patientId = $patientId + 100000
        slotId = $slotId
    } | ConvertTo-Json

    $conflictStatus = 0
    try {
        Invoke-RestMethod `
            -Method Post `
            -Uri "http://localhost:$($Ports.appointment)/api/appointments" `
            -Headers $headers -ContentType "application/json" -Body $differentBody
    } catch {
        if ($_.Exception.Response) {
            $conflictStatus = [int]$_.Exception.Response.StatusCode
        }
    }

    Assert-Test "Same key with different payload returns HTTP 409" (
        $conflictStatus -eq 409
    )

    Write-Host "`nBooking response:" -ForegroundColor Cyan
    $booking1 | ConvertTo-Json -Depth 5

} catch {
    Write-Host "`nTest run stopped: $($_.Exception.Message)" -ForegroundColor Red
    $RunError = $_.Exception.Message
    $Results.Add("[ERROR] $RunError")
    if ($Failed -eq 0) { $Failed++ }
} finally {
    foreach ($p in $ForwardProcesses) {
        if ($p -and -not $p.HasExited) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
    }

    $summary = if ($Failed -eq 0 -and $null -eq $RunError) {
        "[SUMMARY] PASS: $Passed passed; 0 failed."
    } else {
        "[SUMMARY] FAIL: $Passed passed; $Failed failed."
    }
    $Results.Add($summary)
    if ($RunError) { $Results.Add("[DETAIL] $RunError") }
    $Results | Set-Content -LiteralPath $EvidenceFile
    Write-Host "`nResults: $Passed passed; $Failed failed." -ForegroundColor Cyan
    Write-Host "Port-forward logs: $LogDir"
    Write-Host "Test evidence: $EvidenceFile"
}
