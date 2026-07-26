-- V11 — Fix remaining JDBC-type-code mismatches Same root cause as V10 (ENUM vs VARCHAR): a column's

-- reviews.rating (entity: Review.rating, Integer) NOTE: MODIFY COLUMN silently drops the existing CHECK constraint from V3 (verified
ALTER TABLE reviews
  MODIFY COLUMN rating INT NOT NULL;

ALTER TABLE reviews
  ADD CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5);

-- refresh_tokens.token_hash (entity: RefreshToken.tokenHash, String length = 64)
ALTER TABLE refresh_tokens
  MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;
