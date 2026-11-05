-- #31. Root administrative metadata is private and separate from tenant-owned business rows.
-- No new operator identity, public registration, fiscal table, subscription or logo storage.
CREATE SCHEMA tenant_private AUTHORIZATION CURRENT_USER;
CREATE SCHEMA tenant_api AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA tenant_private,tenant_api FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT USAGE ON SCHEMA tenant_api TO fiscuvia_runtime;

CREATE TABLE tenant_private.profiles (
 tenant_id uuid PRIMARY KEY REFERENCES app.tenants(tenant_id) ON DELETE RESTRICT,
 commercial_name varchar(160), contact_email varchar(254), phone varchar(30),
 time_zone varchar(64) NOT NULL DEFAULT 'America/Mexico_City', locale varchar(5) NOT NULL DEFAULT 'es-MX',
 status varchar(9) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SUSPENDED')),
 version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 9007199254740991),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(commercial_name IS NULL OR (commercial_name=btrim(commercial_name) AND length(commercial_name) BETWEEN 1 AND 160 AND commercial_name !~ '[[:cntrl:]]')),
 CHECK(contact_email IS NULL OR (length(contact_email) BETWEEN 3 AND 254 AND contact_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$' AND contact_email !~ '[[:cntrl:]]')),
 CHECK(phone IS NULL OR phone ~ '^[+0-9 ()-]{7,30}$'),
 CHECK(time_zone IN ('America/Mexico_City','America/Cancun','America/Chihuahua','America/Ciudad_Juarez','America/Hermosillo','America/Mazatlan','America/Monterrey','America/Tijuana')),
 CHECK(locale='es-MX')
);
-- Existing roots remain usable without inventing commercial names. Configuration is explicit.
-- Atomic upgrade only: ACCESS EXCLUSIVE lock and transactional DDL prevent any concurrent
-- runtime statement from observing this temporary removal. Restore the exact V2 fence below.
DROP POLICY tenant_fence ON app.tenants;
INSERT INTO tenant_private.profiles(tenant_id,created_at,updated_at) SELECT tenant_id,created_at,created_at FROM app.tenants;
CREATE POLICY tenant_fence ON app.tenants AS RESTRICTIVE FOR ALL TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE TABLE tenant_private.admin_commands (
 command_id uuid PRIMARY KEY, operation text NOT NULL CHECK(operation IN ('PROVISION','SUSPEND','REACTIVATE')),
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),
 approval_reference uuid NOT NULL, result_version bigint NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT
);
CREATE TABLE tenant_private.profile_commands (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), command_id uuid NOT NULL,
 actor_id uuid NOT NULL REFERENCES identity_private.users(id), fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),
 result_version bigint NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), PRIMARY KEY(tenant_id,command_id),
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT
);
REVOKE ALL ON ALL TABLES IN SCHEMA tenant_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
-- Root directory/administrative receipts, not a business-data RLS exception. Only narrow functions
-- expose verified membership metadata or an already authorized profile; runtime has no table access.
ALTER TABLE tenant_private.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_private.profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY root_owner ON tenant_private.profiles TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE tenant_private.admin_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_private.admin_commands FORCE ROW LEVEL SECURITY;
CREATE POLICY admin_owner ON tenant_private.admin_commands TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE tenant_private.profile_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_private.profile_commands FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON tenant_private.profile_commands TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());

CREATE FUNCTION tenant_private.initialize_profile() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
BEGIN INSERT INTO tenant_private.profiles(tenant_id,created_at,updated_at) VALUES(NEW.tenant_id,NEW.created_at,NEW.created_at); RETURN NEW; END;
$$;
CREATE TRIGGER initialize_profile AFTER INSERT ON app.tenants FOR EACH ROW EXECUTE FUNCTION tenant_private.initialize_profile();

