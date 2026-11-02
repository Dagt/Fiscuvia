-- #30 disposable adversarial resources, NOT a fiscal migration or a production module.
CREATE SCHEMA threat_fixture;
REVOKE ALL ON SCHEMA threat_fixture FROM PUBLIC;
GRANT USAGE ON SCHEMA threat_fixture TO fiscuvia_runtime;
CREATE TABLE threat_fixture.resources (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), id uuid NOT NULL,
 synthetic_rfc text NOT NULL, label text NOT NULL DEFAULT 'DEMO fixture', version bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,synthetic_rfc)
);
CREATE TABLE threat_fixture.links (
 tenant_id uuid NOT NULL, id uuid NOT NULL, resource_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,id), FOREIGN KEY(tenant_id,resource_id) REFERENCES threat_fixture.resources(tenant_id,id)
);
ALTER TABLE threat_fixture.resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE threat_fixture.resources FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_fixture ON threat_fixture.resources TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY tenant_fence ON threat_fixture.resources AS RESTRICTIVE TO fiscuvia_runtime
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON threat_fixture.resources AS RESTRICTIVE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_VIEW')
 OR access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EDIT')
 OR access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EXPORT'))
 WITH CHECK(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EDIT')
 OR access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_CREATE'));
CREATE POLICY read_fixture ON threat_fixture.resources FOR SELECT TO fiscuvia_runtime USING(true);
CREATE POLICY edit_fixture ON threat_fixture.resources FOR UPDATE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EDIT'))
 WITH CHECK(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EDIT'));
CREATE POLICY delete_fixture ON threat_fixture.resources FOR DELETE TO fiscuvia_runtime
 USING(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_EDIT'));
CREATE POLICY create_fixture ON threat_fixture.resources FOR INSERT TO fiscuvia_runtime
 WITH CHECK(access_control.resource_allowed(tenant_id,'TAXPAYER',id,'TAXPAYER_CREATE'));
GRANT SELECT,INSERT,UPDATE,DELETE ON threat_fixture.resources TO fiscuvia_runtime;
ALTER TABLE threat_fixture.links ENABLE ROW LEVEL SECURITY;
ALTER TABLE threat_fixture.links FORCE ROW LEVEL SECURITY;
CREATE POLICY links_fence ON threat_fixture.links AS RESTRICTIVE TO fiscuvia_runtime
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY links_insert ON threat_fixture.links FOR INSERT TO fiscuvia_runtime WITH CHECK(true);
CREATE POLICY links_owner ON threat_fixture.links TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
GRANT INSERT ON threat_fixture.links TO fiscuvia_runtime;
CREATE TABLE threat_fixture.receipts(event_id uuid PRIMARY KEY);
REVOKE ALL ON threat_fixture.receipts FROM PUBLIC,fiscuvia_runtime;
INSERT INTO access_control_private.role_capabilities VALUES
 ('ACCOUNTANT','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY #30'),
 ('ACCOUNTANT','TAXPAYER_EDIT','TAXPAYER',false,'SYNTHETIC TEST ONLY #30'),
 ('ACCOUNTANT','TAXPAYER_CREATE','TAXPAYER',false,'SYNTHETIC TEST ONLY #30'),
 ('ACCOUNTANT','TAXPAYER_EXPORT','TAXPAYER',true,'SYNTHETIC TEST ONLY #30'),
 ('FIRM_SUPERVISOR','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY #30');
CREATE FUNCTION threat_fixture.fail_write() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='SYNTHETIC_WRITE_REJECTED'; END; $$;
