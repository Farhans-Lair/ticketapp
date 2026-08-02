package com.ticketapp.security;

import com.ticketapp.repository.OrganizerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralized role checks used by controllers.
 *
 * This consolidates logic that used to be duplicated across the codebase:
 *   - EventController had "user == null || !\"admin\".equals(user.getRole())" repeated
 *     inline 10 separate times, once per admin-only endpoint.
 *   - RevenueController and EventCategoryController each had their own copy of the
 *     same admin check.
 *   - OrganizerController and PayoutController each had their own private, word-for-word
 *     identical isApprovedOrganizer()/isAdmin() helper methods.
 *   - CancellationController had its own inline "organizer or admin" check.
 *
 * Every method here preserves the exact original behavior of the check it replaces —
 * this is a consolidation, not a change in authorization logic. Injected as a normal
 * Spring bean and called directly (e.g. "if (!accessControl.isAdmin(user)) ..."),
 * matching the existing manual-check style used throughout the controllers rather
 * than introducing @PreAuthorize/SpEL (which the codebase deliberately moved away
 * from — see the historical comment in RevenueController).
 */
@Component
@RequiredArgsConstructor
public class AccessControl {

    private final OrganizerProfileRepository organizerProfileRepo;

    /** True only for an authenticated user with role="admin". */
    public boolean isAdmin(AuthenticatedUser user) {
        return user != null && "admin".equals(user.getRole());
    }

    /** True for role="admin" OR role="organizer" (no approval-status check). */
    public boolean isOrganizerOrAdmin(AuthenticatedUser user) {
        if (user == null) return false;
        return "admin".equals(user.getRole()) || "organizer".equals(user.getRole());
    }

    /**
     * True for role="admin" (always), or role="organizer" AND an approved
     * organizer profile. Matches the OrganizerController/PayoutController
     * duplicate exactly, including delegating to OrganizerProfileRepository
     * the same way the (now-deleted) original RoleCheck class did.
     */
    public boolean isApprovedOrganizer(AuthenticatedUser user) {
        if (user == null) return false;
        if ("admin".equals(user.getRole())) return true;
        if (!"organizer".equals(user.getRole())) return false;
        return organizerProfileRepo.findByUserId(user.getId())
                .map(p -> "approved".equals(p.getStatus()))
                .orElse(false);
    }
}
