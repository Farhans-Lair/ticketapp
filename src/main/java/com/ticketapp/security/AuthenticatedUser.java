package com.ticketapp.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * JWT-authenticated principal placed in the SecurityContext by JwtAuthFilter.
 *
 * TWO CRITICAL FIXES applied here:
 *
 * FIX 1 — getPrincipal() must NOT return `this`:
 *   AbstractAuthenticationToken.getName() calls getPrincipal().toString().
 *   If getPrincipal() returns `this`, then toString() eventually calls getName()
 *   again → StackOverflowError, which cuts the HTTP response mid-stream
 *   → ERR_INCOMPLETE_CHUNKED_ENCODING in the browser.
 *
 * FIX 2 — Spring Security 6 @AuthenticationPrincipal resolution:
 *   AuthenticationPrincipalArgumentResolver calls authentication.getPrincipal()
 *   and checks if the result is an instance of the parameter type.
 *   If getPrincipal() returns `this` (the Authentication object itself),
 *   Spring Security's resolver can misinterpret it and inject null into
 *   controller parameters annotated with @AuthenticationPrincipal.
 *   Returning a distinct String principal avoids this edge case.
 *
 * @JsonIgnoreProperties prevents Jackson from attempting to serialize this
 * class if it somehow ends up in error dispatch paths.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final Long   id;
    private final String role;

    // A simple string used as the principal name — NOT `this`.
    private final String principalName;

    public AuthenticatedUser(Long id, String role) {
        super(Collections.emptyList());
        this.id            = id;
        this.role          = role;
        this.principalName = "user-" + id;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * Returns a plain String, NOT `this`.
     * This is essential for two reasons:
     *  1. Prevents StackOverflowError via getName() → getPrincipal().toString() → getName() loop
     *  2. Ensures Spring's @AuthenticationPrincipal resolver correctly resolves
     *     the AuthenticatedUser object (since the parameter type != principal type,
     *     Spring uses authentication itself, which IS AuthenticatedUser)
     */
    @Override
    public Object getPrincipal() {
        return principalName;
    }
}
