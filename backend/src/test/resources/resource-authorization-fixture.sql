-- Synthetic only, never a Flyway location or application resource.
CREATE SCHEMA resource_fixture;
REVOKE ALL ON SCHEMA resource_fixture FROM PUBLIC;
GRANT USAGE ON SCHEMA resource_fixture TO fiscuvia_runtime;
CREATE TABLE resource_fixture.items (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), id uuid NOT NULL,
 kind varchar(10) NOT NULL, label text NOT NULL, state text NOT NULL DEFAULT 'ACTIVE', version bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,id)
);
ALTER TABLE resource_fixture.items ENABLE ROW LEVEL SECURITY;
ALTER TABLE resource_fixture.items FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_fixture ON resource_fixture.items TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY tenant_fence ON resource_fixture.items AS RESTRICTIVE TO fiscuvia_runtime
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON resource_fixture.items AS RESTRICTIVE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_VIEW')
 OR access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT')
 OR access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EXPORT')
 OR access_control.resource_allowed(tenant_id,kind,id,'DOCUMENT_DOWNLOAD')
 OR access_control.resource_allowed(tenant_id,kind,id,'REPORT_EXPORT'))
 WITH CHECK(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT'));
CREATE POLICY read_fixture ON resource_fixture.items FOR SELECT TO fiscuvia_runtime USING(true);
CREATE POLICY update_fixture ON resource_fixture.items FOR UPDATE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT'))
 WITH CHECK(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT'));
CREATE POLICY delete_fixture ON resource_fixture.items FOR DELETE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT'));
GRANT SELECT,UPDATE,DELETE ON resource_fixture.items TO fiscuvia_runtime;
-- Synthetic approvals only in this disposable test database. Not product permissions.
INSERT INTO access_control_private.role_capabilities VALUES
 ('ACCOUNTANT','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY'),
 ('ACCOUNTANT','TAXPAYER_EDIT','TAXPAYER',false,'SYNTHETIC TEST ONLY'),
 ('ACCOUNTANT','TAXPAYER_EXPORT','TAXPAYER',false,'SYNTHETIC TEST ONLY'),
 ('ACCOUNTANT','DOCUMENT_DOWNLOAD','DOCUMENT',false,'SYNTHETIC TEST ONLY'),
 ('ACCOUNTANT','REPORT_EXPORT','REPORT',false,'SYNTHETIC TEST ONLY'),
 ('FIRM_SUPERVISOR','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY') ON CONFLICT DO NOTHING;
