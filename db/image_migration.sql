-- ============================================================
-- MIGRATION: Fix event images — base64 → S3 URL paths
-- ============================================================
--
-- BACKGROUND
-- Before this migration, event images were stored as raw base64
-- JPEG data-URIs inside the LONGTEXT column, e.g.:
--   ["data:image/jpeg;base64,/9j/4AAQSkZJRgAB..."]
-- A single 5-image event could occupy ~2 MB of row storage.
-- The events list API shipped all that base64 back to every browser
-- on every page load, making it extremely slow.
--
-- AFTER THIS MIGRATION
-- The images column stores compact S3 proxy URL paths, e.g.:
--   ["/api/images/events/images/abc123.jpg"]
-- Storage per event drops from ~2 MB to ~150 bytes (5 images).
--
-- WHAT THIS SCRIPT DOES
--   1. Clears all rows that still contain base64 data (identified by
--      the "data:" prefix inside the JSON string).
--   2. Shrinks the column from LONGTEXT to TEXT — sufficient for URLs.
--
-- IMPORTANT: After running this migration, organizers will need to
-- re-upload images for any events whose images column is cleared.
-- Events themselves (title, date, seats, etc.) are NOT affected.
--
-- Idempotent: safe to run more than once.
-- ============================================================


-- ── Step 1: Clear rows that still contain base64 data-URIs ───
--
-- Base64 data-URIs always start with  ["data:
-- S3 proxy paths start with           ["/api/
-- This pattern safely targets only the old format.
UPDATE events
SET    images = NULL
WHERE  images LIKE '%"data:%';

-- ── Step 2: Shrink the column from LONGTEXT to TEXT ──────────
--
-- TEXT holds up to 65 535 bytes — more than enough for a JSON array
-- of URL paths. LONGTEXT (4 GB) was only needed for base64 blobs.
--
-- This ALTER TABLE performs a metadata-only change on InnoDB when
-- the existing data fits in TEXT (which it now does after Step 1).
ALTER TABLE events
  MODIFY COLUMN images TEXT DEFAULT NULL;
