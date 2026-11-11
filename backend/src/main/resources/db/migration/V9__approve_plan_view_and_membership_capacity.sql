-- Limited owner approval: https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955
-- No commercial approval, fiscal grants or changes to V1--V8.
INSERT INTO access_control_private.role_capabilities VALUES
 ('FIRM_OWNER_ADMIN','TENANT_PLAN_VIEW','TENANT',true,'https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955');

CREATE OR REPLACE FUNCTION entitlement_private.membership_capacity() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE config entitlement_private.limits%ROWTYPE; used bigint; held bigint; reservation uuid;
BEGIN
 IF TG_OP='UPDATE' THEN
 IF NEW.tenant_id<>OLD.tenant_id OR NEW.user_id<>OLD.user_id THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='MEMBERSHIP_IMMUTABLE'; END IF;
 IF NEW.status<>'ACTIVE' OR OLD.status='ACTIVE' THEN RETURN NEW; END IF;
 ELSIF NEW.status<>'ACTIVE' THEN RETURN NEW; END IF;
 IF NEW.tenant_id IS DISTINCT FROM app.current_tenant_id() THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 PERFORM 1 FROM tenant_private.profiles WHERE tenant_id=NEW.tenant_id AND status='ACTIVE' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 SELECT * INTO config FROM entitlement_private.limits WHERE tenant_id=NEW.tenant_id AND resource='USERS' FOR UPDATE;
 -- Approved bootstrap remains possible before any limits are assigned; no tenant self-provisioning.
 IF config.tenant_id IS NULL AND TG_OP='INSERT' AND SESSION_USER='fiscuvia_migrator'
 AND identity.current_actor_id() IS NULL
 AND NOT EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=NEW.tenant_id)
 AND EXISTS(SELECT 1 FROM identity_private.owner_approvals WHERE tenant_id=NEW.tenant_id AND user_id=NEW.user_id)
 THEN RETURN NEW; END IF;
 IF config.tenant_id IS NULL OR config.valid_from>clock_timestamp() OR config.valid_until<=clock_timestamp()
 THEN RAISE EXCEPTION USING ERRCODE='P3201',MESSAGE='QUOTA_UNCONFIGURED'; END IF;
 SELECT command_id INTO reservation FROM entitlement_private.reservations WHERE tenant_id=NEW.tenant_id AND reference_id=NEW.user_id AND resource='USERS' AND status='RESERVED';
 used:=entitlement_private.measured_users(NEW.tenant_id);
 SELECT count(*) INTO held FROM entitlement_private.reservations WHERE tenant_id=NEW.tenant_id AND resource='USERS' AND status='RESERVED' AND command_id IS DISTINCT FROM reservation;
 IF used>=config.limit_value OR held>=config.limit_value-used THEN RAISE EXCEPTION USING ERRCODE='P3202',MESSAGE='QUOTA_EXHAUSTED'; END IF;
 UPDATE entitlement_private.reservations SET status='CONFIRMED' WHERE tenant_id=NEW.tenant_id AND command_id=reservation;
 RETURN NEW;
END;
$$;

CREATE TRIGGER entitlement_capacity BEFORE INSERT OR UPDATE ON identity.memberships
 FOR EACH ROW EXECUTE FUNCTION entitlement_private.membership_capacity();

CREATE FUNCTION entitlement_api.approved_view_policy() RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
 SELECT (SELECT count(*) FROM access_control_private.role_capabilities WHERE capability='TENANT_PLAN_VIEW')=1
 AND EXISTS(SELECT 1 FROM access_control_private.role_capabilities WHERE capability='TENANT_PLAN_VIEW'
 AND role_code='FIRM_OWNER_ADMIN' AND resource_kind='TENANT' AND requires_mfa
 AND approval_reference='https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955')
$$;
REVOKE ALL ON FUNCTION entitlement_api.approved_view_policy() FROM PUBLIC,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION entitlement_api.approved_view_policy() TO fiscuvia_runtime;

ALTER TABLE journal.events DROP CONSTRAINT events_event_code_check;
ALTER TABLE journal.events ADD CONSTRAINT events_event_code_check CHECK(event_code IN
 ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED','INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED',
 'AUTHORIZED_OPERATION_RECORDED','TENANT_CREATED','TENANT_PROFILE_UPDATED','TENANT_SUSPENDED','TENANT_REACTIVATED','TENANT_QUOTA_CHANGED','QUOTA_RESERVED','QUOTA_RELEASED','MEMBERSHIP_REACTIVATED'));
ALTER TABLE journal.events DROP CONSTRAINT events_operation_check;
ALTER TABLE journal.events ADD CONSTRAINT events_operation_check CHECK(operation IN
 ('BOOTSTRAP','INVITATION_ACCEPT','TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE',
 'TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW',
 'TENANT_PROVISION','TENANT_SUSPEND','TENANT_REACTIVATE','TENANT_QUOTA_CHANGE','MEMBERSHIP_REACTIVATE'));


-- The same resource mutation which acquires capacity records reactivation atomically.
-- No new HTTP reactivation authority is granted; team administration remains #33.
CREATE OR REPLACE FUNCTION journal_private.membership_event() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status THEN
   IF NEW.status='ACTIVE' THEN
     PERFORM journal_private.append_event(NEW.tenant_id,'MEMBERSHIP_REACTIVATED','MEMBERSHIP_REACTIVATE','MEMBERSHIP',NEW.id);
   ELSIF NEW.status IN ('SUSPENDED','REVOKED') THEN
     PERFORM journal_private.append_event(NEW.tenant_id,'MEMBERSHIP_'||NEW.status,'MEMBERSHIP_DISABLE','MEMBERSHIP',NEW.id);
   END IF;
 END IF;
 RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION entitlement_private.membership_capacity(),journal_private.membership_event() FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
