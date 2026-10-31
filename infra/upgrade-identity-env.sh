#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")"
[ -f .env ] || { printf '%s\n' 'Generate infra/.env first.' >&2; exit 1; }
for name in DB_SESSION_PASSWORD FISCUVIA_ACTOR_PROOF_KEY; do
  if ! grep -q "^${name}=.\+$" .env; then
    # Refuse ambiguous blank entries; edit/remove the blank example locally first.
    if grep -q "^${name}=" .env; then printf '%s\n' 'Remove the blank identity variable before upgrading.' >&2; exit 1; fi
    value=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
    [ "${#value}" -eq 64 ] || exit 1
    printf '\n%s=%s\n' "$name" "$value" >> .env
  fi
done
printf '%s\n' 'Private identity settings prepared; existing values preserved.'