-- Preserve #27 checks privately and add lifecycle locking to EVERY protected tenant authorization.
ALTER FUNCTION identity.authorize_tenant(uuid,boolean) SET SCHEMA identity_private;
ALTER FUNCTION identity_private.authorize_tenant(uuid,boolean) RENAME TO authorize_active_membership;
REVOKE ALL ON FUNCTION identity_private.authorize_active_membership(uuid,boolean) FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
CREATE FUNCTION identity.authorize_tenant(p_tenant uuid,p_privileged boolean) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF identity.current_actor_id() IS NULL OR p_tenant IS NULL
 OR NOT EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=p_tenant AND user_id=identity.current_actor_id() AND status='ACTIVE')
 OR EXISTS(SELECT 1 FROM identity_private.actor_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND tenant_id IS NOT NULL AND tenant_id<>p_tenant)
 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=p_tenant AND status='ACTIVE' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 PERFORM identity_private.authorize_active_membership(p_tenant,p_privileged);
END;
$$;
REVOKE ALL ON FUNCTION identity.authorize_tenant(uuid,boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity.authorize_tenant(uuid,boolean) TO fiscuvia_runtime;

-- Own directory only: no supplied actor, no selected tenant, no business context. Suspended root
-- metadata is visible to its own ACTIVE members so the UI cannot present it as operational.
CREATE FUNCTION identity.own_firms() RETURNS TABLE(tenant_id uuid,membership_id uuid,role_code text,commercial_name text,tenant_status text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT m.tenant_id,m.id,r.role_code::text,p.commercial_name::text,p.status::text
 FROM identity.memberships m JOIN identity.membership_roles r ON r.tenant_id=m.tenant_id AND r.membership_id=m.id
 JOIN tenant_private.profiles p ON p.tenant_id=m.tenant_id
 JOIN access_control_private.role_capabilities c ON c.role_code=r.role_code AND c.capability='TENANT_READ' AND c.resource_kind='TENANT'
 WHERE m.user_id=identity.current_actor_id() AND m.status='ACTIVE' ORDER BY m.tenant_id,r.role_code
$$;
REVOKE ALL ON FUNCTION identity.own_firms() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity.own_firms() TO fiscuvia_runtime;

-- Invitations cannot create membership in a suspended firm, including a race with suspension.
ALTER FUNCTION identity.accept_invitation(bytea) SET SCHEMA tenant_private;
REVOKE ALL ON FUNCTION tenant_private.accept_invitation(bytea) FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
CREATE FUNCTION identity.accept_invitation(p_hash bytea) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
DECLARE target uuid;
BEGIN
 SELECT i.tenant_id INTO target FROM identity.invitations i JOIN identity_private.external_identities e
 ON e.issuer=i.issuer AND e.subject=i.subject AND e.user_id=identity.current_actor_id()
 WHERE i.token_hash=p_hash AND i.consumed_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>clock_timestamp();
 PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=target AND status='ACTIVE' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 RETURN tenant_private.accept_invitation(p_hash);
END;
$$;
REVOKE ALL ON FUNCTION identity.accept_invitation(bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity.accept_invitation(bytea) TO fiscuvia_runtime;

ALTER TABLE journal.events DROP CONSTRAINT events_event_code_check;
ALTER TABLE journal.events ADD CONSTRAINT events_event_code_check CHECK(event_code IN ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED',
 'INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED','AUTHORIZED_OPERATION_RECORDED',
 'TENANT_CREATED','TENANT_PROFILE_UPDATED','TENANT_SUSPENDED','TENANT_REACTIVATED'));
ALTER TABLE journal.events DROP CONSTRAINT events_operation_check;
ALTER TABLE journal.events ADD CONSTRAINT events_operation_check CHECK(operation IN ('BOOTSTRAP','INVITATION_ACCEPT','TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW',
 'MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE','TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN',
 'TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW','TENANT_PROVISION','TENANT_SUSPEND','TENANT_REACTIVATE'));

CREATE FUNCTION tenant_private.require_operation(p_tenant uuid,p_capability text) RETURNS void LANGUAGE plpgsql
SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM access_control_private.operation_context c WHERE c.transaction_id=pg_current_xact_id_if_assigned()
 AND c.actor_id=identity.current_actor_id() AND c.tenant_id=p_tenant AND c.tenant_id=app.current_tenant_id()
 AND c.capability=p_capability AND c.resource_kind='TENANT' AND c.resource_id IS NULL) THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
END;
$$;
CREATE FUNCTION tenant_api.profile(p_tenant uuid) RETURNS TABLE(commercial_name text,contact_email text,phone text,time_zone text,locale text,
 status text,version bigint,created_at timestamptz,updated_at timestamptz,can_manage boolean)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'TENANT_READ');
 RETURN QUERY SELECT p.commercial_name::text,p.contact_email::text,p.phone::text,p.time_zone::text,p.locale::text,p.status::text,
 p.version,p.created_at,p.updated_at,EXISTS(SELECT 1 FROM identity.memberships m JOIN identity.membership_roles r ON r.tenant_id=m.tenant_id AND r.membership_id=m.id
 JOIN access_control_private.role_capabilities c ON c.role_code=r.role_code
 JOIN identity_private.actor_context a ON a.transaction_id=pg_current_xact_id_if_assigned() AND a.user_id=m.user_id
 WHERE m.tenant_id=p_tenant AND m.user_id=identity.current_actor_id() AND m.status='ACTIVE'
 AND r.role_code='FIRM_OWNER_ADMIN'
 AND c.capability='TENANT_SETTINGS_MANAGE' AND c.resource_kind='TENANT' AND c.requires_mfa
 AND a.mfa_at BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30)
 FROM tenant_private.profiles p WHERE p.tenant_id=p_tenant;
