-- #28 infrastructure only. No taxpayers/documents or role hierarchy. V1/V2/V3 immutable.
CREATE SCHEMA access_control AUTHORIZATION CURRENT_USER;
CREATE SCHEMA access_control_private AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA access_control,access_control_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT USAGE ON SCHEMA access_control TO fiscuvia_runtime;

-- Global policy metadata, writable only by the migrator under reviewed administrative change.
CREATE TABLE access_control_private.role_capabilities (
    role_code varchar(20) NOT NULL CHECK(role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')),
    capability varchar(32) NOT NULL CHECK(capability IN ('TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW',
      'MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE','TAXPAYER_VIEW','TAXPAYER_CREATE',
      'TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW')),
    resource_kind varchar(10) NOT NULL CHECK(resource_kind IN ('TENANT','TAXPAYER','DOCUMENT','REPORT','AUDIT')),
    requires_mfa boolean NOT NULL,
    approval_reference text NOT NULL CHECK(length(approval_reference) BETWEEN 1 AND 200),
    PRIMARY KEY(role_code,capability,resource_kind)
);
-- Only behavior explicitly approved/implemented in #27. Fiscal combinations remain absent/DENY.
INSERT INTO access_control_private.role_capabilities
 SELECT r,'TENANT_READ','TENANT',false,'Owner authorization #27 / PR #83'
 FROM unnest(ARRAY['FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT']) r;
INSERT INTO access_control_private.role_capabilities VALUES
 ('FIRM_OWNER_ADMIN','MEMBERSHIP_INVITE','TENANT',true,'Owner authorization #27 / PR #83'),
 ('FIRM_OWNER_ADMIN','MEMBERSHIP_DISABLE','TENANT',true,'Owner authorization #27 / PR #83');

-- Generic explicit per-membership/per-capability assignments, not a fiscal entity registry.
-- Future domain tables must add concrete composite references/adapters after #74 approval.
CREATE TABLE access_control.resource_grants (
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    resource_kind varchar(10) NOT NULL CHECK(resource_kind IN ('TAXPAYER','DOCUMENT','REPORT','AUDIT')),
    resource_id uuid NOT NULL,
    capability varchar(32) NOT NULL CHECK(capability IN ('TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN',
      'TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW')),
    active boolean NOT NULL DEFAULT true,
    approval_reference uuid NOT NULL,
    PRIMARY KEY(tenant_id,membership_id,resource_kind,capability,resource_id),
    FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX resource_grants_target ON access_control.resource_grants(tenant_id,resource_kind,resource_id,membership_id);
ALTER TABLE access_control.resource_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE access_control.resource_grants FORCE ROW LEVEL SECURITY;
CREATE POLICY grants_owner ON access_control.resource_grants TO fiscuvia_migrator
    USING (tenant_id=app.current_tenant_id() OR EXISTS(SELECT 1 FROM identity.memberships m
      WHERE m.tenant_id=resource_grants.tenant_id AND m.id=resource_grants.membership_id AND m.user_id=identity.current_actor_id()))
    WITH CHECK(tenant_id=app.current_tenant_id());
-- Runtime has no direct grant-table privileges. Even future permissive policies cannot open it.
CREATE POLICY grants_runtime_fence ON access_control.resource_grants AS RESTRICTIVE TO fiscuvia_runtime
    USING(false) WITH CHECK(false);

CREATE TABLE access_control_private.operation_context (
    transaction_id xid8 PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES identity_private.users(id),
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    capability varchar(32) NOT NULL,
    resource_kind varchar(10) NOT NULL,
    resource_id uuid,
    authorized_resources uuid[] NOT NULL,
    FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id)
);
REVOKE ALL ON ALL TABLES IN SCHEMA access_control,access_control_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;

-- Before opening RLS context: locks identity (#27), membership, internal role, approved policy,
-- and matching assignments. Concurrent revocation either wins before this check or waits for
-- completion. No authorize-then-commit-then-use window; no claims/headers/actor parameters.
CREATE FUNCTION access_control.authorize_operation(p_tenant uuid,p_capability text,p_kind text,p_resource uuid)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id(); member uuid; mfa bigint; needs_mfa boolean; tx xid8:=pg_current_xact_id(); resources uuid[];
BEGIN
    IF actor IS NULL OR p_tenant IS NULL OR p_capability IS NULL OR p_kind IS NULL THEN RETURN 'CONTEXT_REJECTED'; END IF;
    SELECT id INTO member FROM identity.memberships WHERE tenant_id=p_tenant AND user_id=actor AND status='ACTIVE' FOR SHARE;
    IF member IS NULL THEN RETURN 'MEMBERSHIP_INACTIVE'; END IF;
    PERFORM identity.authorize_tenant(p_tenant,false);
    SELECT p.requires_mfa INTO needs_mfa FROM identity.membership_roles r
      JOIN access_control_private.role_capabilities p ON p.role_code=r.role_code
      WHERE r.tenant_id=p_tenant AND r.membership_id=member AND p.capability=p_capability AND p.resource_kind=p_kind
      ORDER BY p.requires_mfa,r.role_code LIMIT 1 FOR SHARE OF r,p;
    IF NOT FOUND THEN RETURN 'CAPABILITY_UNCONFIGURED'; END IF;
    SELECT mfa_at INTO mfa FROM identity_private.actor_context WHERE transaction_id=tx;
    IF needs_mfa AND mfa NOT BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30
      THEN RETURN 'MFA_REQUIRED'; END IF;
    IF p_kind='TENANT' AND p_resource IS NOT NULL THEN RETURN 'CONTEXT_REJECTED'; END IF;
    IF p_kind<>'TENANT' THEN
      SELECT array_agg(locked.resource_id ORDER BY locked.resource_id) INTO resources FROM (
        SELECT g.resource_id FROM access_control.resource_grants g WHERE g.tenant_id=p_tenant AND g.membership_id=member
          AND g.resource_kind=p_kind AND g.capability=p_capability AND g.active
          AND (p_resource IS NULL OR g.resource_id=p_resource) ORDER BY g.resource_id FOR SHARE) locked;
      IF resources IS NULL AND p_resource IS NOT NULL THEN RETURN 'RESOURCE_UNASSIGNED'; END IF;
    END IF;
    DELETE FROM access_control_private.operation_context WHERE transaction_id IN (
      SELECT transaction_id FROM access_control_private.operation_context WHERE transaction_id<>tx LIMIT 100 FOR UPDATE SKIP LOCKED);
    -- Freeze the row set too: grants inserted after this check cannot expand this operation.
    INSERT INTO access_control_private.operation_context VALUES(tx,actor,p_tenant,member,p_capability,p_kind,p_resource,COALESCE(resources,'{}'::uuid[]))
      ON CONFLICT DO NOTHING;
    IF NOT EXISTS(SELECT 1 FROM access_control_private.operation_context c WHERE c.transaction_id=tx AND c.actor_id=actor
      AND c.tenant_id=p_tenant AND c.capability=p_capability AND c.resource_kind=p_kind AND c.resource_id IS NOT DISTINCT FROM p_resource
      AND c.authorized_resources=COALESCE(resources,'{}'::uuid[]))
      THEN RETURN 'CONTEXT_REJECTED'; END IF;
    RETURN 'AUTHORIZED';
END;
$$;

-- Mandatory RESTRICTIVE resource fence on future protected domain tables, in addition to tenant_fence.
-- Call with a server-owned operation capability, NEVER a GUC/client value or a VIEW fallback for EDIT.
CREATE FUNCTION access_control.resource_allowed(p_tenant uuid,p_kind text,p_resource uuid,p_capability text)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
    SELECT EXISTS(SELECT 1 FROM access_control_private.operation_context c JOIN access_control.resource_grants g
      ON g.tenant_id=c.tenant_id AND g.membership_id=c.membership_id AND g.resource_kind=c.resource_kind AND g.capability=c.capability
      WHERE c.transaction_id=pg_current_xact_id_if_assigned() AND c.actor_id=identity.current_actor_id()
        AND c.tenant_id=app.current_tenant_id() AND c.tenant_id=p_tenant AND c.resource_kind=p_kind AND c.capability=p_capability
        AND (c.resource_id IS NULL OR c.resource_id=p_resource) AND p_resource=ANY(c.authorized_resources)
        AND g.resource_id=p_resource AND g.active)
$$;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA access_control FROM PUBLIC;
GRANT EXECUTE ON FUNCTION access_control.authorize_operation(uuid,text,text,uuid),
  access_control.resource_allowed(uuid,text,uuid,text) TO fiscuvia_runtime;

-- Close direct-SQL bypass of the #27 administrative commands too. Preserve their original
-- checks/bodies privately; public wrappers require the exact locked #28 capability first.
ALTER FUNCTION identity.create_invitation(uuid,text,text,text,bytea,timestamptz) SET SCHEMA identity_private;
ALTER FUNCTION identity.revoke_invitation(uuid,uuid) SET SCHEMA identity_private;
ALTER FUNCTION identity.change_membership(uuid,uuid,text) SET SCHEMA identity_private;
REVOKE ALL ON FUNCTION identity_private.create_invitation(uuid,text,text,text,bytea,timestamptz),
  identity_private.revoke_invitation(uuid,uuid),identity_private.change_membership(uuid,uuid,text) FROM PUBLIC,fiscuvia_runtime;
CREATE FUNCTION access_control_private.require_tenant_capability(p_tenant uuid,p_capability text) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM access_control_private.operation_context c WHERE c.transaction_id=pg_current_xact_id_if_assigned()
      AND c.actor_id=identity.current_actor_id() AND c.tenant_id=p_tenant AND c.tenant_id=app.current_tenant_id()
      AND c.resource_kind='TENANT' AND c.resource_id IS NULL AND c.capability=p_capability) THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED';
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION access_control_private.require_tenant_capability(uuid,text) FROM PUBLIC;
CREATE FUNCTION identity.create_invitation(p_tenant uuid,p_subject text,p_issuer text,p_role text,p_hash bytea,p_expires timestamptz)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM access_control_private.require_tenant_capability(p_tenant,'MEMBERSHIP_INVITE');
    RETURN identity_private.create_invitation(p_tenant,p_subject,p_issuer,p_role,p_hash,p_expires);
END;
$$;
CREATE FUNCTION identity.revoke_invitation(p_tenant uuid,p_id uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM access_control_private.require_tenant_capability(p_tenant,'MEMBERSHIP_INVITE');
    RETURN identity_private.revoke_invitation(p_tenant,p_id);
END;
$$;
CREATE FUNCTION identity.change_membership(p_tenant uuid,p_id uuid,p_status text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM access_control_private.require_tenant_capability(p_tenant,'MEMBERSHIP_DISABLE');
    RETURN identity_private.change_membership(p_tenant,p_id,p_status);
END;
$$;
REVOKE ALL ON FUNCTION identity.create_invitation(uuid,text,text,text,bytea,timestamptz),identity.revoke_invitation(uuid,uuid),
  identity.change_membership(uuid,uuid,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity.create_invitation(uuid,text,text,text,bytea,timestamptz),identity.revoke_invitation(uuid,uuid),
  identity.change_membership(uuid,uuid,text) TO fiscuvia_runtime;
