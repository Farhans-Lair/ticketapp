package com.ticketapp.exception;

import org.springframework.http.HttpStatus;

/* 401 — the caller's credentials (password, OTP) are missing or invalid. */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
