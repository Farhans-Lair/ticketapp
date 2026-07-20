-- ============================================================
-- V11 — Fix remaining JDBC-type-code mismatches
--
-- Same root cause as V10 (ENUM vs VARCHAR): a column's underlying
-- SQL type reports a different java.sql.Types code than what
-- Hibernate expects for the mapped Java field, so ddl-auto=validate
-- rejects it even though the two types can obviously hold the same
-- values.
--
-- 1) reviews.rating is TINYINT (java.sql.Types.TINYINT), but
--    Review.rating is a Java Integer (Hibernate expects
--    java.sql.Types.INTEGER). Note: this is distinct from the
--    TINYINT(1) columns elsewhere (is_active, is_featured,
--    is_cancellation_allowed) — those map to Boolean fields and
--    MySQL Connector/J's tinyInt1isBit behavior reports TINYINT(1)
--    as BIT/BOOLEAN, which Hibernate's Boolean mapping already
--    matches correctly. Plain TINYINT (no "(1)" width) does not get
--    that treatment, so it stays a genuine INTEGER mismatch.
--
-- 2) refresh_tokens.token_hash is CHAR(64), but RefreshToken.tokenHash
--    is a String with length = 64 (Hibernate expects VARCHAR(64),
--    not CHAR(64)) — same CHAR-vs-VARCHAR type-code mismatch as the
--    ENUM columns fixed in V10 (ENUM is also reported as CHAR by
--    MySQL's JDBC driver).
-- ============================================================

-- reviews.rating  (entity: Review.rating, Integer)
--
-- NOTE: MODIFY COLUMN silently drops the existing CHECK constraint
-- from V3 (verified against a real MySQL-compatible instance) — it
-- must be explicitly re-added afterward or the 1-5 rating bound is
-- lost with no error or warning.
ALTER TABLE reviews
  MODIFY COLUMN rating INT NOT NULL;

ALTER TABLE reviews
  ADD CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5);

-- refresh_tokens.token_hash  (entity: RefreshToken.tokenHash, String length = 64)
ALTER TABLE refresh_tokens
  MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;
