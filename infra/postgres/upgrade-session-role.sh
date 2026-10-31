#!/bin/sh
set -eu
# For an existing V2 development volume, after Compose recreates the service with DB_SESSION_PASSWORD.
# Does not reset existing passwords or change the migrator/runtime roles.
psql -X --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
\getenv session_password DB_SESSION_PASSWORD
SELECT format('CREATE ROLE fiscuvia_sessions LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD %L', :'session_password')
WHERE NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fiscuvia_sessions')
\gexec
SELECT NOT(rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolinherit OR rolreplication) AS safe_role FROM pg_roles WHERE rolname='fiscuvia_sessions'
\gset
\if :safe_role
GRANT CONNECT ON DATABASE fiscuvia_local TO fiscuvia_sessions;
\else
\quit 1
\endif
SQL
