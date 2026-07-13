package com.ticketapp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central error handler — all errors surface as { "error": "..." } JSON.
 *
 * Exception hierarchy (most specific first):
 *  1. BusinessException subclasses  → mapped to their own HttpStatus, safe message exposed
 *  2. Bean validation errors         → 400 with field messages
 *  3. DataIntegrityViolationException→ 409 with a generic "duplicate" message
 *                                       (never leaks SQL / constraint names to client)
 *  4. OptimisticLockingFailure       → 409 (stale read — caller should retry)
 *  5. AccessDeniedException          → 403
 *  6. NoResourceFoundException       → 404 (silent — static file probes)
 *  7. RuntimeException               → 400 with exception message ONLY if it is a
 *                                       known BusinessException; otherwise 500.
 *  8. Exception (catch-all)          → 500 with generic message (no internal detail)
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 1. BusinessException hierarchy (safe, curated messages) ──────────────
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    // ── 2. Bean Validation (@Valid) ───────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    // ── 3. DB unique / FK constraint violations ───────────────────────────────
    // Never expose the raw SQL error to the client (it reveals table names,
    // column names, and constraint identifiers).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        // Narrow the message to a human-readable hint based on known constraints.
        String msg = "A duplicate or invalid value was provided.";
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null) {
            if (cause.contains("razorpay_order_id"))
                msg = "This payment order has already been processed.";
            else if (cause.contains("email"))
                msg = "An account with this email already exists.";
            else if (cause.contains("coupon") || cause.contains("uq_review"))
                msg = "You have already used this resource.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
    }

    // ── 4. Optimistic lock (stale Event.availableTickets) ────────────────────
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error",
                        "The resource was updated by another request. Please try again."));
    }

    // ── 5. Spring Security access denied ─────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "You do not have permission to access this resource."));
    }

    // ── 6. Missing static resources (silent 404) ──────────────────────────────
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    // ── 7. Untyped RuntimeException ───────────────────────────────────────────
    // Only BusinessException messages are safe to surface. All other
    // RuntimeExceptions are logged at ERROR and return a generic 500.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        // BusinessException is a RuntimeException; this branch only runs for
        // non-BusinessException RuntimeExceptions (the typed handler above runs first).
        log.error("Unexpected runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }

    // ── 8. Catch-all ─────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }
}
