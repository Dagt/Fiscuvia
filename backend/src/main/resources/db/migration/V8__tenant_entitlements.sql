-- #32 technical entitlements only. No commercial plan, prices or fiscal objects.
CREATE SCHEMA entitlement_private AUTHORIZATION CURRENT_USER;
CREATE SCHEMA entitlement_api AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA entitlement_private,entitlement_api FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT USAGE ON SCHEMA entitlement_api TO fiscuvia_runtime;
CREATE TABLE entitlement_private.limits (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id),
 resource text NOT NULL CHECK(resource IN ('USERS','TAXPAYERS','STORAGE_BYTES','JOBS_RUNNING','JOBS_PENDING','JOBS_PER_PERIOD')),
 limit_value bigint NOT NULL CHECK(limit_value BETWEEN 0 AND 9007199254740991),
 version bigint NOT NULL CHECK(version BETWEEN 1 AND 9007199254740991),
 valid_from timestamptz NOT NULL, valid_until timestamptz,
 PRIMARY KEY(tenant_id,resource), CHECK(valid_until IS NULL OR valid_until>valid_from)
);
CREATE TABLE entitlement_private.changes (
 command_id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id),
 resource text NOT NULL, fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),
 result_version bigint NOT NULL, limit_value bigint NOT NULL, valid_from timestamptz NOT NULL,valid_until timestamptz,
 approval_reference uuid NOT NULL, occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(tenant_id,resource) REFERENCES entitlement_private.limits(tenant_id,resource),
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id)
);
CREATE INDEX entitlement_history ON entitlement_private.changes(tenant_id,resource,result_version);
CREATE TABLE entitlement_private.reservations (
 tenant_id uuid NOT NULL, command_id uuid NOT NULL,resource text NOT NULL,
 reference_id uuid NOT NULL REFERENCES identity_private.users(id),
 amount bigint NOT NULL CHECK(amount=1), configuration_version bigint NOT NULL,
 status text NOT NULL CHECK(status IN ('RESERVED','CONFIRMED','RELEASED')),
 actor_id uuid NOT NULL REFERENCES identity_private.users(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(tenant_id,command_id), FOREIGN KEY(tenant_id,resource) REFERENCES entitlement_private.limits(tenant_id,resource)
);
CREATE UNIQUE INDEX reserved_user ON entitlement_private.reservations(tenant_id,reference_id) WHERE status='RESERVED';
CREATE INDEX reserved_capacity ON entitlement_private.reservations(tenant_id,resource) WHERE status='RESERVED';
ALTER TABLE entitlement_private.limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE entitlement_private.limits FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON entitlement_private.limits TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
ALTER TABLE entitlement_private.changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE entitlement_private.changes FORCE ROW LEVEL SECURITY;
-- Global command deduplication metadata, no runtime access; same administrative boundary as #31.
CREATE POLICY admin_owner ON entitlement_private.changes TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE entitlement_private.reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE entitlement_private.reservations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON entitlement_private.reservations TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON entitlement_private.changes FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON entitlement_private.changes FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();

-- Extend the closed catalogs without changing any V1--V7 bytes or granting capabilities.
ALTER TABLE access_control_private.role_capabilities DROP CONSTRAINT role_capabilities_capability_check;
ALTER TABLE access_control_private.role_capabilities ADD CONSTRAINT role_capabilities_capability_check CHECK(capability IN
 ('TENANT_READ','TENANT_SETTINGS_MANAGE','TENANT_PLAN_VIEW','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE',
 'TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW'));
ALTER TABLE journal.events DROP CONSTRAINT events_event_code_check;
ALTER TABLE journal.events ADD CONSTRAINT events_event_code_check CHECK(event_code IN
 ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED','INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED',
 'AUTHORIZED_OPERATION_RECORDED','TENANT_CREATED','TENANT_PROFILE_UPDATED','TENANT_SUSPENDED','TENANT_REACTIVATED','TENANT_QUOTA_CHANGED','QUOTA_RESERVED','QUOTA_RELEASED'));
