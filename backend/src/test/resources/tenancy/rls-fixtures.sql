-- Disposable test schema only. This is NOT a fiscal migration or a production module.
CREATE SCHEMA rls_fixture AUTHORIZATION fiscuvia_migrator;
REVOKE ALL ON SCHEMA rls_fixture FROM PUBLIC;
GRANT USAGE ON SCHEMA rls_fixture TO fiscuvia_runtime;
CREATE TABLE rls_fixture.records (
    tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id),
    id uuid NOT NULL,
    synthetic_rfc text NOT NULL CHECK (synthetic_rfc LIKE 'DEMO%'),
    label text NOT NULL,
    PRIMARY KEY (tenant_id,id),
    UNIQUE (tenant_id,synthetic_rfc)
);
CREATE TABLE rls_fixture.links (
    tenant_id uuid NOT NULL,
    id uuid NOT NULL,
    record_id uuid NOT NULL,
    PRIMARY KEY (tenant_id,id),
    FOREIGN KEY (tenant_id,record_id) REFERENCES rls_fixture.records(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX links_tenant_record_idx ON rls_fixture.links(tenant_id,record_id);
REVOKE ALL ON rls_fixture.records, rls_fixture.links FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON rls_fixture.records, rls_fixture.links TO fiscuvia_runtime;
ALTER TABLE rls_fixture.records ENABLE ROW LEVEL SECURITY;
ALTER TABLE rls_fixture.records FORCE ROW LEVEL SECURITY;
ALTER TABLE rls_fixture.links ENABLE ROW LEVEL SECURITY;
ALTER TABLE rls_fixture.links FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON rls_fixture.records AS RESTRICTIVE TO fiscuvia_runtime, fiscuvia_migrator
    USING (tenant_id = (SELECT app.current_tenant_id())) WITH CHECK (tenant_id = (SELECT app.current_tenant_id()));
CREATE POLICY tenant_select ON rls_fixture.records FOR SELECT TO fiscuvia_runtime, fiscuvia_migrator USING (true);
CREATE POLICY tenant_insert ON rls_fixture.records FOR INSERT TO fiscuvia_runtime, fiscuvia_migrator WITH CHECK (true);
CREATE POLICY tenant_update ON rls_fixture.records FOR UPDATE TO fiscuvia_runtime, fiscuvia_migrator USING (true) WITH CHECK (true);
CREATE POLICY tenant_delete ON rls_fixture.records FOR DELETE TO fiscuvia_runtime, fiscuvia_migrator USING (true);
CREATE POLICY tenant_fence ON rls_fixture.links AS RESTRICTIVE TO fiscuvia_runtime, fiscuvia_migrator
    USING (tenant_id = (SELECT app.current_tenant_id())) WITH CHECK (tenant_id = (SELECT app.current_tenant_id()));
CREATE POLICY tenant_select ON rls_fixture.links FOR SELECT TO fiscuvia_runtime, fiscuvia_migrator USING (true);
CREATE POLICY tenant_insert ON rls_fixture.links FOR INSERT TO fiscuvia_runtime, fiscuvia_migrator WITH CHECK (true);
CREATE POLICY tenant_update ON rls_fixture.links FOR UPDATE TO fiscuvia_runtime, fiscuvia_migrator USING (true) WITH CHECK (true);
CREATE POLICY tenant_delete ON rls_fixture.links FOR DELETE TO fiscuvia_runtime, fiscuvia_migrator USING (true);
