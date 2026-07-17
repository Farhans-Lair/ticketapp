-- V6__refresh_tokens.sql
--
-- Backs the refresh-token rotation feature. We never store the raw JWT —
-- only a SHA-256 hash of it — so a DB leak alone can't be used to forge
-- a working refresh token.
--
-- session_id ties every refresh token issued during one login session
-- together (it's also embedded as a claim in the access token and the
-- session token), so a whole session can be revoked at once — e.g. on
-- detecting refresh-token reuse, or a future "log out this device"
-- feature — without needing to touch every row individually.

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         INT NOT NULL,
  session_id      VARCHAR(64) NOT NULL,
  token_hash      CHAR(64) NOT NULL,            -- SHA-256 hex digest of the raw JWT
  issued_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at      TIMESTAMP NOT NULL,
  revoked         BOOLEAN NOT NULL DEFAULT FALSE,
  revoked_at      TIMESTAMP NULL,
  replaced_by_id  BIGINT NULL,                  -- points at the row that superseded this one (rotation chain)

  CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_refresh_tokens_replaced_by
    FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL,

  INDEX idx_refresh_tokens_user (user_id),
  INDEX idx_refresh_tokens_hash (token_hash),
  INDEX idx_refresh_tokens_session (session_id),
  INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
