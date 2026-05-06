-- =============================================================================
-- TicketApp — Feature migration SQL
-- Generated for: Features 1–9
-- Run this ONCE against your existing database before deploying the new code.
-- Hibernate ddl-auto=update will handle the JPA entities, but explicit ALTER
-- statements here ensure the column order and indexes are exactly right and
-- let you review/test in staging before production.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Feature 1: Movies + Cinemas + Screens + Showtimes
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS movies (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    cast             VARCHAR(500),
    director         VARCHAR(150),
    runtime_minutes  INT,
    genre            VARCHAR(100),
    language         VARCHAR(50),
    certification    VARCHAR(20),
    trailer_url      VARCHAR(512),
    poster_url       VARCHAR(512),
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movies_status  (status),
    INDEX idx_movies_genre   (genre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cinemas (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    address     TEXT,
    city        VARCHAR(100) NOT NULL,
    latitude    DOUBLE,
    longitude   DOUBLE,
    amenities   TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    INDEX idx_cinemas_city   (city),
    INDEX idx_cinemas_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screens (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cinema_id      BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    screen_type    VARCHAR(20)  NOT NULL DEFAULT '2D',
    total_capacity INT,
    FOREIGN KEY (cinema_id) REFERENCES cinemas(id) ON DELETE CASCADE,
    INDEX idx_screens_cinema (cinema_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS showtimes (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    movie_id         BIGINT       NOT NULL,
    screen_id        BIGINT       NOT NULL,
    start_time       DATETIME     NOT NULL,
    language         VARCHAR(50),
    format           VARCHAR(20)  NOT NULL DEFAULT '2D',
    base_price       DECIMAL(10,2) NOT NULL,
    available_seats  INT,
    total_seats      INT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    FOREIGN KEY (movie_id)  REFERENCES movies(id)  ON DELETE CASCADE,
    FOREIGN KEY (screen_id) REFERENCES screens(id) ON DELETE CASCADE,
    INDEX idx_showtimes_movie      (movie_id),
    INDEX idx_showtimes_screen     (screen_id),
    INDEX idx_showtimes_start_time (start_time),
    INDEX idx_showtimes_status     (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Feature 1 additions to existing tables
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS showtime_id BIGINT NULL AFTER event_id;

ALTER TABLE seats
    ADD COLUMN IF NOT EXISTS showtime_id BIGINT NULL AFTER event_id;

-- ---------------------------------------------------------------------------
-- Feature 2: City column on events
-- ---------------------------------------------------------------------------
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS city VARCHAR(100) NULL AFTER location;

CREATE INDEX IF NOT EXISTS idx_events_city ON events(city);

-- ---------------------------------------------------------------------------
-- Feature 3: Seat category + tiered pricing
-- ---------------------------------------------------------------------------
ALTER TABLE seats
    ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'Silver' AFTER status,
    ADD COLUMN IF NOT EXISTS price    DECIMAL(10,2) NULL AFTER category;

CREATE INDEX IF NOT EXISTS idx_seats_category ON seats(category);

-- ---------------------------------------------------------------------------
-- Feature 4: Seat hold timer
-- ---------------------------------------------------------------------------
ALTER TABLE seats
    ADD COLUMN IF NOT EXISTS held_until       DATETIME NULL AFTER price,
    ADD COLUMN IF NOT EXISTS held_by_user_id  BIGINT   NULL AFTER held_until;

-- Composite index for the scheduler sweep (status='held' AND held_until < NOW())
CREATE INDEX IF NOT EXISTS idx_seats_held ON seats(status, held_until);

-- ---------------------------------------------------------------------------
-- Feature 5: Reviews & ratings
-- ---------------------------------------------------------------------------
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS average_rating DECIMAL(3,1) NULL AFTER city,
    ADD COLUMN IF NOT EXISTS review_count   INT NOT NULL DEFAULT 0 AFTER average_rating;

CREATE TABLE IF NOT EXISTS reviews (
    id               BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT   NOT NULL,
    event_id         BIGINT   NULL,
    movie_id         BIGINT   NULL,
    rating           TINYINT  NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text             TEXT,
    verified_booking BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_review_user_event (user_id, event_id),
    UNIQUE KEY uq_review_user_movie (user_id, movie_id),
    INDEX idx_reviews_event   (event_id),
    INDEX idx_reviews_movie   (movie_id),
    INDEX idx_reviews_user    (user_id),
    INDEX idx_reviews_verified(verified_booking)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- Feature 6: Wishlist / "Notify me"
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wishlists (
    id                    BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT   NOT NULL,
    event_id              BIGINT   NOT NULL,
    notify_on_availability BOOLEAN NOT NULL DEFAULT FALSE,
    saved_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wishlist_user_event (user_id, event_id),
    INDEX idx_wishlist_user  (user_id),
    INDEX idx_wishlist_event (event_id),
    INDEX idx_wishlist_notify(event_id, notify_on_availability)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- Feature 7: Coupons & offers
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS coupons (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    discount_type  VARCHAR(10)  NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    min_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    max_discount   DECIMAL(10,2) NULL,
    valid_from     DATETIME     NULL,
    valid_to       DATETIME     NULL,
    usage_limit    INT          NOT NULL DEFAULT 0,
    per_user_limit INT          NOT NULL DEFAULT 1,
    usage_count    INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'active',
    INDEX idx_coupons_code   (code),
    INDEX idx_coupons_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(50)    NULL AFTER showtime_id,
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(10,2)  NOT NULL DEFAULT 0.00 AFTER coupon_code;

-- ---------------------------------------------------------------------------
-- Feature 8: QR-code tickets + check-in
-- ---------------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS qr_token       TEXT     NULL   AFTER discount_amount,
    ADD COLUMN IF NOT EXISTS checked_in     BOOLEAN  NOT NULL DEFAULT FALSE AFTER qr_token,
    ADD COLUMN IF NOT EXISTS checked_in_at  DATETIME NULL    AFTER checked_in;

-- ---------------------------------------------------------------------------
-- Feature 9: Waiting list
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS waitlist (
    id             BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT   NOT NULL,
    event_id       BIGINT   NOT NULL,
    tickets_wanted INT      NOT NULL DEFAULT 1,
    notified_at    DATETIME NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'waiting',
    joined_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_waitlist_user_event (user_id, event_id),
    INDEX idx_waitlist_event  (event_id),
    INDEX idx_waitlist_user   (user_id),
    INDEX idx_waitlist_status (event_id, status, joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
-- Done. Verify with:
--   SHOW TABLES;
--   DESCRIBE seats;
--   DESCRIBE bookings;
--   DESCRIBE events;
-- =============================================================================
