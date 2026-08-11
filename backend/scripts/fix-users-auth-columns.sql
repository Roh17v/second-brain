-- One-shot fix when Hibernate fails with:
--   alter table users add column email_verified boolean not null
--   ERROR: column "email_verified" of relation "users" contains null values
--
-- Run against your app DB (example):
--   psql -h localhost -U postgres -d secondbrain -f scripts/fix-users-auth-columns.sql
--
-- Existing password users are treated as already verified so they can keep signing in.
-- New email signups still start as unverified via the app.

-- ---- email_verified ----
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified boolean;
UPDATE users SET email_verified = true WHERE email_verified IS NULL;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT false;
ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;

-- ---- OTP fields ----
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_hash varchar(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_expires_at timestamp with time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_last_sent_at timestamp with time zone;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_attempts integer;
UPDATE users SET email_otp_attempts = 0 WHERE email_otp_attempts IS NULL;
ALTER TABLE users ALTER COLUMN email_otp_attempts SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN email_otp_attempts SET NOT NULL;

-- ---- Google sign-in ----
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id varchar(255);
-- Partial unique index: many NULL google_ids are allowed; non-null values unique
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_google_id ON users (google_id)
  WHERE google_id IS NOT NULL;

-- Google-only accounts need nullable password_hash
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
