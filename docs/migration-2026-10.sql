-- Schema changes for the October 2026 update (tests, settlement, sync and security fixes).
-- Prod runs with DDL_AUTO=validate, so apply this BEFORE starting the new backend
-- (alternatively boot once with DDL_AUTO=update). Idempotent: safe to run more than once.
--
--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db \
--     psql -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 < docs/migration-2026-10.sql

-- Tokens issued before a password reset are rejected (JwtService "ver" claim)
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;

-- Admin overrides no longer freeze synced matches; optimistic locking for sync vs admin edits
ALTER TABLE matches ADD COLUMN IF NOT EXISTS status_overridden boolean NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS time_overridden boolean NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE market_offers ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

-- The shedlock table and the new metrics view are created by schema.sql on startup.
