package com.splitpay.api;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({java.time.format.DateTimeParseException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<?> malformed() {return ResponseEntity.unprocessableEntity().body(Map.of("message","Check the date, amount and required fields."));}
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class, org.springframework.web.bind.MethodArgumentNotValidException.class})
    ResponseEntity<?> validation(Exception error) { return ResponseEntity.unprocessableEntity().body(Map.of("message", error instanceof org.springframework.web.bind.MethodArgumentNotValidException ? "Check all required fields." : error.getMessage()==null?"Invalid input":error.getMessage())); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<?> forbidden(Exception error) { return ResponseEntity.status(403).body(Map.of("message", "You are not authorized for this operation.")); }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("message", "This item no longer exists.")); }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message", "This record changed or already exists. Refresh and retry.")); }
}
