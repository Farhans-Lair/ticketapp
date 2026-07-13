package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the application-level exception hierarchy.
 *
 * Usage: throw a concrete subclass (e.g. NotFoundException, ConflictException)
 * from any service method. GlobalExceptionHandler catches BusinessException and
 * maps it to the appropriate HTTP status + JSON body { "error": "..." }.
 *
 * This prevents leaking JPA / Hibernate internals (e.g. DataIntegrityViolation
 * messages, constraint names, SQL fragments) to the HTTP client.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
