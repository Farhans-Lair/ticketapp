package com.ticketapp.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * Placed into the SecurityContext after JWT verification.
 * Mirrors the shape of req.user in the Express middleware (id, role).
 */
@Getter
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final Long id;
    private final String role;

    public AuthenticatedUser(Long id, String role) {
        super(Collections.emptyList());
        this.id   = id;
        this.role = role;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal()   { return this;   }
}
