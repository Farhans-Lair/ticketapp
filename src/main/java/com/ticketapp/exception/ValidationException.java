package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/** 400 — business-rule validation failure (not bean-validation, which uses MethodArgumentNotValidException). */
public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
