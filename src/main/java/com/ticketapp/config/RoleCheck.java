package com.ticketapp.config;

import com.ticketapp.repository.OrganizerProfileRepository;
import com.ticketapp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Named bean used in @PreAuthorize expressions:
 *   @PreAuthorize("@roleCheck.isAdmin(authentication)")
 *   @PreAuthorize("@roleCheck.isOrganizer(authentication)")
 *
 * isOrganizer() mirrors the original Express authorizeOrganizer middleware:
 * it checks both role="organizer" AND profile.status="approved".
 * Pending or rejected organizers get 403 instead of reaching controllers.
 */

@Component("roleCheck")
@RequiredArgsConstructor
public class RoleCheck {

    private final OrganizerProfileRepository profileRepo;


    public boolean isAdmin(Authentication auth) {
        if (!(auth instanceof AuthenticatedUser u)) return false;
        return "admin".equals(u.getRole());
    }

    public boolean isOrganizer(Authentication auth) {
        if (!(auth instanceof AuthenticatedUser u)) return false;

        // Admins can access all organizer routes without a profile check
        if ("admin".equals(u.getRole())) return true;

        // Organizers must have role="organizer" AND an approved profile
        if (!"organizer".equals(u.getRole())) return false;

        return profileRepo.findByUserId(u.getId())
                .map(profile -> "approved".equals(profile.getStatus()))
                .orElse(false);

    }
}
