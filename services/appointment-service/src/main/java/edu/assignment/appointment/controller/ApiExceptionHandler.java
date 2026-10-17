package edu.assignment.appointment.controller;

import edu.assignment.appointment.service.BookingService;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.bind.MissingRequestHeaderException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BookingService.KeyReuseException.class) ResponseEntity<?> keyReuse(){return error(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key was already used with a different payload");}
    @ExceptionHandler(BookingService.IneligiblePatientException.class) ResponseEntity<?> patient(){return error(HttpStatus.UNPROCESSABLE_ENTITY,"PATIENT_INELIGIBLE","Patient is missing or not eligible to book");}
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class, ConstraintViolationException.class}) ResponseEntity<?> invalid(Exception e){return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Request body or Idempotency-Key is invalid");}
    @ExceptionHandler(RestClientResponseException.class) ResponseEntity<?> downstream(RestClientResponseException e){
        HttpStatus status=e.getStatusCode().value()==404?HttpStatus.NOT_FOUND:e.getStatusCode().value()==409?HttpStatus.CONFLICT:HttpStatus.BAD_GATEWAY;
        return error(status,"DOWNSTREAM_REJECTED",status==HttpStatus.CONFLICT?"Requested slot is unavailable": "A downstream service rejected the request");
    }
    @ExceptionHandler(ResourceAccessException.class) ResponseEntity<?> unavailable(){return error(HttpStatus.SERVICE_UNAVAILABLE,"DOWNSTREAM_UNAVAILABLE","A required downstream service is unavailable");}
    @ExceptionHandler(BookingService.PersistenceFailureException.class) ResponseEntity<?> persistence(){return error(HttpStatus.INTERNAL_SERVER_ERROR,"PERSISTENCE_FAILED","Appointment could not be persisted");}
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class}) ResponseEntity<?> internal(){return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Request could not be completed");}
    private static ResponseEntity<Map<String,String>> error(HttpStatus s,String code,String message){return ResponseEntity.status(s).body(Map.of("code",code,"message",message));}
}
