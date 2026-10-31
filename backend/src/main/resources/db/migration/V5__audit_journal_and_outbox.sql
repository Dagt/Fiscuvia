-- #29 infrastructure. V1--V4 are immutable. No fiscal model, audit reader or automatic worker.
CREATE SCHEMA journal AUTHORIZATION CURRENT_USER;
CREATE SCHEMA journal_private AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA journal,journal_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT USAGE ON SCHEMA journal TO fiscuvia_runtime;

CREATE TABLE journal_private.command_context (
 transaction_id xid8 PRIMARY KEY, command_id uuid NOT NULL, correlation_id uuid NOT NULL
);
CREATE TABLE journal.events (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), event_id uuid NOT NULL DEFAULT gen_random_uuid(),
 actor_kind text NOT NULL CHECK(actor_kind IN ('USER','DATABASE_ADMIN')),
 actor_id uuid REFERENCES identity_private.users(id) ON DELETE RESTRICT,
 event_code text NOT NULL CHECK(event_code IN ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED',
   'INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED','AUTHORIZED_OPERATION_RECORDED')),
 operation text NOT NULL CHECK(operation IN ('BOOTSTRAP','INVITATION_ACCEPT','TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW',
   'MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE','TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN',
   'TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW')),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(), correlation_id uuid NOT NULL, command_id uuid NOT NULL,
 resource_kind text NOT NULL CHECK(resource_kind IN ('TENANT','TAXPAYER','DOCUMENT','REPORT','AUDIT','INVITATION','MEMBERSHIP')),
 resource_id uuid, outcome text NOT NULL DEFAULT 'SUCCESS' CHECK(outcome='SUCCESS'), schema_version integer NOT NULL DEFAULT 1 CHECK(schema_version=1),
 role_code text CHECK(role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')),
 approval_reference uuid, causation_id uuid,
 PRIMARY KEY(tenant_id,event_id), UNIQUE(tenant_id,command_id),
 FOREIGN KEY(tenant_id,causation_id) REFERENCES journal.events(tenant_id,event_id),
 CHECK((actor_kind='USER' AND actor_id IS NOT NULL) OR (actor_kind='DATABASE_ADMIN' AND actor_id IS NULL))
);
CREATE INDEX journal_time ON journal.events(tenant_id,occurred_at,event_id);
CREATE INDEX journal_category ON journal.events(tenant_id,event_code,occurred_at,event_id);
CREATE TABLE journal.outbox (
 tenant_id uuid NOT NULL, event_id uuid NOT NULL, event_type text NOT NULL DEFAULT 'AUDIT_RECORDED' CHECK(event_type='AUDIT_RECORDED'),
 schema_version integer NOT NULL DEFAULT 1 CHECK(schema_version BETWEEN 1 AND 1000),
 occurred_at timestamptz NOT NULL, correlation_id uuid NOT NULL, causation_id uuid NOT NULL, deduplication_key uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object' AND payload=jsonb_build_object('auditEventId',event_id::text)),
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','DELIVERED','DEAD')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 available_at timestamptz NOT NULL DEFAULT clock_timestamp(), lease_token uuid, leased_until timestamptz,
 completed_at timestamptz,
 failure_code text CHECK(failure_code IN ('RETRYABLE','UNSUPPORTED_VERSION','INVALID_PAYLOAD','LEASE_EXHAUSTED','PERMANENT')),
 PRIMARY KEY(tenant_id,event_id), UNIQUE(tenant_id,deduplication_key),
 FOREIGN KEY(tenant_id,event_id) REFERENCES journal.events(tenant_id,event_id),
 FOREIGN KEY(tenant_id,causation_id) REFERENCES journal.events(tenant_id,event_id),
 CHECK(causation_id=event_id AND deduplication_key=event_id),
 CHECK((status='PENDING' AND lease_token IS NULL AND leased_until IS NULL AND completed_at IS NULL AND attempts<5)
   OR (status='PROCESSING' AND lease_token IS NOT NULL AND leased_until IS NOT NULL AND completed_at IS NULL AND attempts>0)
   OR (status IN ('DELIVERED','DEAD') AND lease_token IS NULL AND leased_until IS NULL AND completed_at IS NOT NULL AND attempts>0))
);
CREATE INDEX outbox_due ON journal.outbox(tenant_id,available_at,occurred_at,event_id) WHERE status='PENDING';
CREATE INDEX outbox_expired ON journal.outbox(tenant_id,leased_until,event_id) WHERE status='PROCESSING';
CREATE TABLE journal_private.commands (
 tenant_id uuid NOT NULL, command_id uuid NOT NULL, actor_id uuid NOT NULL REFERENCES identity_private.users(id),
 operation text NOT NULL, resource_kind text NOT NULL, resource_id uuid, claimed_tx xid8 NOT NULL, event_id uuid,
 PRIMARY KEY(tenant_id,command_id), FOREIGN KEY(tenant_id,event_id) REFERENCES journal.events(tenant_id,event_id)
);
-- Transport authority ONLY. Empty in every production/local installation. No business/identity grants.
-- Provisioning a real transport account/authority still requires owner review, not an application API.
CREATE TABLE journal_private.delivery_scopes (
 role_oid oid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id), approval_reference uuid NOT NULL,
 active boolean NOT NULL DEFAULT true
);
CREATE TABLE journal_private.denials (
 event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_id uuid NOT NULL REFERENCES identity_private.users(id),
 event_code text NOT NULL DEFAULT 'PRIVILEGED_OPERATION_DENIED' CHECK(event_code='PRIVILEGED_OPERATION_DENIED'),
 operation text NOT NULL CHECK(operation IN ('TENANT_SETTINGS_MANAGE','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE','AUDIT_VIEW')),
 reason_code text NOT NULL CHECK(reason_code IN ('MEMBERSHIP_INACTIVE','CAPABILITY_UNCONFIGURED','RESOURCE_UNASSIGNED','MFA_REQUIRED','CONTEXT_REJECTED')),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(), correlation_id uuid NOT NULL, command_id uuid NOT NULL UNIQUE,
 schema_version integer NOT NULL DEFAULT 1 CHECK(schema_version=1), outcome text NOT NULL DEFAULT 'DENY' CHECK(outcome='DENY')
);
CREATE INDEX denial_time ON journal_private.denials(occurred_at,event_id);
REVOKE ALL ON ALL TABLES IN SCHEMA journal,journal_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;

