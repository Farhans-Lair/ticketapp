-- ============================================================
-- Fix: Clear stale seat holds
-- Run this in MySQL whenever seats appear "stuck" as held
-- but no checkout is in progress.
-- ============================================================

-- Release all seats whose hold timer has already expired
UPDATE seats
SET    status           = 'available',
       held_by_user_id  = NULL,
       held_until       = NULL
WHERE  status    = 'held'
  AND  held_until < NOW();

-- If the above doesn't clear them (e.g. held_until was never set),
-- run this to force-release ALL held seats:
-- UPDATE seats SET status='available', held_by_user_id=NULL, held_until=NULL
-- WHERE status = 'held';

-- Verify
SELECT event_id, status, COUNT(*) AS cnt
FROM   seats
GROUP  BY event_id, status
ORDER  BY event_id, status;
