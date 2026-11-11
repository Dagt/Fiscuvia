-- Administrative connection only. UUIDs are references, never credentials/authority.
-- Independently verify approval and tenant outside the browser; protect the migrator credential.
\set ON_ERROR_STOP on
BEGIN;
SELECT entitlement_private.assign_limit(:'tenant_id'::uuid, :'command_id'::uuid,
 :'approval_reference'::uuid, :'resource', :'limit_value'::bigint,
 :'expected_version'::bigint, NULLIF(:'valid_until','')::timestamptz);
COMMIT;