CREATE FUNCTION journal_private.delivery_tenant() RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
 SELECT s.tenant_id FROM journal_private.delivery_scopes s JOIN pg_roles r ON r.oid=s.role_oid
 WHERE r.rolname=SESSION_USER AND s.active AND r.rolname NOT IN ('fiscuvia_runtime','fiscuvia_sessions','fiscuvia_migrator')
   AND NOT(r.rolsuper OR r.rolbypassrls OR r.rolinherit OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication)
   AND NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=r.oid)
   AND NOT has_schema_privilege(r.oid,'app','USAGE') AND NOT has_schema_privilege(r.oid,'identity','USAGE')
$$;
ALTER TABLE journal.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal.events FORCE ROW LEVEL SECURITY;
CREATE POLICY event_owner ON journal.events TO fiscuvia_migrator USING(true) WITH CHECK(true);
CREATE POLICY tenant_fence ON journal.events AS RESTRICTIVE TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
ALTER TABLE journal.outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal.outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_owner ON journal.outbox TO fiscuvia_migrator USING(true) WITH CHECK(true);
CREATE POLICY tenant_fence ON journal.outbox AS RESTRICTIVE TO fiscuvia_migrator
 USING(tenant_id=COALESCE(journal_private.delivery_tenant(),app.current_tenant_id()))
 WITH CHECK(tenant_id=COALESCE(journal_private.delivery_tenant(),app.current_tenant_id()));
