#!/bin/sh
set -eu
# No shell tracing, echo-all or SQL error statement logging: credentials stay out of logs.
psql -X --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
\getenv migration_password DB_MIGRATION_PASSWORD
\getenv runtime_password DB_RUNTIME_PASSWORD
CREATE ROLE fiscuvia_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD :'migration_password';
CREATE ROLE fiscuvia_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD :'runtime_password';
ALTER DATABASE fiscuvia_local OWNER TO fiscuvia_migrator;
REVOKE ALL ON DATABASE fiscuvia_local FROM PUBLIC;
GRANT CONNECT ON DATABASE fiscuvia_local TO fiscuvia_runtime;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
