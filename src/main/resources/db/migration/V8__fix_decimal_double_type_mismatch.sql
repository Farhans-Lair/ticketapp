-- ============================================================
-- V8 — Fix Hibernate schema-validation mismatches
--
-- Several columns were created as DECIMAL in earlier migrations
-- (V3/V4) but their JPA entity fields are mapped as Java `Double`,
-- which Hibernate expects to correspond to a FLOAT column.
--
-- This caused SchemaManagementException: Schema-validation:
-- wrong column type encountered ... found [decimal], but expecting
-- [float(53)] on application startup, crash-looping the container
-- and failing ALB health checks.
--
-- Fix: align the DB column type with the existing Java type (Double
-- -> FLOAT), matching the pattern already used successfully for
-- bookings.ticket_amount, convenience_fee, gst_amount, total_paid,
-- and events.price (all FLOAT + Double, and already passing
-- validation).
-- ============================================================

-- bookings
ALTER TABLE bookings MODIFY COLUMN cancellation_fee     FLOAT NULL;
ALTER TABLE bookings MODIFY COLUMN cancellation_fee_gst FLOAT NULL;
ALTER TABLE bookings MODIFY COLUMN refund_amount        FLOAT NULL;
ALTER TABLE bookings MODIFY COLUMN discount_amount      FLOAT NOT NULL DEFAULT 0.00;
ALTER TABLE bookings MODIFY COLUMN price_per_ticket     FLOAT NULL;

-- coupons
ALTER TABLE coupons MODIFY COLUMN discount_value FLOAT NOT NULL;
ALTER TABLE coupons MODIFY COLUMN min_amount     FLOAT NOT NULL DEFAULT 0.00;
ALTER TABLE coupons MODIFY COLUMN max_discount   FLOAT NULL;

-- seats
ALTER TABLE seats MODIFY COLUMN price FLOAT NULL;

-- events
ALTER TABLE events MODIFY COLUMN average_rating FLOAT NULL;

-- organizer_payouts
ALTER TABLE organizer_payouts MODIFY COLUMN amount       FLOAT NOT NULL DEFAULT 0.00;
ALTER TABLE organizer_payouts MODIFY COLUMN platform_fee FLOAT NOT NULL DEFAULT 0.00;
