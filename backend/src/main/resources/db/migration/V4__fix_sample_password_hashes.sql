-- Repairs the seeded password hashes.
--
-- V3 shipped the hash below:
--   $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa
-- It does not correspond to the documented password "secret123", so every seeded
-- account failed to log in on a fresh database. V3 is left untouched because
-- Flyway checksums applied migrations: editing it would break every environment
-- that already ran V3. The repair therefore lives here instead.
--
-- The correct BCrypt hash for "secret123" is:
--   $2a$10$92aUW1Je66O7UVPpwuAD2uaMld6pAeKqEtyLApL415WYt65mAdBmO
--
-- Keyed on the seeded email addresses rather than a blanket UPDATE, so a real
-- user who happens to share one of these test addresses is left alone and any
-- account whose password was changed since seeding is not silently reset.
UPDATE users
SET password_hash = '$2a$10$92aUW1Je66O7UVPpwuAD2uaMld6pAeKqEtyLApL415WYt65mAdBmO'
WHERE email IN (
    'admin@courtside.tn',
    'manager1@courtside.tn',
    'manager2@courtside.tn',
    'ahmed@test.tn',
    'sarah@test.tn',
    'karim@test.tn',
    'leila@test.tn',
    'youssef@test.tn'
)
  AND password_hash = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa';