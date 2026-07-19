-- ============================================================
-- V7 — Widen id/FK columns from INT to BIGINT
--
-- ROOT CAUSE: several tables were created back in V1 (and one in V4,
-- plus refresh_tokens in V6) with `id INT AUTO_INCREMENT` / `xxx_id INT`,
-- while every JPA entity in the codebase declares `private Long id`
-- (Hibernate maps Long -> BIGINT). This was invisible under
-- ddl-auto=update (it only ADDS missing things, never validates
-- existing column types) and was only caught the first time
-- ddl-auto=validate ran — application-prod.properties, activated via
-- SPRING_PROFILES_ACTIVE=prod — which correctly refused to start:
--
--   Schema-validation: wrong column type encountered in column [id]
--   in table [bookings]; found [int], but expecting [bigint]
--
-- Widening INT -> BIGINT is safe and backward-compatible: every
-- existing INT value fits inside BIGINT unchanged, no data is altered
-- or lost.
--
-- MySQL requires FK constraints touching a column to be dropped
-- before that column's type can change, then recreated afterward —
-- hence the three-phase structure below.
-- ============================================================

-- ── Phase 1: drop FK constraints touching the affected columns ─────────
ALTER TABLE organizer_profiles DROP FOREIGN KEY fk_organizer_profile_user;
ALTER TABLE events             DROP FOREIGN KEY fk_event_organizer;
ALTER TABLE seats              DROP FOREIGN KEY fk_seat_event;
ALTER TABLE bookings           DROP FOREIGN KEY fk_booking_user;
ALTER TABLE bookings           DROP FOREIGN KEY fk_booking_event;
ALTER TABLE organizer_payouts  DROP FOREIGN KEY fk_payout_organizer;
ALTER TABLE refresh_tokens     DROP FOREIGN KEY fk_refresh_tokens_user;

-- ── Phase 2: widen every affected id / FK column to BIGINT ─────────────
ALTER TABLE users MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE organizer_profiles MODIFY COLUMN id      BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE organizer_profiles MODIFY COLUMN user_id BIGINT NOT NULL;

ALTER TABLE events MODIFY COLUMN id           BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE events MODIFY COLUMN organizer_id BIGINT NULL;

ALTER TABLE seats MODIFY COLUMN id       BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE seats MODIFY COLUMN event_id BIGINT NOT NULL;

ALTER TABLE bookings MODIFY COLUMN id       BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE bookings MODIFY COLUMN user_id  BIGINT NOT NULL;
ALTER TABLE bookings MODIFY COLUMN event_id BIGINT NOT NULL;

ALTER TABLE organizer_payouts MODIFY COLUMN id           BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE organizer_payouts MODIFY COLUMN organizer_id BIGINT NOT NULL;

ALTER TABLE event_categories MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- refresh_tokens.id was already BIGINT (V6) — only user_id needs widening.
ALTER TABLE refresh_tokens MODIFY COLUMN user_id BIGINT NOT NULL;

-- ── Phase 3: recreate the FK constraints, unchanged, on the widened columns ──
ALTER TABLE organizer_profiles
  ADD CONSTRAINT fk_organizer_profile_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE events
  ADD CONSTRAINT fk_event_organizer
    FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE seats
  ADD CONSTRAINT fk_seat_event
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;

ALTER TABLE bookings
  ADD CONSTRAINT fk_booking_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE bookings
  ADD CONSTRAINT fk_booking_event
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;

ALTER TABLE organizer_payouts
  ADD CONSTRAINT fk_payout_organizer
    FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens
  ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
