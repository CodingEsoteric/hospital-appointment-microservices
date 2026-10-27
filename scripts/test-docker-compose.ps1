
# Docker Compose end-to-end evidence test
$ErrorActionPreference = "Stop"

$Base = @{
    patient      = "http://localhost:8082"
    availability = "http://localhost:8083"
    appointment  = "http://localhost:8081"
}

$LogDir = Join-Path (Get-Location) "docs\evidence\docker-test"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Results = [System.Collections.Generic.List[string]]::new()

function Record($name, $passed) {
    $status = if ($passed) { "PASS" } else { "FAIL" }
    $line = "[$status] $name"
    $Results.Add($line)
    Write-Host $line -ForegroundColor $(if ($passed) { "Green" } else { "Red" })
    if (-not $passed) { throw $name }
}

try {
    # Confirm all Compose services are running.
    docker compose ps
    if ($LASTEXITCODE -ne 0) { throw "docker compose ps failed" }

    foreach ($service in $Base.Keys) {
        $h = Invoke-RestMethod "$($Base[$service])/health"
        Record "$service health endpoint" ($h.status -eq "UP")
    }

    $runId = [guid]::NewGuid().ToString("N").Substring(0,10)

    # Create patient.
    $patientBody = @{
        name = "Docker Evidence $runId"
        email = "docker.$runId@example.com"
        active = $true
    } | ConvertTo-Json

    $patient = Invoke-RestMethod -Method Post `
        -Uri "$($Base.patient)/api/patients" `
        -ContentType "application/json" -Body $patientBody

    $patientId = $patient.id
    if (-not $patientId) { $patientId = $patient.patientId }
    Record "Create patient (ID $patientId)" ([bool]$patientId)

    # Create doctor.
    $doctorBody = @{
        name = "Dr. Docker $runId"
        specialty = "General Medicine"
    } | ConvertTo-Json

    $doctor = Invoke-RestMethod -Method Post `
        -Uri "$($Base.availability)/api/v1/doctors" `
        -ContentType "application/json" -Body $doctorBody

    $doctorId = $doctor.id
    Record "Create doctor (ID $doctorId)" ([bool]$doctorId)

    # Create future slot.
    $start = [DateTime]::UtcNow.AddDays(30)
    $slotBody = @{
        doctorId = $doctorId
        startsAt = $start.ToString("yyyy-MM-ddTHH:mm:ssZ")
        endsAt = $start.AddMinutes(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
        capacity = 5
    } | ConvertTo-Json

    $slot = Invoke-RestMethod -Method Post `
        -Uri "$($Base.availability)/api/v1/slots" `
        -ContentType "application/json" -Body $slotBody

    $slotId = $slot.id
    Record "Create slot (ID $slotId)" ([bool]$slotId)

    # Create appointment.
    $key = "docker-evidence-$runId"
    $body = @{ patientId = $patientId; slotId = $slotId } | ConvertTo-Json
    $headers = @{ "Idempotency-Key" = $key }

    $booking = Invoke-RestMethod -Method Post `
        -Uri "$($Base.appointment)/api/appointments" `
        -Headers $headers -ContentType "application/json" -Body $body

    $appointmentId = $booking.id
    Record "Booking created (appointment ID $appointmentId)" ([bool]$appointmentId)
    Record "Notification sent" ($booking.notificationStatus -eq "SENT")

    # Replay same key; expect same appointment ID.
    $replay = Invoke-RestMethod -Method Post `
        -Uri "$($Base.appointment)/api/appointments" `
        -Headers $headers -ContentType "application/json" -Body $body

    Record "Idempotent replay returns same appointment" ($replay.id -eq $booking.id)

    # Same key, different patient ID; expect 409.
    $differentBody = @{ patientId = ([int]$patientId + 100000); slotId = $slotId } | ConvertTo-Json
    $statusCode = 0
    try {
        Invoke-RestMethod -Method Post `
            -Uri "$($Base.appointment)/api/appointments" `
            -Headers $headers -ContentType "application/json" -Body $differentBody
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
    }

    Record "Same key with different payload returns HTTP 409" ($statusCode -eq 409)

    # Save the successful booking response for evidence.
    $booking | ConvertTo-Json -Depth 5 |
        Set-Content (Join-Path $LogDir "successful-booking.json")

} catch {
    Write-Host "`nTest stopped: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host "API response: $($_.ErrorDetails.Message)"
    }
} finally {
    $Results | Set-Content (Join-Path $LogDir "test-results.txt")
    Write-Host "`n=== Docker Compose test results ===" -ForegroundColor Cyan
    $Results | ForEach-Object { Write-Host $_ }
    Write-Host "`nEvidence files saved to: $LogDir"
}
