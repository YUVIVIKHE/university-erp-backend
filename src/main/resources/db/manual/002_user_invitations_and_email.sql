-- Additive PostgreSQL migration. Review and apply before deploying the new backend.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS invitation_pending boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;
-- Fails safely if existing emails differ only by case; resolve those records first.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_normalized ON users (lower(email));
CREATE TABLE IF NOT EXISTS email_settings (
    id bigint PRIMARY KEY, host varchar(255) NOT NULL, port integer NOT NULL,
    username varchar(255) NOT NULL, password_encrypted text NOT NULL,
    sender_name varchar(255) NOT NULL, sender_email varchar(255) NOT NULL,
    reply_to varchar(255), security varchar(255) NOT NULL,
    public_url varchar(255) NOT NULL, enabled boolean NOT NULL,
    updated_at timestamptz, version bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS user_invitations (
    id bigserial PRIMARY KEY, user_id bigint NOT NULL UNIQUE REFERENCES users(id),
    token_hash varchar(64) NOT NULL UNIQUE, expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL, used_at timestamptz, revoked_at timestamptz,
    delivery varchar(255) NOT NULL, delivery_message varchar(255)
);
CREATE TABLE IF NOT EXISTS email_deliveries (
    id bigserial PRIMARY KEY, recipient varchar(255) NOT NULL, purpose varchar(255) NOT NULL,
    sent boolean NOT NULL, message varchar(255) NOT NULL, created_at timestamptz NOT NULL
);
COMMIT;
