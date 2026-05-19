-- ============================================================
-- Fix: Emoji characters showing as garbled text in categories
-- Root cause: event_categories table or icon_emoji column is
-- using utf8 (3-byte) instead of utf8mb4 (4-byte, full emoji).
-- Run this ONCE in MySQL, then restart the Spring Boot app.
-- ============================================================

-- Step 1: Convert the whole database default charset
ALTER DATABASE ticket_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Step 2: Convert the event_categories table
ALTER TABLE event_categories
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Step 3: Explicitly fix the icon_emoji column
ALTER TABLE event_categories
  MODIFY icon_emoji VARCHAR(10)
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '🎟️';

-- Step 4: Re-seed the emoji values now that the column supports 4-byte chars
UPDATE event_categories SET icon_emoji = '🎵' WHERE slug = 'Music';
UPDATE event_categories SET icon_emoji = '⚽' WHERE slug = 'Sports';
UPDATE event_categories SET icon_emoji = '😂' WHERE slug = 'Comedy';
UPDATE event_categories SET icon_emoji = '🎭' WHERE slug = 'Theatre';
UPDATE event_categories SET icon_emoji = '💼' WHERE slug = 'Conference';
UPDATE event_categories SET icon_emoji = '🎉' WHERE slug = 'Festival';
UPDATE event_categories SET icon_emoji = '🔧' WHERE slug = 'Workshop';
UPDATE event_categories SET icon_emoji = '🎟️' WHERE slug = 'Other';

-- Verify
SELECT id, name, slug, icon_emoji FROM event_categories ORDER BY sort_order;
