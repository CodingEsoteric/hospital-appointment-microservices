package edu.assignment.availability.controller;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestControllerAdvice public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason() == null ? "Request failed" : e.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "Invalid request")); }
    @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,String>> unexpected(Exception e) {
        log.error("Unexpected availability service error", e);
        return ResponseEntity.internalServerError().body(Map.of("error", "An internal error occurred"));
    }
}