END;
$$;

CREATE FUNCTION tenant_api.update_profile(p_tenant uuid,p_command uuid,p_version bigint,p_name text,p_email text,p_phone text,p_zone text,p_locale text)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE prior tenant_private.profile_commands%ROWTYPE; fingerprint bytea; current_version bigint; resulting bigint;
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'TENANT_SETTINGS_MANAGE');
 PERFORM identity.authorize_tenant(p_tenant,true);
 IF p_command IS NULL OR NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=p_command)
 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_version,p_name,p_email,p_phone,p_zone,p_locale)::text,'UTF8'),'sha256');
 -- Serialize version check and replay before touching metadata; no duplicate event/outbox.
 SELECT version INTO current_version FROM tenant_private.profiles WHERE tenant_id=p_tenant FOR UPDATE;
 SELECT * INTO prior FROM tenant_private.profile_commands WHERE tenant_id=p_tenant AND command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
   IF prior.actor_id IS DISTINCT FROM identity.current_actor_id() OR prior.fingerprint IS DISTINCT FROM fingerprint THEN
     RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
   RETURN prior.result_version;
 END IF;
 IF current_version IS DISTINCT FROM p_version THEN RAISE EXCEPTION USING ERRCODE='40001',MESSAGE='STALE_VERSION'; END IF;
 IF p_name IS NULL OR length(btrim(p_name))=0 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='PROFILE_INVALID'; END IF;
 UPDATE tenant_private.profiles SET commercial_name=p_name,contact_email=p_email,phone=p_phone,time_zone=p_zone,locale=p_locale,
 version=version+1,updated_at=clock_timestamp() WHERE tenant_id=p_tenant RETURNING version INTO resulting;
 PERFORM journal_private.append_event(p_tenant,'TENANT_PROFILE_UPDATED','TENANT_SETTINGS_MANAGE','TENANT',NULL);
 INSERT INTO tenant_private.profile_commands VALUES(p_tenant,p_command,identity.current_actor_id(),fingerprint,resulting,clock_timestamp());
 RETURN resulting;
END;
$$;

