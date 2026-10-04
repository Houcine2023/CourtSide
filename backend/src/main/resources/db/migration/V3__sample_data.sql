-- ============================================================
-- V3: Sample data for development and testing
-- Run after V1 and V2. Safe to run multiple times (uses ON CONFLICT DO NOTHING).
-- ============================================================

-- ============================================================
-- USERS
-- Passwords are BCrypt hash of "secret123" (cost 10)
-- Hash: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa
-- ============================================================

-- Admin user
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('admin@courtside.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Admin User', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- Manager users
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('manager1@courtside.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Ahmed Ben Ali', 'MANAGER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('manager2@courtside.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Fatma Trabelsi', 'MANAGER')
ON CONFLICT (email) DO NOTHING;

-- Member users
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('ahmed@test.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Ahmed Mansouri', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('sarah@test.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Sarah Ben Youssef', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('karim@test.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Karim Bouaziz', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('leila@test.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Leila Mzoughi', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('youssef@test.tn', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'Youssef Jaziri', 'MEMBER')
ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- CLUBS
-- ============================================================

-- Club 1: Padel Club Tunis (managed by manager1)
INSERT INTO clubs (name, city, address, manager_id)
VALUES ('Padel Club Tunis', 'Tunis', '15 Rue de la Liberté, Tunis 1001', (SELECT id FROM users WHERE email = 'manager1@courtside.tn'))
ON CONFLICT DO NOTHING;

-- Club 2: Tennis Club La Marsa (managed by manager1)
INSERT INTO clubs (name, city, address, manager_id)
VALUES ('Tennis Club La Marsa', 'La Marsa', '8 Avenue Habib Bourguiba, La Marsa 2070', (SELECT id FROM users WHERE email = 'manager1@courtside.tn'))
ON CONFLICT DO NOTHING;

-- Club 3: Squash Center Sfax (managed by manager2)
INSERT INTO clubs (name, city, address, manager_id)
VALUES ('Squash Center Sfax', 'Sfax', '22 Rue Jaziri, Sfax 3000', (SELECT id FROM users WHERE email = 'manager2@courtside.tn'))
ON CONFLICT DO NOTHING;

-- Club 4: Multi-Sport Club Sousse (no manager assigned)
INSERT INTO clubs (name, city, address, manager_id)
VALUES ('Multi-Sport Club Sousse', 'Sousse', '5 Boulevard de l''Environnement, Sousse 4000', NULL)
ON CONFLICT DO NOTHING;

-- Club 5: Padel Paradise Hammamet (no manager assigned)
INSERT INTO clubs (name, city, address, manager_id)
VALUES ('Padel Paradise Hammamet', 'Hammamet', 'Zone Touristique, Hammamet 8050', NULL)
ON CONFLICT DO NOTHING;

-- ============================================================
-- COURTS
-- ============================================================

-- Club 1: Padel Club Tunis (3 courts)
INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Padel Club Tunis'),
    'Court Central', 'PADEL', 90, 45.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Padel Club Tunis'),
    'Court Est', 'PADEL', 90, 40.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Padel Club Tunis'),
    'Court Ouest', 'PADEL', 90, 40.000, true
) ON CONFLICT DO NOTHING;

-- Club 2: Tennis Club La Marsa (4 courts)
INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa'),
    'Court Philippe Chatrier', 'TENNIS', 90, 55.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa'),
    'Court Suzanne Lenglen', 'TENNIS', 90, 50.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa'),
    'Court 3', 'TENNIS', 90, 45.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa'),
    'Court 4', 'TENNIS', 60, 35.000, true
) ON CONFLICT DO NOTHING;

-- Club 3: Squash Center Sfax (3 courts)
INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Squash Center Sfax'),
    'Court A', 'SQUASH', 45, 25.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Squash Center Sfax'),
    'Court B', 'SQUASH', 45, 25.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Squash Center Sfax'),
    'Court C', 'SQUASH', 45, 20.000, false
) ON CONFLICT DO NOTHING;

-- Club 4: Multi-Sport Club Sousse (3 courts - mixed sports)
INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Multi-Sport Club Sousse'),
    'Padel Court 1', 'PADEL', 90, 35.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Multi-Sport Club Sousse'),
    'Tennis Court 1', 'TENNIS', 90, 40.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Multi-Sport Club Sousse'),
    'Squash Court 1', 'SQUASH', 45, 22.000, true
) ON CONFLICT DO NOTHING;

-- Club 5: Padel Paradise Hammamet (2 courts)
INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Padel Paradise Hammamet'),
    'Sunset Court', 'PADEL', 90, 50.000, true
) ON CONFLICT DO NOTHING;

INSERT INTO courts (club_id, name, sport, slot_minutes, price_per_slot, active)
VALUES (
    (SELECT id FROM clubs WHERE name = 'Padel Paradise Hammamet'),
    'Ocean View Court', 'PADEL', 90, 55.000, true
) ON CONFLICT DO NOTHING;

-- ============================================================
-- OPENING HOURS
-- day_of_week: 1=Monday ... 7=Sunday (ISO)
-- ============================================================

-- Club 1: Padel Club Tunis (08:00-23:00 daily)
INSERT INTO opening_hours (club_id, day_of_week, opens, closes)
SELECT c.id, d.day, TIME '08:00:00', TIME '23:00:00'
FROM clubs c
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS d(day)
WHERE c.name = 'Padel Club Tunis'
ON CONFLICT (club_id, day_of_week) DO NOTHING;

-- Club 2: Tennis Club La Marsa (07:00-22:00 daily)
INSERT INTO opening_hours (club_id, day_of_week, opens, closes)
SELECT c.id, d.day, TIME '07:00:00', TIME '22:00:00'
FROM clubs c
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS d(day)
WHERE c.name = 'Tennis Club La Marsa'
ON CONFLICT (club_id, day_of_week) DO NOTHING;

-- Club 3: Squash Center Sfax (10:00-21:00 weekdays, 09:00-20:00 weekends)
INSERT INTO opening_hours (club_id, day_of_week, opens, closes)
SELECT c.id, d.day,
    CASE WHEN d.day BETWEEN 1 AND 5 THEN TIME '10:00:00' ELSE TIME '09:00:00' END,
    CASE WHEN d.day BETWEEN 1 AND 5 THEN TIME '21:00:00' ELSE TIME '20:00:00' END
FROM clubs c
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS d(day)
WHERE c.name = 'Squash Center Sfax'
ON CONFLICT (club_id, day_of_week) DO NOTHING;

-- Club 4: Multi-Sport Club Sousse (08:00-22:00 daily)
INSERT INTO opening_hours (club_id, day_of_week, opens, closes)
SELECT c.id, d.day, TIME '08:00:00', TIME '22:00:00'
FROM clubs c
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS d(day)
WHERE c.name = 'Multi-Sport Club Sousse'
ON CONFLICT (club_id, day_of_week) DO NOTHING;

-- Club 5: Padel Paradise Hammamet (09:00-23:00 daily)
INSERT INTO opening_hours (club_id, day_of_week, opens, closes)
SELECT c.id, d.day, TIME '09:00:00', TIME '23:00:00'
FROM clubs c
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) AS d(day)
WHERE c.name = 'Padel Paradise Hammamet'
ON CONFLICT (club_id, day_of_week) DO NOTHING;