ALTER TABLE journal_private.commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_private.commands FORCE ROW LEVEL SECURITY;
CREATE POLICY command_owner ON journal_private.commands TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
-- Global metadata/denials have an explicitly separate private boundary, never a NULL-tenant bypass.
ALTER TABLE journal_private.command_context ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_private.command_context FORCE ROW LEVEL SECURITY;
CREATE POLICY context_owner ON journal_private.command_context TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE journal_private.delivery_scopes ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_private.delivery_scopes FORCE ROW LEVEL SECURITY;
CREATE POLICY scope_owner ON journal_private.delivery_scopes TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE journal_private.denials ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_private.denials FORCE ROW LEVEL SECURITY;
CREATE POLICY denial_owner ON journal_private.denials TO fiscuvia_migrator USING(true) WITH CHECK(true);

CREATE FUNCTION journal_private.immutable_history() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_IMMUTABLE'; END;
$$;
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON journal.events FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON journal.events FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON journal_private.denials FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON journal_private.denials FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();

CREATE FUNCTION journal_private.verify_proof(p_payload text,p_proof text) RETURNS boolean LANGUAGE sql
SET search_path=pg_catalog,pg_temp AS $$
 SELECT CASE WHEN p_proof ~ '^[0-9a-f]{64}$' THEN identity_crypto.digest(identity_crypto.hmac(convert_to(p_payload,'UTF8'),secret,'sha256'),'sha256')
   =identity_crypto.digest(decode(p_proof,'hex'),'sha256') ELSE false END FROM identity_private.proof_key WHERE singleton
$$;
CREATE FUNCTION journal.bind_command(p_command uuid,p_correlation uuid,p_proof text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE tx xid8:=pg_current_xact_id();
BEGIN
 IF p_command IS NULL OR p_correlation IS NULL OR identity.current_actor_id() IS NULL OR NOT COALESCE(journal_private.verify_proof(
   'journal'||E'\n'||tx::text||E'\n'||p_correlation::text||E'\n'||p_command::text,p_proof),false) THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 DELETE FROM journal_private.command_context WHERE transaction_id IN(
   SELECT transaction_id FROM journal_private.command_context WHERE transaction_id<>tx LIMIT 100 FOR UPDATE SKIP LOCKED);
 INSERT INTO journal_private.command_context VALUES(tx,p_command,p_correlation) ON CONFLICT DO NOTHING;
 IF NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=tx AND command_id=p_command AND correlation_id=p_correlation)
   THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
END;
$$;
CREATE FUNCTION journal_private.append_event(p_tenant uuid,p_code text,p_operation text,p_kind text,p_resource uuid,
 p_role text DEFAULT NULL,p_approval uuid DEFAULT NULL,p_cause uuid DEFAULT NULL) RETURNS uuid
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE ctx journal_private.command_context%ROWTYPE; actor uuid:=identity.current_actor_id(); id uuid; existing journal.events%ROWTYPE;
BEGIN
 IF p_tenant IS DISTINCT FROM app.current_tenant_id() OR p_tenant IS NULL THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 SELECT * INTO ctx FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id();
 IF ctx.command_id IS NULL AND SESSION_USER='fiscuvia_migrator' THEN
   INSERT INTO journal_private.command_context VALUES(pg_current_xact_id(),gen_random_uuid(),gen_random_uuid()) RETURNING * INTO ctx;
 END IF;
 IF ctx.command_id IS NULL OR (SESSION_USER='fiscuvia_runtime' AND actor IS NULL) OR SESSION_USER NOT IN ('fiscuvia_runtime','fiscuvia_migrator') THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 INSERT INTO journal.events(tenant_id,actor_kind,actor_id,event_code,operation,correlation_id,command_id,resource_kind,resource_id,role_code,approval_reference,causation_id)
 VALUES(p_tenant,CASE WHEN actor IS NULL THEN 'DATABASE_ADMIN' ELSE 'USER' END,actor,p_code,p_operation,ctx.correlation_id,ctx.command_id,p_kind,p_resource,p_role,p_approval,p_cause)
 ON CONFLICT(tenant_id,command_id) DO NOTHING RETURNING event_id INTO id;
 IF id IS NULL THEN
   SELECT * INTO existing FROM journal.events WHERE tenant_id=p_tenant AND command_id=ctx.command_id;
   IF ROW(existing.actor_id,existing.event_code,existing.operation,existing.resource_kind,existing.resource_id,existing.role_code,existing.approval_reference,existing.causation_id)
      IS DISTINCT FROM ROW(actor,p_code,p_operation,p_kind,p_resource,p_role,p_approval,p_cause) THEN
      RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
   RETURN existing.event_id;
 END IF;
 INSERT INTO journal.outbox(tenant_id,event_id,occurred_at,correlation_id,causation_id,deduplication_key,payload)
 SELECT tenant_id,event_id,occurred_at,correlation_id,event_id,event_id,jsonb_build_object('auditEventId',event_id::text)
 FROM journal.events WHERE tenant_id=p_tenant AND event_id=id;
 RETURN id;
