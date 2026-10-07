-- ============================================================
-- V5: club photos
--
-- The bytes live in their own table rather than on `clubs` on purpose. The club
-- list is read on every landing-page and directory load, and a BYTEA column
-- hanging off `clubs` would drag every photo into memory on each of those
-- requests. A separate table keyed by club id means club queries never touch the
-- bytes, there is at most one photo per club by construction, and the row
-- disappears with the club (ON DELETE CASCADE) instead of orphaning megabytes.
-- ============================================================

CREATE TABLE club_photos (
    club_id      BIGINT PRIMARY KEY REFERENCES clubs (id) ON DELETE CASCADE,
    content_type VARCHAR(64) NOT NULL,
    data         BYTEA       NOT NULL
);

-- Every club that already exists gets a placeholder cover, so the directory has
-- imagery before a manager has uploaded anything and so the photo pipeline is
-- exercised end to end from the very first run. The artwork is generated from
-- the club's own name rather than hardcoded, which keeps it correct for any row
-- present when this migration runs.
--
-- convert_to() rather than a bytea literal: the value stays readable text until
-- the final encoding step, so the markup below is what it actually says.
INSERT INTO club_photos (club_id, content_type, data)
SELECT id,
       'image/svg+xml',
       convert_to(
           '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 450" role="img" aria-label="'
               || name || '">'
               || '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
               || '<stop offset="0" stop-color="#0f2f46"/>'
               || '<stop offset="1" stop-color="#17496b"/>'
               || '</linearGradient></defs>'
               || '<rect width="800" height="450" fill="url(#g)"/>'
               || '<circle cx="720" cy="70" r="180" fill="#1d5f8a" opacity="0.55"/>'
               || '<rect x="44" y="44" width="712" height="362" fill="none" stroke="#ffffff"'
               || ' stroke-opacity="0.25" stroke-width="2"/>'
               || '<text x="64" y="378" font-family="system-ui,sans-serif" font-size="58"'
               || ' font-weight="600" fill="#ffffff">' || name || '</text>'
               || '</svg>',
           'UTF8')
FROM clubs;