-- ============================================================
-- SAMPLE BOOKINGS
-- Using dates relative to today to ensure they work regardless of when migration runs
-- ============================================================

-- Get user and court IDs for sample bookings
DO $$
DECLARE
    v_ahmed_id BIGINT;
    v_sarah_id BIGINT;
    v_karim_id BIGINT;
    v_leila_id BIGINT;
    v_youssef_id BIGINT;
    v_padel_central_id BIGINT;
    v_padel_est_id BIGINT;
    v_tennis_chatrier_id BIGINT;
    v_squash_a_id BIGINT;
    v_padel_sousse_id BIGINT;
    v_today DATE := CURRENT_DATE;
BEGIN
    -- Get user IDs
    SELECT id INTO v_ahmed_id FROM users WHERE email = 'ahmed@test.tn';
    SELECT id INTO v_sarah_id FROM users WHERE email = 'sarah@test.tn';
    SELECT id INTO v_karim_id FROM users WHERE email = 'karim@test.tn';
    SELECT id INTO v_leila_id FROM users WHERE email = 'leila@test.tn';
    SELECT id INTO v_youssef_id FROM users WHERE email = 'youssef@test.tn';

    -- Get court IDs
    SELECT id INTO v_padel_central_id FROM courts WHERE name = 'Court Central' AND club_id = (SELECT id FROM clubs WHERE name = 'Padel Club Tunis');
    SELECT id INTO v_padel_est_id FROM courts WHERE name = 'Court Est' AND club_id = (SELECT id FROM clubs WHERE name = 'Padel Club Tunis');
    SELECT id INTO v_tennis_chatrier_id FROM courts WHERE name = 'Court Philippe Chatrier' AND club_id = (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa');
    SELECT id INTO v_squash_a_id FROM courts WHERE name = 'Court A' AND club_id = (SELECT id FROM clubs WHERE name = 'Squash Center Sfax');
    SELECT id INTO v_padel_sousse_id FROM courts WHERE name = 'Padel Court 1' AND club_id = (SELECT id FROM clubs WHERE name = 'Multi-Sport Club Sousse');

    -- Booking 1: Ahmed - Padel Club Tunis, Court Central, tomorrow 10:00-11:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_padel_central_id, v_ahmed_id,
        (v_today + INTERVAL '1 day') + TIME '10:00:00+01',
        (v_today + INTERVAL '1 day') + TIME '11:30:00+01',
        'CONFIRMED',
        45.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 2: Sarah - Padel Club Tunis, Court Central, tomorrow 14:00-15:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_padel_central_id, v_sarah_id,
        (v_today + INTERVAL '1 day') + TIME '14:00:00+01',
        (v_today + INTERVAL '1 day') + TIME '15:30:00+01',
        'CONFIRMED',
        45.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 3: Karim - Tennis Club La Marsa, Court Chatrier, day after tomorrow 10:00-11:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_tennis_chatrier_id, v_karim_id,
        (v_today + INTERVAL '2 days') + TIME '10:00:00+01',
        (v_today + INTERVAL '2 days') + TIME '11:30:00+01',
        'CONFIRMED',
        55.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 4: Leila - Squash Center Sfax, Court A, today 18:00-18:45 (HOLD - expires in 10 min)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price, hold_expires_at)
    VALUES (
        v_squash_a_id, v_leila_id,
        v_today + TIME '18:00:00+01',
        v_today + TIME '18:45:00+01',
        'HOLD',
        25.000,
        NOW() + INTERVAL '10 minutes'
    ) ON CONFLICT DO NOTHING;

    -- Booking 5: Youssef - Padel Club Tunis, Court Est, tomorrow 18:00-19:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_padel_est_id, v_youssef_id,
        (v_today + INTERVAL '1 day') + TIME '18:00:00+01',
        (v_today + INTERVAL '1 day') + TIME '19:30:00+01',
        'CONFIRMED',
        40.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 6: Ahmed - Padel Club Sousse, tomorrow 16:00-17:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_padel_sousse_id, v_ahmed_id,
        (v_today + INTERVAL '1 day') + TIME '16:00:00+01',
        (v_today + INTERVAL '1 day') + TIME '17:30:00+01',
        'CONFIRMED',
        35.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 7: Sarah - Padel Club Tunis, Court Central, 3 days from now 10:00-11:30 (CANCELLED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        v_padel_central_id, v_sarah_id,
        (v_today + INTERVAL '3 days') + TIME '10:00:00+01',
        (v_today + INTERVAL '3 days') + TIME '11:30:00+01',
        'CANCELLED',
        45.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 8: Karim - Tennis La Marsa, Court 3, 5 days from now 15:00-16:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        (SELECT id FROM courts WHERE name = 'Court 3' AND club_id = (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa')),
        v_karim_id,
        (v_today + INTERVAL '5 days') + TIME '15:00:00+01',
        (v_today + INTERVAL '5 days') + TIME '16:30:00+01',
        'CONFIRMED',
        45.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 9: Leila - Padel Paradise Hammamet, Sunset Court, 2 days from now 17:00-18:30 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        (SELECT id FROM courts WHERE name = 'Sunset Court' AND club_id = (SELECT id FROM clubs WHERE name = 'Padel Paradise Hammamet')),
        v_leila_id,
        (v_today + INTERVAL '2 days') + TIME '17:00:00+01',
        (v_today + INTERVAL '2 days') + TIME '18:30:00+01',
        'CONFIRMED',
        50.000
    ) ON CONFLICT DO NOTHING;

    -- Booking 10: Youssef - Tennis La Marsa, Court 4, 4 days from now 09:00-10:00 (CONFIRMED)
    INSERT INTO bookings (court_id, user_id, start_time, end_time, status, price)
    VALUES (
        (SELECT id FROM courts WHERE name = 'Court 4' AND club_id = (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa')),
        v_youssef_id,
        (v_today + INTERVAL '4 days') + TIME '09:00:00+01',
        (v_today + INTERVAL '4 days') + TIME '10:00:00+01',
        'CONFIRMED',
        35.000
    ) ON CONFLICT DO NOTHING;

END $$;

-- ============================================================
-- SAMPLE WAITLIST ENTRIES
-- ============================================================

DO $$
DECLARE
    v_ahmed_id BIGINT;
    v_karim_id BIGINT;
    v_padel_central_id BIGINT;
    v_tennis_chatrier_id BIGINT;
    v_today DATE := CURRENT_DATE;
BEGIN
    SELECT id INTO v_ahmed_id FROM users WHERE email = 'ahmed@test.tn';
    SELECT id INTO v_karim_id FROM users WHERE email = 'karim@test.tn';
    SELECT id INTO v_padel_central_id FROM courts WHERE name = 'Court Central' AND club_id = (SELECT id FROM clubs WHERE name = 'Padel Club Tunis');
    SELECT id INTO v_tennis_chatrier_id FROM courts WHERE name = 'Court Philippe Chatrier' AND club_id = (SELECT id FROM clubs WHERE name = 'Tennis Club La Marsa');

    -- Waitlist 1: Ahmed waiting for Padel Central tomorrow 11:30-13:00 (already booked by Sarah)
    INSERT INTO waitlist_entries (court_id, user_id, start_time, end_time, active)
    VALUES (
        v_padel_central_id, v_ahmed_id,
        (v_today + INTERVAL '1 day') + TIME '11:30:00+01',
        (v_today + INTERVAL '1 day') + TIME '13:00:00+01',
        true
    ) ON CONFLICT DO NOTHING;

    -- Waitlist 2: Karim waiting for Tennis Chatrier day after tomorrow 11:30-13:00 (booked by himself at 10:00, but wants later)
    INSERT INTO waitlist_entries (court_id, user_id, start_time, end_time, active)
    VALUES (
        v_tennis_chatrier_id, v_karim_id,
        (v_today + INTERVAL '2 days') + TIME '11:30:00+01',
        (v_today + INTERVAL '2 days') + TIME '13:00:00+01',
        true
    ) ON CONFLICT DO NOTHING;

END $$;

-- ============================================================
-- REFRESH TOKENS (sample - normally created at login)
-- ============================================================
-- These are just placeholders; real tokens are generated at runtime.
-- The hash is SHA-256 of "sample-refresh-token-" || user_id
-- ============================================================
-- No sample refresh tokens needed - created at runtime.

-- ============================================================
-- END OF SAMPLE DATA
-- ============================================================