END;
$$;

CREATE FUNCTION journal_private.invitation_event() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE code text; op text; cause uuid;
BEGIN
 IF TG_OP='INSERT' THEN code:='INVITATION_ISSUED';
 ELSIF OLD.consumed_at IS NULL AND NEW.consumed_at IS NOT NULL THEN code:='INVITATION_ACCEPTED';
 ELSIF OLD.revoked_at IS NULL AND NEW.revoked_at IS NOT NULL THEN code:='INVITATION_REVOKED';
 ELSE RETURN NEW; END IF;
 op:=CASE WHEN code='INVITATION_ACCEPTED' THEN 'INVITATION_ACCEPT' ELSE 'MEMBERSHIP_INVITE' END;
 SELECT event_id INTO cause FROM journal.events WHERE tenant_id=NEW.tenant_id AND resource_id=NEW.id AND event_code='INVITATION_ISSUED';
 PERFORM journal_private.append_event(NEW.tenant_id,code,op,'INVITATION',NEW.id,NEW.role_code,NULL,cause);
 RETURN NEW;
END;
$$;
CREATE TRIGGER audit_invitation AFTER INSERT OR UPDATE ON identity.invitations FOR EACH ROW EXECUTE FUNCTION journal_private.invitation_event();
CREATE FUNCTION journal_private.membership_event() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status AND NEW.status IN ('SUSPENDED','REVOKED') THEN
   PERFORM journal_private.append_event(NEW.tenant_id,'MEMBERSHIP_'||NEW.status,'MEMBERSHIP_DISABLE','MEMBERSHIP',NEW.id);
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER audit_membership AFTER UPDATE ON identity.memberships FOR EACH ROW EXECUTE FUNCTION journal_private.membership_event();
CREATE FUNCTION journal_private.bootstrap_event() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE approval uuid;
BEGIN
 IF NEW.event_code='ACTIVE' THEN
   SELECT a.approval_reference INTO approval FROM identity_private.owner_approvals a JOIN identity.membership_roles r ON r.tenant_id=a.tenant_id
    WHERE a.tenant_id=NEW.tenant_id AND a.user_id=NEW.actor_id AND r.membership_id=NEW.membership_id AND r.role_code='FIRM_OWNER_ADMIN';
   IF approval IS NOT NULL THEN
     IF SESSION_USER<>'fiscuvia_migrator' THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='BOOTSTRAP_REJECTED'; END IF;
     PERFORM journal_private.append_event(NEW.tenant_id,'OWNER_BOOTSTRAPPED','BOOTSTRAP','MEMBERSHIP',NEW.membership_id,'FIRM_OWNER_ADMIN',approval);
   END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER audit_bootstrap AFTER INSERT ON identity_private.membership_events FOR EACH ROW EXECUTE FUNCTION journal_private.bootstrap_event();

