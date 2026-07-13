-- ============================================================
-- V3 — FEATURES 1–9 (from db/db-migration-features-1-9.sql)
-- ============================================================

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;

DELIMITER $$
CREATE PROCEDURE tv_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE tv_add_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_ddl   TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND INDEX_NAME   = p_index
    ) THEN
        SET @sql = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- Feature 1: Showtimes support
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
    INDEX idx_movies_status (status),
    INDEX idx_movies_genre  (genre)
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
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    movie_id         BIGINT        NOT NULL,
    screen_id        BIGINT        NOT NULL,
    start_time       DATETIME      NOT NULL,
    language         VARCHAR(50),
    format           VARCHAR(20)   NOT NULL DEFAULT '2D',
    base_price       DECIMAL(10,2) NOT NULL,
    available_seats  INT,
    total_seats      INT,
    status           VARCHAR(20)   NOT NULL DEFAULT 'active',
    FOREIGN KEY (movie_id)  REFERENCES movies(id)  ON DELETE CASCADE,
    FOREIGN KEY (screen_id) REFERENCES screens(id) ON DELETE CASCADE,
    INDEX idx_showtimes_movie      (movie_id),
    INDEX idx_showtimes_screen     (screen_id),
    INDEX idx_showtimes_start_time (start_time),
    INDEX idx_showtimes_status     (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL tv_add_column('bookings', 'showtime_id', 'showtime_id BIGINT NULL AFTER event_id');
CALL tv_add_column('seats',    'showtime_id', 'showtime_id BIGINT NULL AFTER event_id');

-- Feature 2: City on events
CALL tv_add_column('events', 'city', 'city VARCHAR(100) NULL AFTER location');
CALL tv_add_index ('events', 'idx_events_city', '(city)');

-- Feature 3: Seat categories + tiered pricing
CALL tv_add_column('seats', 'category', "category VARCHAR(20) NOT NULL DEFAULT 'Silver' AFTER status");
CALL tv_add_column('seats', 'price',    'price DECIMAL(10,2) NULL AFTER category');
CALL tv_add_index ('seats', 'idx_seats_category', '(category)');

-- Feature 4: Seat hold timer
CALL tv_add_column('seats', 'held_until',      'held_until      DATETIME NULL AFTER price');
CALL tv_add_column('seats', 'held_by_user_id', 'held_by_user_id BIGINT   NULL AFTER held_until');
CALL tv_add_index ('seats', 'idx_seats_held', '(status, held_until)');

-- Feature 5: Reviews
CALL tv_add_column('events', 'average_rating', 'average_rating DECIMAL(3,1) NULL AFTER city');
CALL tv_add_column('events', 'review_count',   'review_count   INT NOT NULL DEFAULT 0 AFTER average_rating');

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

-- Feature 6: Wishlist
CREATE TABLE IF NOT EXISTS wishlists (
    id                     BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT   NOT NULL,
    event_id               BIGINT   NOT NULL,
    notify_on_availability BOOLEAN  NOT NULL DEFAULT FALSE,
    saved_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wishlist_user_event (user_id, event_id),
    INDEX idx_wishlist_user   (user_id),
    INDEX idx_wishlist_event  (event_id),
    INDEX idx_wishlist_notify (event_id, notify_on_availability)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Feature 7: Coupons
CREATE TABLE IF NOT EXISTS coupons (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(50)   NOT NULL UNIQUE,
    discount_type  VARCHAR(10)   NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    min_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    max_discount   DECIMAL(10,2) NULL,
    valid_from     DATETIME      NULL,
    valid_to       DATETIME      NULL,
    usage_limit    INT           NOT NULL DEFAULT 0,
    per_user_limit INT           NOT NULL DEFAULT 1,
    usage_count    INT           NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'active',
    INDEX idx_coupons_code   (code),
    INDEX idx_coupons_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL tv_add_column('bookings', 'coupon_code',     'coupon_code     VARCHAR(50)   NULL AFTER showtime_id');
CALL tv_add_column('bookings', 'discount_amount', 'discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER coupon_code');

-- Feature 8: QR code
CALL tv_add_column('bookings', 'qr_token',      'qr_token       TEXT    NULL    AFTER discount_amount');
CALL tv_add_column('bookings', 'checked_in',    'checked_in     BOOLEAN NOT NULL DEFAULT FALSE AFTER qr_token');
CALL tv_add_column('bookings', 'checked_in_at', 'checked_in_at  DATETIME NULL   AFTER checked_in');

-- Feature 9: Waitlist
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

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;