ALTER TABLE journal.events DROP CONSTRAINT events_operation_check;
ALTER TABLE journal.events ADD CONSTRAINT events_operation_check CHECK(operation IN
 ('BOOTSTRAP','INVITATION_ACCEPT','TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE',
 'TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW',
 'TENANT_PROVISION','TENANT_SUSPEND','TENANT_REACTIVATE','TENANT_QUOTA_CHANGE'));

ALTER TABLE journal.events DROP CONSTRAINT events_resource_kind_check;
ALTER TABLE journal.events ADD CONSTRAINT events_resource_kind_check CHECK(resource_kind IN ('TENANT','TAXPAYER','DOCUMENT','REPORT','AUDIT','INVITATION','MEMBERSHIP','RESERVATION'));

CREATE FUNCTION entitlement_private.assign_limit(p_tenant uuid,p_command uuid,p_approval uuid,p_resource text,p_limit bigint,p_version bigint,p_until timestamptz)
RETURNS bigint LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE prior entitlement_private.changes%ROWTYPE; current_version bigint; fingerprint bytea; resulting bigint; starts timestamptz;
BEGIN
 IF SESSION_USER<>'fiscuvia_migrator' OR identity.current_actor_id() IS NOT NULL OR p_command IS NULL OR p_approval IS NULL
 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ADMIN_REQUIRED'; END IF;
 IF p_resource IS NULL OR p_resource NOT IN ('USERS','TAXPAYERS','STORAGE_BYTES','JOBS_RUNNING','JOBS_PENDING','JOBS_PER_PERIOD')
 OR p_limit IS NULL OR p_limit NOT BETWEEN 0 AND 9007199254740991 OR p_version IS NULL OR p_version NOT BETWEEN 0 AND 9007199254740990
 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='QUOTA_INVALID'; END IF;
 PERFORM app.establish_tenant_context(p_tenant);
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_tenant,p_approval,p_resource,p_limit,p_version,p_until)::text,'UTF8'),'sha256');
 PERFORM pg_advisory_xact_lock(hashtextextended(p_command::text,3201));
 SELECT * INTO prior FROM entitlement_private.changes WHERE command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.fingerprint IS DISTINCT FROM fingerprint THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN prior.result_version; END IF;
 -- Root lock serializes creation of absent resource rows and lifecycle changes, before quota lock.
 PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=p_tenant FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='TENANT_UNKNOWN'; END IF;
 SELECT version INTO current_version FROM entitlement_private.limits WHERE tenant_id=p_tenant AND resource=p_resource FOR UPDATE;
 IF COALESCE(current_version,0)<>p_version THEN RAISE EXCEPTION USING ERRCODE='40001',MESSAGE='STALE_VERSION'; END IF;
 starts:=clock_timestamp();
 IF p_until IS NOT NULL AND p_until<=starts THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='QUOTA_INVALID'; END IF;
 resulting:=p_version+1;
 INSERT INTO entitlement_private.limits VALUES(p_tenant,p_resource,p_limit,resulting,starts,p_until)
 ON CONFLICT(tenant_id,resource) DO UPDATE SET limit_value=EXCLUDED.limit_value,version=EXCLUDED.version,valid_from=EXCLUDED.valid_from,valid_until=EXCLUDED.valid_until;
 INSERT INTO journal_private.command_context VALUES(pg_current_xact_id(),p_command,gen_random_uuid()) ON CONFLICT(transaction_id) DO UPDATE SET command_id=EXCLUDED.command_id;
 PERFORM journal_private.append_event(p_tenant,'TENANT_QUOTA_CHANGED','TENANT_QUOTA_CHANGE','TENANT',NULL,NULL,p_approval);
 INSERT INTO entitlement_private.changes(command_id,tenant_id,resource,fingerprint,result_version,limit_value,valid_from,valid_until,approval_reference)
 VALUES(p_command,p_tenant,p_resource,fingerprint,resulting,p_limit,starts,p_until,p_approval);
 RETURN resulting;
