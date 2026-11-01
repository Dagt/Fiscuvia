CREATE SCHEMA journal_fixture;
REVOKE ALL ON SCHEMA journal_fixture FROM PUBLIC;
GRANT USAGE ON SCHEMA journal_fixture TO fiscuvia_runtime;
CREATE TABLE journal_fixture.counters(tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id),id uuid NOT NULL,value integer NOT NULL DEFAULT 0,PRIMARY KEY(tenant_id,id));
ALTER TABLE journal_fixture.counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_fixture.counters FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON journal_fixture.counters AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY fixture_access ON journal_fixture.counters TO fiscuvia_runtime,fiscuvia_migrator USING(true) WITH CHECK(true);
GRANT SELECT,UPDATE ON journal_fixture.counters TO fiscuvia_runtime;
CREATE TABLE journal_fixture.receipts(event_id uuid PRIMARY KEY);
REVOKE ALL ON journal_fixture.receipts FROM PUBLIC;
CREATE FUNCTION journal_fixture.fail_insert() RETURNS trigger LANGUAGE plpgsql AS $$
 BEGIN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='SYNTHETIC_JOURNAL_FAILURE'; END; $$;
