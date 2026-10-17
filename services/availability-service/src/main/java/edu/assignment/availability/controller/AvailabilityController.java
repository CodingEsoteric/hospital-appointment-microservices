package edu.assignment.availability.controller;

import edu.assignment.availability.model.*;
import edu.assignment.availability.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/v1")
public class AvailabilityController {
    private final DoctorRepository doctors;
    private final SlotRepository slots;
    private final ReservationRepository reservations;
    public AvailabilityController(DoctorRepository doctors, SlotRepository slots, ReservationRepository reservations) {
        this.doctors = doctors; this.slots = slots; this.reservations = reservations;
    }
    public record DoctorRequest(@NotBlank String name, @NotBlank String specialty) {}
    public record DoctorView(Long id, String name, String specialty) {}
    public record SlotRequest(@NotNull Long doctorId, @NotNull Instant startsAt, @NotNull Instant endsAt, @Positive int capacity) {}
    public record SlotView(Long id, Long doctorId, Instant startsAt, Instant endsAt, int capacity, int reservedCount, int remainingCapacity) {}
    public record ReservationView(String reservationId, Long slotId, boolean released) {}

    @PostMapping("/doctors") public ResponseEntity<DoctorView> createDoctor(@Valid @RequestBody DoctorRequest req) {
        Doctor d = doctors.save(new Doctor(req.name().trim(), req.specialty().trim()));
        return ResponseEntity.created(URI.create("/api/v1/doctors/" + d.getId())).body(view(d));
    }
    @GetMapping("/doctors/{id}") public DoctorView doctor(@PathVariable("id") Long id) { return view(findDoctor(id)); }
    @GetMapping("/doctors") public List<DoctorView> doctors() { return doctors.findAll().stream().map(AvailabilityController::view).toList(); }
    @PostMapping("/slots") public ResponseEntity<SlotView> createSlot(@Valid @RequestBody SlotRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) throw new ResponseStatusException(BAD_REQUEST, "endsAt must be after startsAt");
        AppointmentSlot s = slots.save(new AppointmentSlot(findDoctor(req.doctorId()), req.startsAt(), req.endsAt(), req.capacity()));
        return ResponseEntity.created(URI.create("/api/v1/slots/" + s.getId())).body(view(s));
    }
    @GetMapping("/slots") public List<SlotView> slots(@RequestParam(name="doctorId", required=false) Long doctorId) {
        return (doctorId == null ? slots.findAll() : slots.findAllByDoctorId(doctorId)).stream().map(AvailabilityController::view).toList();
    }
    @GetMapping("/slots/{id}") public SlotView slot(@PathVariable("id") Long id) { return view(findSlot(id)); }
    @PostMapping("/slots/{id}/reservations") @Transactional public ResponseEntity<ReservationView> reserve(@PathVariable("id") Long id) {
        if (slots.reserveIfAvailable(id) == 0) {
            if (!slots.existsById(id)) throw new ResponseStatusException(NOT_FOUND, "Slot not found");
            throw new ResponseStatusException(CONFLICT, "Slot capacity exhausted");
        }
        SlotReservation r = reservations.save(new SlotReservation(id));
        return ResponseEntity.status(CREATED).body(new ReservationView(r.getId(), id, false));
    }
    @PostMapping("/reservations/{reservationId}/release") @Transactional public ReservationView release(@PathVariable("reservationId") String reservationId) {
        SlotReservation r = reservations.findById(reservationId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Reservation not found"));
        if (reservations.markReleasedIfActive(reservationId) == 0) return new ReservationView(r.getId(), r.getSlotId(), true);
        if (slots.decrementIfPositive(r.getSlotId()) != 1) throw new ResponseStatusException(CONFLICT, "Reservation cannot be released");
        return new ReservationView(r.getId(), r.getSlotId(), true);
    }
    private Doctor findDoctor(Long id) { return doctors.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Doctor not found")); }
    private AppointmentSlot findSlot(Long id) { return slots.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Slot not found")); }
    private static DoctorView view(Doctor d) { return new DoctorView(d.getId(), d.getName(), d.getSpecialty()); }
    private static SlotView view(AppointmentSlot s) { return new SlotView(s.getId(), s.getDoctor().getId(), s.getStartsAt(), s.getEndsAt(), s.getCapacity(), s.getReservedCount(), s.getCapacity()-s.getReservedCount()); }
}
