-- #27: private global authentication metadata, scoped memberships and private session storage.
-- V1/V2 remain immutable. No fiscal data or public owner registration.
CREATE SCHEMA identity_private AUTHORIZATION CURRENT_USER;
CREATE SCHEMA identity_crypto AUTHORIZATION CURRENT_USER;
CREATE SCHEMA identity AUTHORIZATION CURRENT_USER;
CREATE SCHEMA auth_sessions AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA identity_private, identity_crypto, identity, auth_sessions FROM PUBLIC;
CREATE EXTENSION pgcrypto WITH SCHEMA identity_crypto;
-- Trusted extension C functions are owned by PostgreSQL's bootstrap role, not the installer.
-- The non-superuser migrator cannot revoke those ACLs. Namespace USAGE is private above;
-- these SECURITY INVOKER crypto primitives have no access to the actor key or tenant tables.
GRANT USAGE ON SCHEMA identity TO fiscuvia_runtime;
GRANT USAGE ON SCHEMA auth_sessions TO fiscuvia_sessions;

CREATE TABLE identity_private.proof_key (
    singleton boolean PRIMARY KEY CHECK (singleton),
    secret bytea NOT NULL CHECK (octet_length(secret)=32)
);
CREATE TABLE identity_private.users (
    id uuid PRIMARY KEY,
    status varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    auth_version bigint NOT NULL DEFAULT 0 CHECK (auth_version>=0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE identity_private.external_identities (
    issuer text NOT NULL CHECK (length(issuer) BETWEEN 1 AND 500 AND issuer !~ E'[\\n\\r]'),
    subject varchar(255) NOT NULL CHECK (length(subject)>0 AND subject !~ E'[\\n\\r]'),
    user_id uuid NOT NULL REFERENCES identity_private.users(id) ON DELETE RESTRICT,
    PRIMARY KEY (issuer,subject),
    UNIQUE (user_id,issuer)
);
CREATE TABLE identity_private.actor_context (
    transaction_id xid8 PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES identity_private.users(id),
    mfa_at bigint NOT NULL,
    tenant_id uuid,
    privileged boolean NOT NULL DEFAULT false
);
CREATE TABLE identity_private.owner_approvals (
    tenant_id uuid PRIMARY KEY REFERENCES app.tenants(tenant_id),
    user_id uuid NOT NULL REFERENCES identity_private.users(id),
    approval_reference uuid NOT NULL,
    approved_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
REVOKE ALL ON ALL TABLES IN SCHEMA identity_private FROM PUBLIC, fiscuvia_runtime, fiscuvia_sessions;

CREATE FUNCTION identity.current_actor_id() RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
    SELECT user_id FROM identity_private.actor_context WHERE transaction_id=pg_current_xact_id_if_assigned()
$$;

CREATE FUNCTION identity.bind_actor(p_issuer text,p_subject text,p_mfa_at bigint,p_proof text)
RETURNS TABLE(user_id uuid,auth_version bigint) LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
DECLARE tx xid8:=pg_current_xact_id(); key bytea; expected bytea; actor uuid; version bigint; bound uuid; bound_mfa bigint;
BEGIN
    IF p_issuer IS NULL OR length(p_issuer) NOT BETWEEN 1 AND 500 OR p_issuer ~ E'[\\n\\r]'
      OR p_subject IS NULL OR length(p_subject) NOT BETWEEN 1 AND 255 OR p_subject ~ E'[\\n\\r]'
      OR p_mfa_at IS NULL OR p_mfa_at<0 OR p_mfa_at>extract(epoch FROM clock_timestamp())::bigint+30
      OR p_proof IS NULL OR p_proof !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='IDENTITY_CONTEXT_REJECTED';
    END IF;
    SELECT secret INTO key FROM identity_private.proof_key WHERE singleton;
    expected:=identity_crypto.hmac(convert_to(p_issuer||E'\n'||p_subject||E'\n'||tx::text||E'\n'||p_mfa_at::text,'UTF8'),key,'sha256');
    IF expected IS NULL OR identity_crypto.digest(expected,'sha256')<>identity_crypto.digest(decode(p_proof,'hex'),'sha256') THEN
        RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='IDENTITY_CONTEXT_REJECTED';
    END IF;
    SELECT e.user_id INTO actor FROM identity_private.external_identities e WHERE e.issuer=p_issuer AND e.subject=p_subject;
    IF actor IS NULL THEN
        PERFORM pg_advisory_xact_lock(hashtextextended(p_issuer||E'\n'||p_subject,271));
        SELECT e.user_id INTO actor FROM identity_private.external_identities e WHERE e.issuer=p_issuer AND e.subject=p_subject;
        IF actor IS NULL THEN
            actor:=gen_random_uuid();
            INSERT INTO identity_private.users(id) VALUES(actor);
            INSERT INTO identity_private.external_identities VALUES(p_issuer,p_subject,actor);
        END IF;
    END IF;
    SELECT u.auth_version INTO version FROM identity_private.users u WHERE u.id=actor AND u.status='ACTIVE' FOR SHARE;
    IF version IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='IDENTITY_CONTEXT_REJECTED'; END IF;
    DELETE FROM identity_private.actor_context WHERE transaction_id IN (
      SELECT transaction_id FROM identity_private.actor_context WHERE transaction_id<>tx LIMIT 100 FOR UPDATE SKIP LOCKED);
    INSERT INTO identity_private.actor_context(transaction_id,user_id,mfa_at) VALUES(tx,actor,p_mfa_at) ON CONFLICT DO NOTHING;
    SELECT c.user_id,c.mfa_at INTO bound,bound_mfa FROM identity_private.actor_context c WHERE c.transaction_id=tx;
    IF bound<>actor OR bound_mfa<>p_mfa_at THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='IDENTITY_CONTEXT_REJECTED'; END IF;
    RETURN QUERY SELECT actor,version;
END;
$$;

CREATE TABLE identity.memberships (
    tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id) ON DELETE RESTRICT,
    id uuid NOT NULL,
    user_id uuid NOT NULL REFERENCES identity_private.users(id) ON DELETE RESTRICT,
    status varchar(10) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,user_id)
);
CREATE INDEX memberships_user_idx ON identity.memberships(user_id,tenant_id);
CREATE TABLE identity.membership_roles (
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role_code varchar(20) NOT NULL CHECK(role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')),
    PRIMARY KEY(tenant_id,membership_id,role_code),
    FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT
);
CREATE TABLE identity_private.membership_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, membership_id uuid NOT NULL,
    actor_id uuid NOT NULL REFERENCES identity_private.users(id),
    event_code varchar(10) NOT NULL CHECK(event_code IN ('ACTIVE','SUSPENDED','REVOKED')),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id)
);
REVOKE ALL ON identity_private.membership_events FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
CREATE TABLE identity.invitations (
    tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), id uuid NOT NULL,
    issuer text NOT NULL, subject varchar(255) NOT NULL,
    role_code varchar(20) NOT NULL CHECK(role_code IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')),
    token_hash bytea NOT NULL UNIQUE CHECK(octet_length(token_hash)=32),
    expires_at timestamptz NOT NULL, consumed_at timestamptz, revoked_at timestamptz,
    created_by uuid NOT NULL REFERENCES identity_private.users(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(tenant_id,id)
);
REVOKE ALL ON identity.memberships,identity.membership_roles,identity.invitations FROM PUBLIC,fiscuvia_runtime;
GRANT SELECT ON identity.memberships,identity.membership_roles TO fiscuvia_runtime;
ALTER TABLE identity.memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity.memberships FORCE ROW LEVEL SECURITY;
ALTER TABLE identity.membership_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity.membership_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE identity.invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity.invitations FORCE ROW LEVEL SECURITY;

CREATE FUNCTION identity.admin_tenant_id() RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
    SELECT tenant_id FROM identity_private.actor_context
    WHERE transaction_id=pg_current_xact_id_if_assigned() AND privileged
      AND mfa_at BETWEEN extract(epoch FROM CURRENT_TIMESTAMP)::bigint-600 AND extract(epoch FROM CURRENT_TIMESTAMP)::bigint+30
      AND tenant_id=app.current_tenant_id()
$$;

CREATE POLICY membership_runtime ON identity.memberships FOR SELECT TO fiscuvia_runtime
    USING (user_id=identity.current_actor_id() OR tenant_id=identity.admin_tenant_id());
CREATE POLICY membership_fence ON identity.memberships AS RESTRICTIVE TO fiscuvia_runtime
    USING (user_id=identity.current_actor_id() OR tenant_id=identity.admin_tenant_id())
    WITH CHECK (false);
CREATE POLICY membership_owner ON identity.memberships TO fiscuvia_migrator
    USING (tenant_id=app.current_tenant_id() OR user_id=identity.current_actor_id())
    WITH CHECK (tenant_id=app.current_tenant_id());
CREATE POLICY roles_runtime ON identity.membership_roles FOR SELECT TO fiscuvia_runtime
    USING (EXISTS (SELECT 1 FROM identity.memberships m WHERE m.tenant_id=membership_roles.tenant_id AND m.id=membership_roles.membership_id));
CREATE POLICY roles_fence ON identity.membership_roles AS RESTRICTIVE TO fiscuvia_runtime
    USING (EXISTS (SELECT 1 FROM identity.memberships m WHERE m.tenant_id=membership_roles.tenant_id AND m.id=membership_roles.membership_id)) WITH CHECK(false);
CREATE POLICY roles_owner ON identity.membership_roles TO fiscuvia_migrator
    USING (tenant_id=app.current_tenant_id() OR EXISTS (SELECT 1 FROM identity.memberships m WHERE m.tenant_id=membership_roles.tenant_id AND m.id=membership_roles.membership_id AND m.user_id=identity.current_actor_id()))
    WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY invitations_owner ON identity.invitations TO fiscuvia_migrator
    USING (tenant_id=app.current_tenant_id() OR EXISTS(SELECT 1 FROM identity_private.external_identities e WHERE e.user_id=identity.current_actor_id() AND e.issuer=invitations.issuer AND e.subject=invitations.subject))
    WITH CHECK(tenant_id=app.current_tenant_id());

CREATE FUNCTION identity.authorize_tenant(p_tenant uuid,p_privileged boolean) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id(); member uuid; bound uuid; mfa bigint;
BEGIN
    SELECT c.tenant_id,c.mfa_at INTO bound,mfa FROM identity_private.actor_context c WHERE c.transaction_id=pg_current_xact_id_if_assigned();
    IF actor IS NULL OR p_tenant IS NULL OR (bound IS NOT NULL AND bound<>p_tenant) THEN
        RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    SELECT id INTO member FROM identity.memberships WHERE user_id=actor AND tenant_id=p_tenant AND status='ACTIVE' FOR SHARE;
    IF member IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    IF p_privileged AND (mfa NOT BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30
       OR NOT EXISTS(SELECT 1 FROM identity.membership_roles WHERE tenant_id=p_tenant AND membership_id=member AND role_code='FIRM_OWNER_ADMIN')) THEN
       RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    UPDATE identity_private.actor_context SET tenant_id=p_tenant,privileged=privileged OR p_privileged
        WHERE transaction_id=pg_current_xact_id_if_assigned();
END;
$$;

-- Preserve V2's implementation/checksum, but remove runtime access to its unguarded entry point.
-- SESSION_USER is the actual connection role even inside nested SECURITY DEFINER calls.
ALTER FUNCTION app.establish_tenant_context(uuid) SET SCHEMA infrastructure;
ALTER FUNCTION infrastructure.establish_tenant_context(uuid) RENAME TO bind_tenant_context;
REVOKE ALL ON FUNCTION infrastructure.bind_tenant_context(uuid) FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
CREATE FUNCTION app.establish_tenant_context(requested_tenant uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF SESSION_USER='fiscuvia_runtime' THEN
        IF NOT EXISTS(SELECT 1 FROM identity_private.actor_context
            WHERE transaction_id=pg_current_xact_id_if_assigned() AND tenant_id=requested_tenant) THEN
            RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='TENANT_CONTEXT_REJECTED';
        END IF;
    ELSIF SESSION_USER<>'fiscuvia_migrator' THEN
        RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='TENANT_CONTEXT_REJECTED';
    END IF;
    PERFORM infrastructure.bind_tenant_context(requested_tenant);
END;
$$;
REVOKE ALL ON FUNCTION app.establish_tenant_context(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.establish_tenant_context(uuid) TO fiscuvia_runtime,fiscuvia_migrator;

CREATE FUNCTION identity_private.require_admin(p_tenant uuid) RETURNS uuid LANGUAGE plpgsql
SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id();
BEGIN
    IF identity.admin_tenant_id() IS DISTINCT FROM p_tenant OR actor IS NULL OR NOT EXISTS(
      SELECT 1 FROM identity.memberships m JOIN identity.membership_roles r USING(tenant_id)
      WHERE m.user_id=actor AND m.tenant_id=p_tenant AND m.status='ACTIVE' AND r.membership_id=m.id AND r.role_code='FIRM_OWNER_ADMIN') THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    RETURN actor;
END;
$$;

CREATE FUNCTION identity.create_invitation(p_tenant uuid,p_subject text,p_issuer text,p_role text,p_hash bytea,p_expires timestamptz)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity_private.require_admin(p_tenant); invitation uuid:=gen_random_uuid();
BEGIN
    IF p_subject IS NULL OR length(p_subject) NOT BETWEEN 1 AND 255 OR p_subject ~ E'[\\n\\r]' OR p_issuer IS NULL OR length(p_issuer) NOT BETWEEN 1 AND 500
      OR p_role NOT IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT') OR p_hash IS NULL OR octet_length(p_hash)<>32
      OR p_expires<=clock_timestamp() OR p_expires>clock_timestamp()+interval '7 days' THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    INSERT INTO identity.invitations(tenant_id,id,issuer,subject,role_code,token_hash,expires_at,created_by)
      VALUES(p_tenant,invitation,p_issuer,p_subject,p_role,p_hash,p_expires,actor);
    RETURN invitation;
END;
$$;
CREATE FUNCTION identity.revoke_invitation(p_tenant uuid,p_id uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM identity_private.require_admin(p_tenant);
    UPDATE identity.invitations SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE tenant_id=p_tenant AND id=p_id AND consumed_at IS NULL;
    RETURN FOUND;
END;
$$;
CREATE FUNCTION identity.change_membership(p_tenant uuid,p_id uuid,p_status text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM identity_private.require_admin(p_tenant);
    IF p_status IS NULL OR p_status NOT IN ('SUSPENDED','REVOKED') OR EXISTS(SELECT 1 FROM identity.membership_roles WHERE tenant_id=p_tenant AND membership_id=p_id AND role_code='FIRM_OWNER_ADMIN') THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    UPDATE identity.memberships SET status=p_status,changed_at=clock_timestamp()
      WHERE tenant_id=p_tenant AND id=p_id AND status<>p_status AND status<>'REVOKED';
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO identity_private.membership_events(tenant_id,membership_id,actor_id,event_code)
      VALUES(p_tenant,p_id,identity.current_actor_id(),p_status);
    RETURN true;
END;
$$;
CREATE FUNCTION identity.accept_invitation(p_hash bytea) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id(); invitation identity.invitations%ROWTYPE; member uuid:=gen_random_uuid();
BEGIN
    IF actor IS NULL OR p_hash IS NULL OR octet_length(p_hash)<>32 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    SELECT i.* INTO invitation FROM identity.invitations i JOIN identity_private.external_identities e
      ON e.issuer=i.issuer AND e.subject=i.subject AND e.user_id=actor
      WHERE i.token_hash=p_hash AND i.consumed_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>clock_timestamp() FOR UPDATE OF i;
    IF invitation.id IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    UPDATE identity_private.actor_context SET tenant_id=invitation.tenant_id WHERE transaction_id=pg_current_xact_id_if_assigned() AND (tenant_id IS NULL OR tenant_id=invitation.tenant_id);
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    PERFORM app.establish_tenant_context(invitation.tenant_id);
    IF EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=invitation.tenant_id AND user_id=actor) THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
    INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(invitation.tenant_id,member,actor,'ACTIVE');
    INSERT INTO identity.membership_roles VALUES(invitation.tenant_id,member,invitation.role_code);
    INSERT INTO identity_private.membership_events(tenant_id,membership_id,actor_id,event_code)
      VALUES(invitation.tenant_id,member,actor,'ACTIVE');
    UPDATE identity.invitations SET consumed_at=clock_timestamp() WHERE tenant_id=invitation.tenant_id AND id=invitation.id;
    RETURN invitation.tenant_id;
END;
$$;

CREATE FUNCTION identity_private.bootstrap_owner(p_tenant uuid,p_user uuid,p_approval uuid) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE member uuid:=gen_random_uuid();
BEGIN
    IF p_approval IS NULL OR NOT EXISTS(SELECT 1 FROM identity_private.external_identities WHERE user_id=p_user)
      OR NOT EXISTS(SELECT 1 FROM identity_private.users WHERE id=p_user AND status='ACTIVE') THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='BOOTSTRAP_REJECTED'; END IF;
    PERFORM app.establish_tenant_context(p_tenant);
    IF EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=p_tenant)
      OR EXISTS(SELECT 1 FROM identity_private.owner_approvals WHERE tenant_id=p_tenant) THEN
      RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='BOOTSTRAP_REJECTED'; END IF;
    INSERT INTO identity_private.owner_approvals VALUES(p_tenant,p_user,p_approval,clock_timestamp());
    INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(p_tenant,member,p_user,'ACTIVE');
    INSERT INTO identity.membership_roles VALUES(p_tenant,member,'FIRM_OWNER_ADMIN');
    INSERT INTO identity_private.membership_events(tenant_id,membership_id,actor_id,event_code)
      VALUES(p_tenant,member,p_user,'ACTIVE');
END;
$$;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA identity,identity_private FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity.current_actor_id(),identity.admin_tenant_id(),identity.bind_actor(text,text,bigint,text),
    identity.authorize_tenant(uuid,boolean),identity.create_invitation(uuid,text,text,text,bytea,timestamptz),
    identity.revoke_invitation(uuid,uuid),identity.change_membership(uuid,uuid,text),identity.accept_invitation(bytea) TO fiscuvia_runtime;

CREATE TABLE auth_sessions.spring_session (
    primary_id char(36) PRIMARY KEY, session_id char(36) NOT NULL UNIQUE,
    creation_time bigint NOT NULL,last_access_time bigint NOT NULL,max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,principal_name varchar(100)
);
CREATE INDEX spring_session_expiry ON auth_sessions.spring_session(expiry_time);
CREATE INDEX spring_session_principal ON auth_sessions.spring_session(principal_name);
CREATE TABLE auth_sessions.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES auth_sessions.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name varchar(200) NOT NULL,attribute_bytes bytea NOT NULL,
    PRIMARY KEY(session_primary_id,attribute_name)
);
REVOKE ALL ON ALL TABLES IN SCHEMA auth_sessions FROM PUBLIC,fiscuvia_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA auth_sessions TO fiscuvia_sessions;
