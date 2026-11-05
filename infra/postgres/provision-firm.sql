-- INTERNAL ONLY. Run with the separate migrator credential after independent approval of
-- an exact, already OIDC-verified internal owner. Never expose this script via HTTP.
-- command_id is durable across retries; approval_reference identifies the approved admin request.
\set ON_ERROR_STOP on
BEGIN;
SELECT tenant_private.provision(:'command_id'::uuid,:'verified_owner_id'::uuid,:'approval_reference'::uuid,
 :'commercial_name',NULLIF(:'contact_email',''),NULLIF(:'phone',''),:'time_zone','es-MX') AS tenant_id;
COMMIT;
