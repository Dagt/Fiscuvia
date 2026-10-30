-- #26 infrastructure only. No fiscal tables, identity model, provisioning or seeded tenants.
-- This private registry binds one tenant to one real PostgreSQL transaction. GUCs alone are mutable.
CREATE TABLE infrastructure.rls_transaction_context (
    transaction_id xid8 PRIMARY KEY,
    tenant_id uuid NOT NULL
);
REVOKE ALL ON infrastructure.rls_transaction_context FROM PUBLIC, fiscuvia_runtime;
ALTER TABLE infrastructure.rls_transaction_context ENABLE ROW LEVEL SECURITY;
ALTER TABLE infrastructure.rls_transaction_context FORCE ROW LEVEL SECURITY;
CREATE POLICY context_owner ON infrastructure.rls_transaction_context TO fiscuvia_migrator
    USING (true) WITH CHECK (true);
COMMENT ON TABLE infrastructure.rls_transaction_context IS
    'Private platform metadata, not tenant business data. Effective only in its originating transaction. No runtime table access.';

CREATE FUNCTION app.establish_tenant_context(requested_tenant uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    current_transaction xid8 := pg_current_xact_id();
    bound_tenant uuid;
BEGIN
    IF requested_tenant IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'TENANT_CONTEXT_REJECTED';
    END IF;

    -- Other uncommitted bindings are invisible. Never wait on another transaction's cleanup.
    -- Bounded opportunistic cleanup; committed rows confer no access in a subsequent transaction.
    DELETE FROM infrastructure.rls_transaction_context
    WHERE transaction_id IN (
        SELECT transaction_id FROM infrastructure.rls_transaction_context
        WHERE transaction_id <> current_transaction
        LIMIT 100 FOR UPDATE SKIP LOCKED
    );
    INSERT INTO infrastructure.rls_transaction_context(transaction_id, tenant_id)
        VALUES (current_transaction, requested_tenant) ON CONFLICT DO NOTHING;
    SELECT tenant_id INTO STRICT bound_tenant FROM infrastructure.rls_transaction_context
        WHERE transaction_id = current_transaction;
    IF bound_tenant <> requested_tenant THEN
        RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'TENANT_CONTEXT_REJECTED';
    END IF;
    PERFORM set_config('app.current_tenant_id', requested_tenant::text, true);
    PERFORM set_config('app.current_tenant_transaction', current_transaction::text, true);
END;
$$;

CREATE FUNCTION app.current_tenant_id() RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT tenant_id FROM infrastructure.rls_transaction_context
    WHERE transaction_id = pg_current_xact_id_if_assigned()
      AND tenant_id::text = NULLIF(current_setting('app.current_tenant_id', true), '')
      AND transaction_id::text = current_setting('app.current_tenant_transaction', true)
$$;
REVOKE ALL ON FUNCTION app.establish_tenant_context(uuid), app.current_tenant_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.establish_tenant_context(uuid), app.current_tenant_id()
    TO fiscuvia_runtime, fiscuvia_migrator;

-- Root of tenant-scoped FKs. Business settings, identity and memberships require later issues.
CREATE TABLE app.tenants (
    tenant_id uuid NOT NULL PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
REVOKE ALL ON app.tenants FROM PUBLIC, fiscuvia_runtime;
GRANT SELECT ON app.tenants TO fiscuvia_runtime;
ALTER TABLE app.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.tenants FORCE ROW LEVEL SECURITY;

-- Restrictive fence ANDs with every permissive policy, including future additions.
CREATE POLICY tenant_fence ON app.tenants AS RESTRICTIVE
    TO fiscuvia_runtime, fiscuvia_migrator
    USING (tenant_id = (SELECT app.current_tenant_id()))
    WITH CHECK (tenant_id = (SELECT app.current_tenant_id()));
CREATE POLICY tenant_select ON app.tenants FOR SELECT TO fiscuvia_runtime, fiscuvia_migrator USING (true);
CREATE POLICY tenant_insert ON app.tenants FOR INSERT TO fiscuvia_migrator WITH CHECK (true);
CREATE POLICY tenant_update ON app.tenants FOR UPDATE TO fiscuvia_migrator USING (true) WITH CHECK (true);
CREATE POLICY tenant_delete ON app.tenants FOR DELETE TO fiscuvia_migrator USING (true);
COMMENT ON TABLE app.tenants IS 'Minimal dispatch identity root (#26). No runtime provisioning or OIDC/membership assumptions.';
COMMENT ON SCHEMA app IS 'Fiscuvia tenancy infrastructure. Fiscal/domain schema remains gated by #74.';
