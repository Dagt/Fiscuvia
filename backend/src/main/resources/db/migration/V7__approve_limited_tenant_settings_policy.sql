-- Limited policy explicitly approved by the owner on 2026-11-04:
-- https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209
-- V6 already enforces active identity/membership/tenant, own-tenant context,
-- owner role, trusted MFA <= 10 minutes, closed fields, CSRF at HTTP,
-- optimistic version, idempotency and atomic journal/outbox.
-- No hierarchy, fiscal permission, lifecycle, logo or platform-operator grant.
-- Fail closed on an unexpected pre-existing policy; never overwrite it silently.
INSERT INTO access_control_private.role_capabilities
    (role_code,capability,resource_kind,requires_mfa,approval_reference)
VALUES ('FIRM_OWNER_ADMIN','TENANT_SETTINGS_MANAGE','TENANT',true,
    'https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209');