CREATE FUNCTION journal.begin_command(p_command uuid) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c access_control_private.operation_context%ROWTYPE; prior journal_private.commands%ROWTYPE;
BEGIN
 SELECT * INTO c FROM access_control_private.operation_context WHERE transaction_id=pg_current_xact_id_if_assigned();
 IF c.actor_id IS NULL OR c.actor_id IS DISTINCT FROM identity.current_actor_id() OR c.tenant_id IS DISTINCT FROM app.current_tenant_id()
   OR NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=c.transaction_id AND command_id=p_command) THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 INSERT INTO journal_private.commands VALUES(c.tenant_id,p_command,c.actor_id,c.capability,c.resource_kind,c.resource_id,c.transaction_id,NULL)
 ON CONFLICT DO NOTHING;
 SELECT * INTO prior FROM journal_private.commands WHERE tenant_id=c.tenant_id AND command_id=p_command FOR UPDATE;
 IF ROW(prior.actor_id,prior.operation,prior.resource_kind,prior.resource_id) IS DISTINCT FROM ROW(c.actor_id,c.capability,c.resource_kind,c.resource_id) THEN
   RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN prior.event_id;
END;
$$;
CREATE FUNCTION journal.finish_command(p_command uuid) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c journal_private.commands%ROWTYPE; id uuid;
BEGIN
 SELECT * INTO c FROM journal_private.commands WHERE tenant_id=app.current_tenant_id() AND command_id=p_command FOR UPDATE;
 IF c.actor_id IS DISTINCT FROM identity.current_actor_id() OR c.claimed_tx IS DISTINCT FROM pg_current_xact_id_if_assigned() OR c.event_id IS NOT NULL THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 id:=journal_private.append_event(c.tenant_id,'AUTHORIZED_OPERATION_RECORDED',c.operation,c.resource_kind,c.resource_id);
 UPDATE journal_private.commands SET event_id=id WHERE tenant_id=c.tenant_id AND command_id=p_command;
 RETURN id;
END;
$$;
CREATE FUNCTION journal_private.require_completed_command() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM journal_private.commands WHERE tenant_id=NEW.tenant_id AND command_id=NEW.command_id AND event_id IS NOT NULL) THEN
   RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='COMMAND_INCOMPLETE'; END IF;
 RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER command_complete AFTER INSERT ON journal_private.commands DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION journal_private.require_completed_command();

