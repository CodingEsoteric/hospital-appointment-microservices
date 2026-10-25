package edu.assignment.appointment.controller;

import edu.assignment.appointment.dto.*;
import edu.assignment.appointment.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController @RequestMapping("/api/appointments") @Validated
public class AppointmentController {
    private final BookingService bookings;
    public AppointmentController(BookingService bookings){this.bookings=bookings;}
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@RequestHeader("Idempotency-Key") @NotBlank String key, @Valid @RequestBody BookingRequest request) {
        var result=bookings.book(key,request);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(AppointmentResponse.from(result.appointment()));
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable("id") Long id) {
        return bookings.findById(id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Appointment not found"));
    }
}