END;
$$;
CREATE FUNCTION entitlement_private.measured_users(p_tenant uuid) RETURNS bigint LANGUAGE sql STABLE SET search_path=pg_catalog,pg_temp AS $$
 SELECT count(*) FROM identity.memberships WHERE tenant_id=p_tenant AND status='ACTIVE'
$$;
CREATE FUNCTION entitlement_api.usage(p_tenant uuid) RETURNS TABLE(resource text,limit_value bigint,version bigint,valid_from timestamptz,valid_until timestamptz,
 measured bigint,reserved bigint,available bigint,over_limit boolean,integration text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'TENANT_PLAN_VIEW');
 RETURN QUERY SELECT kinds.r,l.limit_value,l.version,l.valid_from,l.valid_until,
 CASE WHEN kinds.r='USERS' THEN u.used ELSE NULL END,
 CASE WHEN kinds.r='USERS' THEN u.held ELSE NULL END,
 CASE WHEN kinds.r='USERS' AND l.valid_from<=clock_timestamp() AND (l.valid_until IS NULL OR l.valid_until>clock_timestamp())
 THEN greatest(0,l.limit_value-u.used-u.held) ELSE NULL END,
 CASE WHEN kinds.r='USERS' AND l.limit_value IS NOT NULL THEN u.used+u.held>l.limit_value ELSE NULL END,
 CASE WHEN kinds.r='USERS' THEN 'MEASURED' ELSE 'NOT_INTEGRATED' END
 FROM unnest(ARRAY['USERS','TAXPAYERS','STORAGE_BYTES','JOBS_RUNNING','JOBS_PENDING','JOBS_PER_PERIOD']) WITH ORDINALITY kinds(r,position)
 LEFT JOIN entitlement_private.limits l ON l.tenant_id=p_tenant AND l.resource=kinds.r
 CROSS JOIN LATERAL(SELECT entitlement_private.measured_users(p_tenant) used,
 (SELECT count(*) FROM entitlement_private.reservations r WHERE r.tenant_id=p_tenant AND r.status='RESERVED') held) u ORDER BY kinds.position;
END;
$$;
CREATE FUNCTION entitlement_api.reserve_user(p_tenant uuid,p_command uuid,p_user uuid) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE config entitlement_private.limits%ROWTYPE; prior entitlement_private.reservations%ROWTYPE; used bigint; held bigint;
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_INVITE');
 IF NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=p_command) THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 IF p_command IS NULL OR p_user IS NULL THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='QUOTA_INVALID'; END IF;
 SELECT * INTO config FROM entitlement_private.limits WHERE tenant_id=p_tenant AND resource='USERS' FOR UPDATE;
 IF config.tenant_id IS NULL THEN RETURN 'UNCONFIGURED'; END IF;
 SELECT * INTO prior FROM entitlement_private.reservations WHERE tenant_id=p_tenant AND command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.reference_id<>p_user OR prior.actor_id<>identity.current_actor_id() THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN prior.status; END IF;
 IF config.valid_from>clock_timestamp() OR config.valid_until<=clock_timestamp() THEN RETURN 'EXPIRED'; END IF;
 IF EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=p_tenant AND user_id=p_user AND status='ACTIVE') THEN RETURN 'ALREADY_ACTIVE'; END IF;
 used:=entitlement_private.measured_users(p_tenant);
 SELECT count(*) INTO held FROM entitlement_private.reservations WHERE tenant_id=p_tenant AND status='RESERVED';
 IF used>=config.limit_value OR held>=config.limit_value-used THEN RETURN 'EXHAUSTED'; END IF;
 INSERT INTO entitlement_private.reservations(tenant_id,command_id,resource,reference_id,amount,configuration_version,status,actor_id)
 VALUES(p_tenant,p_command,'USERS',p_user,1,config.version,'RESERVED',identity.current_actor_id());
 PERFORM journal_private.append_event(p_tenant,'QUOTA_RESERVED','MEMBERSHIP_INVITE','RESERVATION',p_command);
 RETURN 'RESERVED';
