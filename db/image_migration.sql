-- MIGRATION: Fix event images — base64 → S3 URL paths BACKGROUND Before this migration, event images

UPDATE events
SET    images = NULL
WHERE  images LIKE '%"data:%';

-- Step 2: Shrink the column from LONGTEXT to TEXT TEXT holds up to 65 535 bytes
ALTER TABLE events
  MODIFY COLUMN images TEXT DEFAULT NULL;
