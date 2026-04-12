package com.ticketapp.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * JWT-authenticated principal placed in the SecurityContext.
 *
 * CRITICAL: getPrincipal() must NOT return `this`.
 * AbstractAuthenticationToken.getName() calls getPrincipal().toString(),
 * which calls getName() again → infinite recursion → StackOverflowError.
 * This manifests as ERR_INCOMPLETE_CHUNKED_ENCODING because the JVM crash
 * cuts the HTTP response stream mid-way.
 *
 * @JsonIgnoreProperties prevents Jackson from serializing this class if it
 * ever ends up in a response body (e.g. during error dispatch) — the security
 * object has no business in any JSON response.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final Long   id;
    private final String role;

    // A simple string principal — NOT `this`, to avoid getName() → getPrincipal()
    // → toString() → getName() infinite recursion.
    private final String principalName;

    public AuthenticatedUser(Long id, String role) {
        super(Collections.emptyList());
        this.id            = id;
        this.role          = role;
        this.principalName = "user-" + id;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }

    // Returns a plain String, NOT `this`.
    // AbstractAuthenticationToken.getName() calls getPrincipal().toString()
    // If getPrincipal() returns `this`, toString() eventually calls getName()
    // again → StackOverflowError.
    @Override public Object getPrincipal() { return principalName; }
}
