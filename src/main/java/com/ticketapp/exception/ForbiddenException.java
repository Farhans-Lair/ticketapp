package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/** 403 — authenticated but not authorised for this operation. */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
