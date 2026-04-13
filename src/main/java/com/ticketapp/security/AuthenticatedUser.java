package com.ticketapp.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * JWT-authenticated principal placed in the SecurityContext by JwtAuthFilter.
 *
 * CRITICAL DESIGN:
 *
 * 1. getPrincipal() MUST return `this` so that Spring Security 6's
 *    AuthenticationPrincipalArgumentResolver can inject this object into
 *    controller parameters annotated with @AuthenticationPrincipal AuthenticatedUser.
 *
 *    The resolver does: authentication.getPrincipal() → checks instanceof parameter type
 *    If getPrincipal() returns a String, the instanceof check fails → null is injected.
 *    If getPrincipal() returns `this` (AuthenticatedUser), instanceof passes → correct.
 *
 * 2. getName() MUST be overridden to NOT call getPrincipal().toString().
 *    AbstractAuthenticationToken.getName() calls getPrincipal().toString().
 *    If we override toString() to call getName() (or if toString() is not overridden),
 *    we get: getName() → getPrincipal().toString() → getName() → StackOverflowError.
 *    Fix: override getName() directly to return a plain string (breaking the cycle).
 *
 * 3. toString() is also overridden to prevent any other code path from
 *    triggering the getName() → getPrincipal().toString() → getName() loop.
 *
 * @JsonIgnoreProperties prevents Jackson from serializing this security object
 * if it ever ends up in an error dispatch path.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final Long   id;
    private final String role;

    public AuthenticatedUser(Long id, String role) {
        super(Collections.emptyList());
        this.id   = id;
        this.role = role;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * Returns `this` so @AuthenticationPrincipal can inject AuthenticatedUser
     * directly into controller method parameters.
     *
     * Spring's AuthenticationPrincipalArgumentResolver does:
     *   Object principal = auth.getPrincipal();
     *   if (principal instanceof <parameter type>) return principal;
     * Since <parameter type> is AuthenticatedUser and getPrincipal() returns `this`,
     * the instanceof check passes and the correct object is injected.
     */
    @Override
    public Object getPrincipal() {
        return this;
    }

    /**
     * Override getName() to return a plain string directly.
     *
     * AbstractAuthenticationToken.getName() is implemented as:
     *   return getPrincipal().toString()
     * If getPrincipal() returns `this`, then toString() is called on AuthenticatedUser.
     * If toString() were to call getName() (or super.toString()), we'd get infinite recursion.
     *
     * By overriding getName() to return a fixed string directly (not via getPrincipal()),
     * we break the recursion entirely.
     */
    @Override
    public String getName() {
        return "user-" + id;
    }

    /**
     * Override toString() to prevent any external code from triggering the
     * getName() → getPrincipal().toString() → getName() cycle.
     */
    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", role='" + role + "'}";
    }
}
