-- ============================================================
-- V2: payment holds + a usable waitlist
--
-- The first real schema evolution. Note what we do NOT do: touch V1.
-- Applied migrations are immutable — every environment has already run V1,
-- so changing it would make the recorded checksum mismatch and Flyway would
-- refuse to start. Changes always go forward in a new file.
-- ============================================================

-- ---------- holds ----------

-- A HOLD is a booking that reserves the slot while a payment completes.
-- Storing the deadline on the row (instead of deriving it from created_at)
-- keeps it explicit and lets different holds have different lifetimes later.
ALTER TABLE bookings
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

-- The release job scans for expired holds. A partial index keeps it tiny:
-- it only indexes the few rows that are actually HOLD, not the whole table.
CREATE INDEX idx_bookings_expiring_holds
    ON bookings (hold_expires_at)
    WHERE status = 'HOLD';

-- ---------- waitlist ----------

-- V1 modelled the wanted period as a TSTZRANGE. It works in SQL but maps poorly
-- to JPA (no standard range type), and we never need range operators here — only
-- "does this freed booking match what the user asked for?", which two timestamp
-- columns express just as well and every tool understands.
ALTER TABLE waitlist_entries
    DROP CONSTRAINT IF EXISTS waitlist_entries_court_id_user_id_desired_range_key;

ALTER TABLE waitlist_entries
    DROP COLUMN desired_range;

ALTER TABLE waitlist_entries
    ADD COLUMN start_time  TIMESTAMPTZ NOT NULL,
    ADD COLUMN end_time    TIMESTAMPTZ NOT NULL,
    -- When we told this user "your slot is free". NULL = still waiting.
    ADD COLUMN notified_at TIMESTAMPTZ,
    -- Soft state: an entry is consumed (the user booked), cancelled, or expired.
    ADD COLUMN active      BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT waitlist_time_order CHECK (start_time < end_time);

-- One active entry per user per exact slot: joining twice must not double-notify.
CREATE UNIQUE INDEX idx_waitlist_unique_active
    ON waitlist_entries (court_id, user_id, start_time, end_time)
    WHERE active;

-- The lookup the notifier runs: "who is waiting for this court at this time?",
-- oldest first — the queue is fair, first come first served.
CREATE INDEX idx_waitlist_lookup
    ON waitlist_entries (court_id, start_time, created_at)
    WHERE active;