-- Administrative invocation is a separate credential boundary, never an HTTP role. The owner
-- must already exist from verified OIDC and be independently approved; UUID is not authentication.
CREATE FUNCTION tenant_private.provision(p_command uuid,p_owner uuid,p_approval uuid,p_name text,p_email text,p_phone text,p_zone text,p_locale text)
RETURNS uuid LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE id uuid; fingerprint bytea; prior tenant_private.admin_commands%ROWTYPE; cause uuid;
BEGIN
 IF SESSION_USER<>'fiscuvia_migrator' OR identity.current_actor_id() IS NOT NULL OR p_command IS NULL OR p_approval IS NULL
 OR p_name IS NULL OR length(btrim(p_name))=0 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='PROVISION_REJECTED'; END IF;
 PERFORM 1 FROM identity_private.users u WHERE u.id=p_owner AND u.status='ACTIVE'
 AND EXISTS(SELECT 1 FROM identity_private.external_identities e WHERE e.user_id=u.id) FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='PROVISION_REJECTED'; END IF;
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_owner,p_approval,p_name,p_email,p_phone,p_zone,p_locale)::text,'UTF8'),'sha256');
 PERFORM pg_advisory_xact_lock(hashtextextended(p_command::text,3101));
 SELECT * INTO prior FROM tenant_private.admin_commands WHERE command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.operation<>'PROVISION' OR prior.fingerprint IS DISTINCT FROM fingerprint THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN prior.tenant_id; END IF;
 id:=gen_random_uuid(); PERFORM app.establish_tenant_context(id);
 INSERT INTO app.tenants(tenant_id) VALUES(id);
 UPDATE tenant_private.profiles SET commercial_name=p_name,contact_email=p_email,phone=p_phone,time_zone=p_zone,locale=p_locale WHERE tenant_id=id;
 PERFORM identity_private.bootstrap_owner(id,p_owner,p_approval);
 SELECT event_id INTO cause FROM journal.events WHERE tenant_id=id AND event_code='OWNER_BOOTSTRAPPED';
 INSERT INTO journal_private.command_context VALUES(pg_current_xact_id(),p_command,gen_random_uuid())
 ON CONFLICT(transaction_id) DO UPDATE SET command_id=EXCLUDED.command_id;
 PERFORM journal_private.append_event(id,'TENANT_CREATED','TENANT_PROVISION','TENANT',NULL,NULL,p_approval,cause);
 INSERT INTO tenant_private.admin_commands VALUES(p_command,'PROVISION',id,fingerprint,p_approval,0,clock_timestamp());
 RETURN id;
END;
$$;

CREATE FUNCTION tenant_private.change_status(p_tenant uuid,p_command uuid,p_approval uuid,p_version bigint,p_status text) RETURNS bigint
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE prior tenant_private.admin_commands%ROWTYPE; fingerprint bytea; current_profile tenant_private.profiles%ROWTYPE; resulting bigint;
BEGIN
 IF SESSION_USER<>'fiscuvia_migrator' OR identity.current_actor_id() IS NOT NULL OR p_command IS NULL OR p_approval IS NULL
 OR p_status IS NULL OR p_status NOT IN ('ACTIVE','SUSPENDED') THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='STATUS_REJECTED'; END IF;
 PERFORM app.establish_tenant_context(p_tenant);
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_tenant,p_approval,p_version,p_status)::text,'UTF8'),'sha256');
 PERFORM pg_advisory_xact_lock(hashtextextended(p_command::text,3101));
 SELECT * INTO prior FROM tenant_private.admin_commands WHERE command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.fingerprint IS DISTINCT FROM fingerprint OR prior.tenant_id<>p_tenant OR prior.operation='PROVISION' THEN
 RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF; RETURN prior.result_version; END IF;
 SELECT * INTO current_profile FROM tenant_private.profiles WHERE tenant_id=p_tenant FOR UPDATE;
 IF current_profile.tenant_id IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='STATUS_REJECTED'; END IF;
 IF current_profile.version IS DISTINCT FROM p_version OR current_profile.status=p_status THEN
 RAISE EXCEPTION USING ERRCODE='40001',MESSAGE='STALE_VERSION'; END IF;
 UPDATE tenant_private.profiles SET status=p_status,version=version+1,updated_at=clock_timestamp() WHERE tenant_id=p_tenant RETURNING version INTO resulting;
 INSERT INTO journal_private.command_context VALUES(pg_current_xact_id(),p_command,gen_random_uuid())
 ON CONFLICT(transaction_id) DO UPDATE SET command_id=EXCLUDED.command_id;
 PERFORM journal_private.append_event(p_tenant,CASE WHEN p_status='ACTIVE' THEN 'TENANT_REACTIVATED' ELSE 'TENANT_SUSPENDED' END,
 CASE WHEN p_status='ACTIVE' THEN 'TENANT_REACTIVATE' ELSE 'TENANT_SUSPEND' END,'TENANT',NULL,NULL,p_approval);
 INSERT INTO tenant_private.admin_commands VALUES(p_command,CASE WHEN p_status='ACTIVE' THEN 'REACTIVATE' ELSE 'SUSPEND' END,p_tenant,fingerprint,p_approval,resulting,clock_timestamp());
 RETURN resulting;