CREATE FUNCTION journal.record_denial(p_command uuid,p_correlation uuid,p_operation text,p_reason text,p_proof text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id();
BEGIN
 IF actor IS NULL OR p_command IS NULL OR p_correlation IS NULL OR NOT COALESCE(journal_private.verify_proof(
   'denial'||E'\n'||pg_current_xact_id()::text||E'\n'||actor::text||E'\n'||p_command::text||E'\n'||p_correlation::text||E'\n'||p_operation||E'\n'||p_reason,p_proof),false) THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 INSERT INTO journal_private.denials(actor_id,operation,reason_code,correlation_id,command_id) VALUES(actor,p_operation,p_reason,p_correlation,p_command)
 ON CONFLICT(command_id) DO NOTHING;
END;
$$;

-- No runtime/table reader and no worker roles provisioned. Explicit role/tenant transport scopes
-- and function grants are required before this protocol can be called, even by the application.
CREATE FUNCTION journal.claim(p_limit integer,p_lease_ms integer)
RETURNS SETOF journal.outbox LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE tenant uuid:=journal_private.delivery_tenant();
BEGIN
 IF tenant IS NULL OR p_limit IS NULL OR p_lease_ms IS NULL OR p_limit NOT BETWEEN 1 AND 100 OR p_lease_ms NOT BETWEEN 100 AND 300000 THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='DELIVERY_DENIED'; END IF;
 WITH exhausted AS (SELECT event_id FROM journal.outbox WHERE tenant_id=tenant AND status='PROCESSING' AND attempts=5
   AND leased_until<=clock_timestamp() ORDER BY leased_until,event_id LIMIT p_limit FOR UPDATE SKIP LOCKED)
 UPDATE journal.outbox o SET status='DEAD',lease_token=NULL,leased_until=NULL,completed_at=clock_timestamp(),failure_code='LEASE_EXHAUSTED'
 FROM exhausted e WHERE o.tenant_id=tenant AND o.event_id=e.event_id;
 RETURN QUERY WITH eligible AS (SELECT event_id FROM journal.outbox WHERE tenant_id=tenant AND attempts<5 AND
   ((status='PENDING' AND available_at<=clock_timestamp()) OR (status='PROCESSING' AND leased_until<=clock_timestamp()))
   ORDER BY CASE WHEN status='PROCESSING' THEN leased_until ELSE available_at END,occurred_at,event_id LIMIT p_limit FOR UPDATE SKIP LOCKED)
 UPDATE journal.outbox o SET status='PROCESSING',attempts=attempts+1,lease_token=gen_random_uuid(),leased_until=clock_timestamp()+p_lease_ms*interval '1 millisecond'
 FROM eligible e WHERE o.tenant_id=tenant AND o.event_id=e.event_id RETURNING o.*;
END;
$$;
CREATE FUNCTION journal.lock_delivery(p_event uuid,p_token uuid) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM 1 FROM journal.outbox WHERE tenant_id=journal_private.delivery_tenant() AND event_id=p_event AND lease_token=p_token
   AND status='PROCESSING' AND leased_until>clock_timestamp() FOR UPDATE;
 RETURN FOUND;
END;
$$;
CREATE FUNCTION journal.complete_delivery(p_event uuid,p_token uuid,p_failure text DEFAULT NULL) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF p_failure IS NOT NULL AND p_failure NOT IN ('RETRYABLE','UNSUPPORTED_VERSION','INVALID_PAYLOAD','PERMANENT') THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='DELIVERY_DENIED'; END IF;
 UPDATE journal.outbox SET status=CASE WHEN p_failure IS NULL THEN 'DELIVERED' WHEN p_failure<>'RETRYABLE' OR attempts>=5 THEN 'DEAD' ELSE 'PENDING' END,
  completed_at=CASE WHEN p_failure IS NULL OR p_failure<>'RETRYABLE' OR attempts>=5 THEN clock_timestamp() ELSE NULL END,
  available_at=CASE WHEN p_failure='RETRYABLE' THEN clock_timestamp()+least(60,power(2,attempts-1))::integer*interval '1 second' ELSE available_at END,
  lease_token=NULL,leased_until=NULL,failure_code=p_failure
 WHERE tenant_id=journal_private.delivery_tenant() AND event_id=p_event AND lease_token=p_token AND status='PROCESSING' AND leased_until>clock_timestamp();
 RETURN FOUND;
END;
$$;
CREATE FUNCTION journal.read_delivery(p_event uuid,p_token uuid) RETURNS TABLE(schema_version integer)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT schema_version FROM journal.outbox WHERE tenant_id=journal_private.delivery_tenant() AND event_id=p_event
 AND lease_token=p_token AND status='PROCESSING' AND leased_until>clock_timestamp()
$$;
CREATE FUNCTION journal_private.guard_outbox() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='OUTBOX_IMMUTABLE'; END IF;
 IF OLD.status IN ('DELIVERED','DEAD') OR ROW(NEW.tenant_id,NEW.event_id,NEW.event_type,NEW.schema_version,NEW.occurred_at,
   NEW.correlation_id,NEW.causation_id,NEW.deduplication_key,NEW.payload) IS DISTINCT FROM ROW(OLD.tenant_id,OLD.event_id,OLD.event_type,
   OLD.schema_version,OLD.occurred_at,OLD.correlation_id,OLD.causation_id,OLD.deduplication_key,OLD.payload) THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='OUTBOX_IMMUTABLE'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_intent BEFORE UPDATE OR DELETE ON journal.outbox FOR EACH ROW EXECUTE FUNCTION journal_private.guard_outbox();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON journal.outbox FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA journal,journal_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION journal.bind_command(uuid,uuid,text),journal.begin_command(uuid),journal.finish_command(uuid),
 journal.record_denial(uuid,uuid,text,text,text) TO fiscuvia_runtime;
