package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/** 404 — resource does not exist or caller lacks access to it. */
public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
