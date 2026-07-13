package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/** 409 — duplicate resource, idempotency violation, optimistic lock failure. */
public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
