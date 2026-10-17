package edu.assignment.appointment.controller;

import edu.assignment.appointment.dto.*;
import edu.assignment.appointment.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/appointments") @Validated
public class AppointmentController {
    private final BookingService bookings;
    public AppointmentController(BookingService bookings){this.bookings=bookings;}
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@RequestHeader("Idempotency-Key") @NotBlank String key, @Valid @RequestBody BookingRequest request) {
        var result=bookings.book(key,request);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(AppointmentResponse.from(result.appointment()));
    }
}
