package com.ticketapp.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/* JWT-authenticated principal placed in the SecurityContext by JwtAuthFilter. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final Long   id;
    private final String role;
    private final String sessionId;

    public AuthenticatedUser(Long id, String role, String sessionId) {
        super(Collections.emptyList());
        this.id        = id;
        this.role      = role;
        this.sessionId = sessionId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    /* Returns `this` so @AuthenticationPrincipal can inject AuthenticatedUser directly into controller method parameters. */
    @Override
    public Object getPrincipal() {
        return this;
    }

    /* Override getName() to return a plain string directly. */
    @Override
    public String getName() {
        return "user-" + id;
    }

    /* Override toString() to prevent any external code from triggering the getName() → getPrincipal().toString() → getName() cycle. */
    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", role='" + role + "', sessionId='" + sessionId + "'}";
    }
}
