
$ErrorActionPreference = "Stop"

$patientUrl = "http://localhost:8082"
$availabilityUrl = "http://localhost:8083"
$appointmentUrl = "http://localhost:8081"

# Create unique test records.
$runId = [guid]::NewGuid().ToString("N").Substring(0,10)

$patient = Invoke-RestMethod -Method Post `
  -Uri "$patientUrl/api/patients" -ContentType "application/json" `
  -Body (@{
    name = "Persistence Test $runId"
    email = "persist.$runId@example.com"
    active = $true
  } | ConvertTo-Json)

$doctor = Invoke-RestMethod -Method Post `
  -Uri "$availabilityUrl/api/v1/doctors" -ContentType "application/json" `
  -Body (@{
    name = "Dr Persistence $runId"
    specialty = "General Medicine"
  } | ConvertTo-Json)

$start = [DateTime]::UtcNow.AddDays(45)
$slot = Invoke-RestMethod -Method Post `
  -Uri "$availabilityUrl/api/v1/slots" -ContentType "application/json" `
  -Body (@{
    doctorId = $doctor.id
    startsAt = $start.ToString("yyyy-MM-ddTHH:mm:ssZ")
    endsAt = $start.AddMinutes(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
    capacity = 5
  } | ConvertTo-Json)

$key = "persist-$runId"
$headers = @{ "Idempotency-Key" = $key }
$body = @{ patientId = $patient.id; slotId = $slot.id } | ConvertTo-Json

# Create the original booking.
$original = Invoke-RestMethod -Method Post `
  -Uri "$appointmentUrl/api/appointments" -Headers $headers `
  -ContentType "application/json" -Body $body

Write-Host "`nOriginal booking:" -ForegroundColor Cyan
$original | ConvertTo-Json -Depth 5

# Restart containers without removing persistent volumes.
docker compose restart
if ($LASTEXITCODE -ne 0) { throw "Docker Compose restart failed" }

# Wait for Appointment Service to become ready.
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
  try {
    $health = Invoke-RestMethod "$appointmentUrl/health" -TimeoutSec 2
    if ($health.status -eq "UP") { $ready = $true; break }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $ready) { throw "Appointment Service did not become healthy in time." }

# Replay the same request after restart.
$replay = Invoke-RestMethod -Method Post `
  -Uri "$appointmentUrl/api/appointments" -Headers $headers `
  -ContentType "application/json" -Body $body

Write-Host "`nBooking replay after restart:" -ForegroundColor Cyan
$replay | ConvertTo-Json -Depth 5

$passed = ($original.id -eq $replay.id) -and
          ($original.reservationId -eq $replay.reservationId)

if ($passed) {
  Write-Host "`n[PASS] Booking and reservation persisted across restart." -ForegroundColor Green
} else {
  Write-Host "`n[FAIL] Booking IDs changed after restart." -ForegroundColor Red
  throw "Persistence verification failed."
}
