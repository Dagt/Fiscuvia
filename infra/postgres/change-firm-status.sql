-- INTERNAL ONLY. Separate migrator credential and recorded administrative approval required.
-- No billing suspension, public reactivation, deletion or fiscal access is granted.
\set ON_ERROR_STOP on
BEGIN;
SELECT tenant_private.change_status(:'tenant_id'::uuid,:'command_id'::uuid,:'approval_reference'::uuid,
 :'expected_version'::bigint,:'status') AS version;
COMMIT;
