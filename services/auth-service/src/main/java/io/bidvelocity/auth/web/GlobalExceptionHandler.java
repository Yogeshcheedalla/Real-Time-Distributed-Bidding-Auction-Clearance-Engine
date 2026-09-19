package io.bidvelocity.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Every service answers errors in the SAME shape (PS025 §36/§37). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e, HttpServletRequest req) {
        return ResponseEntity.status(e.status()).body(body(e.status().value(), e.code(), e.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(body(400, "VALIDATION_ERROR", msg.isEmpty() ? "Invalid request" : msg, req));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest req) {
        // never leak stack traces or internals to clients
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "INTERNAL_ERROR", "An unexpected error occurred", req));
    }

    private Map<String, Object> body(int status, String code, String message, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status);
        m.put("error", code);
        m.put("message", message);
        m.put("path", req.getRequestURI());
        m.put("correlationId", req.getHeader("X-Correlation-Id") == null ? "none" : req.getHeader("X-Correlation-Id"));
        return m;
    }
}
