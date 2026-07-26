package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/* Root of the application-level exception hierarchy. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