END;
$$;
-- Preserve V4 capability/resource semantics, adding only root lifecycle locking.
CREATE OR REPLACE FUNCTION access_control.authorize_operation(p_tenant uuid,p_capability text,p_kind text,p_resource uuid)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id(); member uuid; mfa bigint; needs_mfa boolean; tx xid8:=pg_current_xact_id(); resources uuid[];
BEGIN
    -- Non-locking rejection before touching a requested root. These checks grant no authority:
    -- membership, policy, MFA and assignments are rechecked/frozen under locks below.
    IF actor IS NULL OR p_tenant IS NULL OR p_capability IS NULL OR p_kind IS NULL THEN RETURN 'CONTEXT_REJECTED'; END IF;
    IF EXISTS(SELECT 1 FROM identity_private.actor_context WHERE transaction_id=tx AND tenant_id IS NOT NULL AND tenant_id<>p_tenant)
      OR EXISTS(SELECT 1 FROM access_control_private.operation_context c WHERE c.transaction_id=tx AND
        ROW(c.actor_id,c.tenant_id,c.capability,c.resource_kind,c.resource_id) IS DISTINCT FROM ROW(actor,p_tenant,p_capability,p_kind,p_resource))
      OR (p_kind='TENANT' AND p_resource IS NOT NULL) THEN RETURN 'CONTEXT_REJECTED'; END IF;
    SELECT id INTO member FROM identity.memberships WHERE tenant_id=p_tenant AND user_id=actor AND status='ACTIVE';
    IF member IS NULL THEN RETURN 'MEMBERSHIP_INACTIVE'; END IF;
    SELECT p.requires_mfa INTO needs_mfa FROM identity.membership_roles r
      JOIN access_control_private.role_capabilities p ON p.role_code=r.role_code
      WHERE r.tenant_id=p_tenant AND r.membership_id=member AND p.capability=p_capability AND p.resource_kind=p_kind
      ORDER BY p.requires_mfa,r.role_code LIMIT 1;
    IF NOT FOUND THEN RETURN 'CAPABILITY_UNCONFIGURED'; END IF;
    SELECT mfa_at INTO mfa FROM identity_private.actor_context WHERE transaction_id=tx;
    IF needs_mfa AND mfa NOT BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30
      THEN RETURN 'MFA_REQUIRED'; END IF;
    -- Lock root metadata before membership/policy locks. Settings commands take UPDATE up front:
    -- two concurrent read-to-write upgrades on the same root must not deadlock.
    IF p_capability='TENANT_SETTINGS_MANAGE' THEN
      PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=p_tenant AND status='ACTIVE' FOR UPDATE;
    ELSE
      PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=p_tenant AND status='ACTIVE' FOR SHARE;
    END IF;
    IF NOT FOUND THEN RETURN 'MEMBERSHIP_INACTIVE'; END IF;
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
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA tenant_private,tenant_api FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION tenant_api.profile(uuid),tenant_api.update_profile(uuid,uuid,bigint,text,text,text,text,text) TO fiscuvia_runtime;
-- TENANT_SETTINGS_MANAGE remains DENY until the owner's explicit approval of the #31 policy.
