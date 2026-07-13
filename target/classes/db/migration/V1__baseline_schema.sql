-- ============================================================
-- V1 — BASELINE SCHEMA (from db/schema.sql)
-- Flyway baseline: represents the state of the database before
-- Flyway was introduced. If this is a fresh install, Flyway
-- runs this. If you have an existing database, run:
--   flyway baseline -baselineVersion=1 -baselineOnMigrate=true
-- and then allow V2+ to run normally.
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'user',
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS organizer_profiles (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT NOT NULL UNIQUE,
  business_name    VARCHAR(200) NOT NULL,
  contact_phone    VARCHAR(20)  DEFAULT NULL,
  gst_number       VARCHAR(20)  DEFAULT NULL,
  address          TEXT         DEFAULT NULL,
  status           ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  rejection_reason TEXT         DEFAULT NULL,
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_organizer_profile_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS events (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  organizer_id      INT          DEFAULT NULL,
  title             VARCHAR(200) NOT NULL,
  description       TEXT,
  location          VARCHAR(150),
  event_date        DATETIME     NOT NULL,
  price             FLOAT        NOT NULL,
  total_tickets     INT          NOT NULL,
  available_tickets INT          NOT NULL,
  category          ENUM('Music','Sports','Comedy','Theatre','Conference','Festival','Workshop','Other') DEFAULT 'Other',
  images            TEXT         DEFAULT NULL,
  created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_event_organizer
    FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS seats (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  event_id     INT         NOT NULL,
  seat_number  VARCHAR(10) NOT NULL,
  status       ENUM('available','booked') DEFAULT 'available',
  CONSTRAINT fk_seat_event
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bookings (
  id                   INT AUTO_INCREMENT PRIMARY KEY,
  user_id              INT   NOT NULL,
  event_id             INT   NOT NULL,
  tickets_booked       INT   NOT NULL,
  ticket_amount        FLOAT NOT NULL,
  convenience_fee      FLOAT NOT NULL,
  gst_amount           FLOAT NOT NULL,
  total_paid           FLOAT NOT NULL,
  selected_seats       TEXT         DEFAULT NULL,
  razorpay_order_id    VARCHAR(255) DEFAULT NULL,
  razorpay_payment_id  VARCHAR(255) DEFAULT NULL,
  payment_status       ENUM('pending','paid','failed') DEFAULT 'pending',
  ticket_pdf_s3_key    VARCHAR(512) DEFAULT NULL,
  booking_date         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_booking_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_booking_event
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
