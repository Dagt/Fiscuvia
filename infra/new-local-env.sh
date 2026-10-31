#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")"
if [ -e .env ]; then
  printf '%s\n' 'Refusing to replace existing infra/.env.' >&2
  exit 1
fi
new_password() {
  random_value=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
  [ "${#random_value}" -eq 64 ] || exit 1
  printf '%s' "$random_value"
}
bootstrap_password=$(new_password)
migration_password=$(new_password)
runtime_password=$(new_password)
# noclobber prevents replacing credentials for an existing development volume.
set -C
{
  printf 'DB_PORT=55433\n'
  printf 'DB_BOOTSTRAP_PASSWORD=%s\n' "$bootstrap_password"
  printf 'DB_MIGRATION_PASSWORD=%s\n' "$migration_password"
  printf 'DB_RUNTIME_PASSWORD=%s\n' "$runtime_password"
  printf 'DB_SESSION_PASSWORD=%s\n' "$(new_password)"
  printf 'FISCUVIA_ACTOR_PROOF_KEY=%s\n' "$(new_password)"
} > .env || exit 1
printf '%s\n' 'Created infra/.env with synthetic local credentials. Keep this ignored file private.'