END;
$$;
CREATE FUNCTION entitlement_api.release_user(p_tenant uuid,p_command uuid,p_release uuid) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE prior entitlement_private.reservations%ROWTYPE;
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_INVITE');
 PERFORM 1 FROM entitlement_private.limits WHERE tenant_id=p_tenant AND resource='USERS' FOR UPDATE;
 SELECT * INTO prior FROM entitlement_private.reservations WHERE tenant_id=p_tenant AND command_id=p_command FOR UPDATE;
 IF prior.command_id IS NULL OR prior.actor_id IS DISTINCT FROM identity.current_actor_id() THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 IF prior.status='CONFIRMED' THEN RETURN 'CONFIRMED'; END IF;
 IF prior.status='RELEASED' THEN RETURN 'RELEASED'; END IF;
 IF p_release IS NULL OR NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=p_release) THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 UPDATE entitlement_private.reservations SET status='RELEASED' WHERE tenant_id=p_tenant AND command_id=p_command;
 PERFORM journal_private.append_event(p_tenant,'QUOTA_RELEASED','MEMBERSHIP_INVITE','RESERVATION',p_command);
 RETURN 'RELEASED';
END;
$$;
-- Prepared hook, deliberately NOT installed until invitation/counting policy receives approval.
-- It must run BEFORE INSERT/UPDATE of memberships, in the resource's transaction. Never cache usage.
CREATE FUNCTION entitlement_private.membership_capacity() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE config entitlement_private.limits%ROWTYPE; used bigint; held bigint; reservation uuid;
BEGIN
 IF TG_OP='UPDATE' THEN
 IF NEW.tenant_id<>OLD.tenant_id OR NEW.user_id<>OLD.user_id THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='MEMBERSHIP_IMMUTABLE'; END IF;
 IF NEW.status<>'ACTIVE' OR OLD.status='ACTIVE' THEN RETURN NEW; END IF;
 ELSIF NEW.status<>'ACTIVE' THEN RETURN NEW; END IF;
 SELECT * INTO config FROM entitlement_private.limits WHERE tenant_id=NEW.tenant_id AND resource='USERS' FOR UPDATE;
 -- Approved bootstrap remains possible before any limits are assigned; no tenant self-provisioning.
 IF config.tenant_id IS NULL AND SESSION_USER='fiscuvia_migrator' THEN RETURN NEW; END IF;
 IF config.tenant_id IS NULL OR config.valid_from>clock_timestamp() OR config.valid_until<=clock_timestamp()
 THEN RAISE EXCEPTION USING ERRCODE='P3201',MESSAGE='QUOTA_UNCONFIGURED'; END IF;
 SELECT command_id INTO reservation FROM entitlement_private.reservations WHERE tenant_id=NEW.tenant_id AND reference_id=NEW.user_id AND status='RESERVED';
 used:=entitlement_private.measured_users(NEW.tenant_id);
 SELECT count(*) INTO held FROM entitlement_private.reservations WHERE tenant_id=NEW.tenant_id AND status='RESERVED' AND command_id IS DISTINCT FROM reservation;
 IF used>=config.limit_value OR held>=config.limit_value-used THEN RAISE EXCEPTION USING ERRCODE='P3202',MESSAGE='QUOTA_EXHAUSTED'; END IF;
 UPDATE entitlement_private.reservations SET status='CONFIRMED' WHERE tenant_id=NEW.tenant_id AND command_id=reservation;
 RETURN NEW;
END;
$$;
REVOKE ALL ON ALL TABLES IN SCHEMA entitlement_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA entitlement_private,entitlement_api FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION entitlement_api.usage(uuid),entitlement_api.reserve_user(uuid,uuid,uuid),entitlement_api.release_user(uuid,uuid,uuid) TO fiscuvia_runtime;
