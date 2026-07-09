-- ============================================================
-- V1: CourtSide initial schema
-- Flyway runs this exactly once per database, records it in
-- flyway_schema_history, and never touches it again.
-- Rule: applied migrations are immutable — changes go in V2, V3, ...
-- ============================================================

-- btree_gist lets a GiST index (needed for range overlap checks)
-- also handle plain equality columns like court_id.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------- users & auth ----------

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,          -- BCrypt output, never the password
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER'
                  CHECK (role IN ('MEMBER', 'MANAGER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Refresh tokens are stored HASHED: if the DB leaks, tokens stay unusable.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,       -- SHA-256 hex
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Postgres does NOT auto-index foreign keys (unlike some DBs) — we do it.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------- clubs & courts ----------

CREATE TABLE clubs (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    city       VARCHAR(80)  NOT NULL,
    address    VARCHAR(255) NOT NULL,
    manager_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_clubs_city ON clubs (city);
CREATE INDEX idx_clubs_manager ON clubs (manager_id);

CREATE TABLE courts (
    id             BIGSERIAL PRIMARY KEY,
    club_id        BIGINT       NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    name           VARCHAR(80)  NOT NULL,
    sport          VARCHAR(30)  NOT NULL DEFAULT 'PADEL'
                   CHECK (sport IN ('PADEL', 'TENNIS', 'SQUASH')),
    slot_minutes   INT          NOT NULL DEFAULT 90 CHECK (slot_minutes BETWEEN 30 AND 240),
    price_per_slot NUMERIC(8,2) NOT NULL CHECK (price_per_slot >= 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0   -- JPA @Version optimistic locking
);

CREATE INDEX idx_courts_club ON courts (club_id);

-- day_of_week: ISO 1=Monday .. 7=Sunday
CREATE TABLE opening_hours (
    id          BIGSERIAL PRIMARY KEY,
    club_id     BIGINT NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    day_of_week INT    NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens       TIME   NOT NULL,
    closes      TIME   NOT NULL,
    CHECK (opens < closes),
    UNIQUE (club_id, day_of_week)
);

-- ---------- bookings: the heart of the system ----------

CREATE TABLE bookings (
    id         BIGSERIAL PRIMARY KEY,
    court_id   BIGINT       NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users (id)  ON DELETE CASCADE,
    start_time TIMESTAMPTZ  NOT NULL,
    end_time   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED'
               CHECK (status IN ('HOLD', 'CONFIRMED', 'CANCELLED', 'NO_SHOW')),
    price      NUMERIC(8,2) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (start_time < end_time),

    -- ========================================================
    -- THE defense against double booking. The database itself
    -- rejects any two rows on the same court whose time ranges
    -- overlap (&&), if both rows are HOLD or CONFIRMED.
    -- No amount of concurrent application code can race past
    -- this: one transaction commits, the other gets an error.
    -- ========================================================
    CONSTRAINT no_overlapping_bookings
        EXCLUDE USING gist (
            court_id WITH =,
            tstzrange(start_time, end_time) WITH &&
        )
        WHERE (status IN ('HOLD', 'CONFIRMED'))
);

-- The two queries we run constantly:
CREATE INDEX idx_bookings_court_time ON bookings (court_id, start_time);
CREATE INDEX idx_bookings_user_time  ON bookings (user_id, start_time DESC);

-- ---------- waitlist (V1 feature, table ready) ----------

CREATE TABLE waitlist_entries (
    id            BIGSERIAL PRIMARY KEY,
    court_id      BIGINT      NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id)  ON DELETE CASCADE,
    desired_range TSTZRANGE   NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (court_id, user_id, desired_range)
);
