package com.ticketapp.controller;

import com.ticketapp.entity.EventCategory;
import com.ticketapp.repository.EventCategoryRepository;
import com.ticketapp.security.AccessControl;
import com.ticketapp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EventCategoryController {

    private final EventCategoryRepository categoryRepo;
    private final AccessControl           accessControl;

    // Public: active categories

    /* GET /categories — no auth required. */
    @GetMapping("/categories")
    public ResponseEntity<List<EventCategory>> listCategories() {
        return ResponseEntity.ok(
            categoryRepo.findByIsActiveTrueOrderBySortOrderAscNameAsc());
    }

    // Admin: list ALL categories

    @GetMapping("/admin/categories")
    public ResponseEntity<?> adminListCategories(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!accessControl.isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        return ResponseEntity.ok(categoryRepo.findAllByOrderBySortOrderAscNameAsc());
    }

    // Admin: create category

    /* POST /admin/categories Body: { "name": "Live Music", "icon_emoji": "🎵", "image_url": "...", "sort_order": 1 } Slug auto-generated */
    @PostMapping("/admin/categories")
    public ResponseEntity<?> createCategory(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!accessControl.isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));

        String name = body.get("name") instanceof String s ? s.trim() : null;
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name is required."));

        String slug = name.replaceAll("\\s+", "_");

        if (categoryRepo.findBySlug(slug).isPresent())
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Category \"" + name + "\" already exists."));

        EventCategory cat = new EventCategory();
        cat.setName(name);
        cat.setSlug(slug);
        cat.setIconEmoji(body.get("icon_emoji") instanceof String e ? e : "🎟️");
        cat.setImageUrl(body.get("image_url") instanceof String u ? u : null);
        cat.setSortOrder(body.get("sort_order") instanceof Number n ? n.intValue() : 0);
        cat.setIsActive(true);

        EventCategory saved = categoryRepo.save(cat);
        log.info("Category created: adminId={} categoryId={} name={}", user.getId(), saved.getId(), saved.getName());
        return ResponseEntity.status(201).body(saved);
    }

    // Admin: update category

    /* PUT /admin/categories/{id} Accepts partial update: only provided fields are changed. */
    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!accessControl.isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));

        EventCategory cat = categoryRepo.findById(id).orElse(null);
        if (cat == null)
            return ResponseEntity.status(404).body(Map.of("error", "Category not found."));

        if (body.containsKey("name") && body.get("name") instanceof String n) {
            String trimmed = n.trim();
            cat.setName(trimmed);
            cat.setSlug(trimmed.replaceAll("\\s+", "_"));
        }
        if (body.containsKey("icon_emoji") && body.get("icon_emoji") instanceof String e)
            cat.setIconEmoji(e);
        if (body.containsKey("image_url"))
            cat.setImageUrl(body.get("image_url") instanceof String u ? u : null);
        if (body.containsKey("sort_order") && body.get("sort_order") instanceof Number n)
            cat.setSortOrder(n.intValue());
        if (body.containsKey("is_active"))
            cat.setIsActive(Boolean.TRUE.equals(body.get("is_active")));

        categoryRepo.save(cat);
        log.info("Category updated: adminId={} categoryId={}", user.getId(), id);
        return ResponseEntity.ok(cat);
    }

    // Admin: delete category

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<?> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!accessControl.isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));

        EventCategory cat = categoryRepo.findById(id).orElse(null);
        if (cat == null)
            return ResponseEntity.status(404).body(Map.of("error", "Category not found."));

        categoryRepo.delete(cat);
        log.info("Category deleted: adminId={} categoryId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Category deleted."));
    }

}
