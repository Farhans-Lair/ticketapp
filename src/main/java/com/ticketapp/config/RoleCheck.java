package com.ticketapp.config;

import com.ticketapp.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Named bean used in @PreAuthorize expressions:
 *   @PreAuthorize("@roleCheck.isAdmin(authentication)")
 *   @PreAuthorize("@roleCheck.isOrganizer(authentication)")
 */
@Component("roleCheck")
public class RoleCheck {

    public boolean isAdmin(Authentication auth) {
        if (!(auth instanceof AuthenticatedUser u)) return false;
        return "admin".equals(u.getRole());
    }

    public boolean isOrganizer(Authentication auth) {
        if (!(auth instanceof AuthenticatedUser u)) return false;
        return "organizer".equals(u.getRole()) || "admin".equals(u.getRole());
    }
}
