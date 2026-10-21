-- One-time setup of umami's own database role (prod). Idempotent: safe to run again, e.g. to
-- change the password. Run it as the main Postgres superuser (DB_USERNAME) from the repo root:
--
--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db \
--     psql -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 \
--       -v umami_user="$UMAMI_DB_USER" -v umami_password="$UMAMI_DB_PASSWORD" < docs/umami-db.sql
--
-- What it does: creates the role (or resets its password), creates the 'umami' database if it
-- doesn't exist yet, makes the role its owner and hands it every existing umami table (an
-- earlier deploy ran umami as the superuser), and stops other roles from connecting to the
-- app database. The role is not a superuser and cannot read the betting data.

SELECT format('CREATE ROLE %I LOGIN', :'umami_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'umami_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L',
              :'umami_user', :'umami_password') \gexec

SELECT 'CREATE DATABASE umami'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'umami') \gexec
SELECT format('ALTER DATABASE umami OWNER TO %I', :'umami_user') \gexec
REVOKE ALL ON DATABASE umami FROM PUBLIC;
-- The app's tables live in the 'postgres' database; only the superuser needs to connect there.
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;

\connect umami
SELECT format('ALTER SCHEMA public OWNER TO %I', :'umami_user') \gexec
SELECT format('ALTER TABLE public.%I OWNER TO %I', tablename, :'umami_user')
FROM pg_tables WHERE schemaname = 'public' \gexec
SELECT format('ALTER TYPE public.%I OWNER TO %I', t.typname, :'umami_user')
FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
WHERE n.nspname = 'public' AND t.typtype = 'e' \gexec
