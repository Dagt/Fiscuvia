-- Internal administrative procedure. UUIDs are explicit approvals, never caller authentication.
-- Run only with the migrator credential after independently verifying the exact OIDC issuer/subject/user.
\set ON_ERROR_STOP on
BEGIN;
SELECT app.establish_tenant_context(:'tenant_id'::uuid);
INSERT INTO app.tenants(tenant_id) VALUES(:'tenant_id'::uuid) ON CONFLICT DO NOTHING;
SELECT identity_private.bootstrap_owner(:'tenant_id'::uuid,:'user_id'::uuid,:'approval_reference'::uuid);
COMMIT;
