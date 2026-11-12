-- #34. D74-01..05 approved by Dagt, 2026-11-12, PR #92. V1--V11 immutable.
-- No assignment/review endpoint, SAT, catalogue, deadlines or business worker.
CREATE SCHEMA taxpayers AUTHORIZATION CURRENT_USER;
CREATE SCHEMA taxpayer_private AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA taxpayers,taxpayer_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT USAGE ON SCHEMA taxpayers TO fiscuvia_runtime;

CREATE FUNCTION taxpayer_private.valid_rfc(rfc text,person text) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE prefix integer;
BEGIN
 IF rfc IS NULL OR person IS NULL OR rfc IN ('XAXX010101000','XEXX010101000') THEN RETURN false; END IF;
 prefix:=CASE person WHEN 'INDIVIDUAL' THEN 4 WHEN 'LEGAL_ENTITY' THEN 3 ELSE 0 END;
 IF prefix=0 OR (prefix=4 AND rfc COLLATE "C" !~ '^[A-ZÑ&]{4}[0-9]{6}[A-Z0-9]{3}$')
 OR (prefix=3 AND rfc COLLATE "C" !~ '^[A-ZÑ&]{3}[0-9]{6}[A-Z0-9]{3}$') THEN RETURN false; END IF;
 PERFORM make_date(2000+substring(rfc,prefix+1,2)::integer,substring(rfc,prefix+3,2)::integer,substring(rfc,prefix+5,2)::integer);
 RETURN true;
EXCEPTION WHEN datetime_field_overflow OR invalid_datetime_format THEN RETURN false;
END; $$;
CREATE TABLE taxpayers.taxpayers (
 tenant_id uuid NOT NULL REFERENCES app.tenants(tenant_id) ON DELETE RESTRICT,id uuid NOT NULL,
 rfc_normalized varchar(13) NOT NULL,person_type varchar(12) NOT NULL CHECK(person_type IN ('INDIVIDUAL','LEGAL_ENTITY')),
 legal_name varchar(300) NOT NULL CHECK(length(btrim(legal_name)) BETWEEN 1 AND 300 AND legal_name !~ '[[:cntrl:]]'),
 trade_name varchar(200) CHECK(trade_name IS NULL OR (length(btrim(trade_name)) BETWEEN 1 AND 200 AND trade_name !~ '[[:cntrl:]]')),
 status varchar(8) NOT NULL CHECK(status IN ('ACTIVE','ARCHIVED')),version bigint NOT NULL CHECK(version BETWEEN 0 AND 9007199254740991),
 created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_membership_id uuid NOT NULL,updated_by_membership_id uuid NOT NULL,
 archived_at timestamptz,archived_by_membership_id uuid,
 PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,rfc_normalized),
 FOREIGN KEY(tenant_id,created_by_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,updated_by_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,archived_by_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 CHECK(taxpayer_private.valid_rfc(rfc_normalized,person_type)),
 CHECK((status='ACTIVE' AND archived_at IS NULL AND archived_by_membership_id IS NULL)
 OR (status='ARCHIVED' AND archived_at IS NOT NULL AND archived_by_membership_id IS NOT NULL))
);
CREATE INDEX taxpayer_directory ON taxpayers.taxpayers(tenant_id,status,legal_name,id);
CREATE TABLE taxpayers.taxpayer_revisions (
 tenant_id uuid NOT NULL,taxpayer_id uuid NOT NULL,version bigint NOT NULL CHECK(version BETWEEN 0 AND 9007199254740991),
 command_id uuid NOT NULL,changed_at timestamptz NOT NULL,actor_membership_id uuid NOT NULL,
 change_kind varchar(16) NOT NULL CHECK(change_kind IN ('CREATE','UPDATE_PROFILE','ARCHIVE','RESTORE')),reason_code varchar(20),
 rfc_normalized varchar(13) NOT NULL,person_type varchar(12) NOT NULL,legal_name varchar(300) NOT NULL,trade_name varchar(200),status varchar(8) NOT NULL,
 created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,created_by_membership_id uuid NOT NULL,updated_by_membership_id uuid NOT NULL,
 archived_at timestamptz,archived_by_membership_id uuid,
 PRIMARY KEY(tenant_id,taxpayer_id,version),
 FOREIGN KEY(tenant_id,taxpayer_id) REFERENCES taxpayers.taxpayers(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,actor_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE taxpayers.taxpayer_regime_references (
 tenant_id uuid NOT NULL,taxpayer_id uuid NOT NULL,taxpayer_version bigint NOT NULL,ordinal smallint NOT NULL CHECK(ordinal BETWEEN 0 AND 9),
 regime_code varchar(3) NOT NULL CHECK(regime_code ~ '^[0-9]{3}$'),valid_from date,valid_to date,
 source varchar(6) NOT NULL CHECK(source='MANUAL'),review_status varchar(14) NOT NULL CHECK(review_status='PENDING_REVIEW'),
 recorded_at timestamptz NOT NULL,recorded_by_membership_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,taxpayer_id,taxpayer_version,ordinal),
 FOREIGN KEY(tenant_id,taxpayer_id,taxpayer_version) REFERENCES taxpayers.taxpayer_revisions(tenant_id,taxpayer_id,version) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,recorded_by_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 CHECK((valid_from IS NULL AND valid_to IS NULL) OR (valid_from IS NOT NULL AND (valid_to IS NULL OR valid_to>valid_from)))
);
CREATE TABLE taxpayers.taxpayer_assignments (
 tenant_id uuid NOT NULL,id uuid NOT NULL,taxpayer_id uuid NOT NULL,membership_id uuid NOT NULL,
 assignment_type varchar(7) NOT NULL CHECK(assignment_type='PRIMARY'),started_at timestamptz NOT NULL,ended_at timestamptz,
 created_command_id uuid NOT NULL,ended_command_id uuid,
 PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,taxpayer_id,id),
 FOREIGN KEY(tenant_id,taxpayer_id) REFERENCES taxpayers.taxpayers(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,created_command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(tenant_id,ended_command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT,
 CHECK((ended_at IS NULL AND ended_command_id IS NULL) OR (ended_at>started_at AND ended_command_id IS NOT NULL))
);
CREATE UNIQUE INDEX taxpayer_primary ON taxpayers.taxpayer_assignments(tenant_id,taxpayer_id) WHERE ended_at IS NULL;
CREATE INDEX taxpayer_member ON taxpayers.taxpayer_assignments(tenant_id,membership_id,taxpayer_id);
CREATE FUNCTION taxpayer_private.reject_regime_overlap() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM taxpayers.taxpayer_regime_references r WHERE r.tenant_id=NEW.tenant_id
 AND r.taxpayer_id=NEW.taxpayer_id AND r.taxpayer_version=NEW.taxpayer_version AND r.regime_code=NEW.regime_code
 AND daterange(r.valid_from,r.valid_to,'[)') && daterange(NEW.valid_from,NEW.valid_to,'[)'))
 THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='REGIME_OVERLAP'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER regime_overlap BEFORE INSERT ON taxpayers.taxpayer_regime_references
 FOR EACH ROW EXECUTE FUNCTION taxpayer_private.reject_regime_overlap();
ALTER TABLE access_control.resource_grants ADD COLUMN taxpayer_id uuid GENERATED ALWAYS AS
 (CASE WHEN resource_kind='TAXPAYER' THEN resource_id ELSE NULL END) STORED;
ALTER TABLE access_control.resource_grants ADD CONSTRAINT grant_taxpayer_fk FOREIGN KEY(tenant_id,taxpayer_id)
 REFERENCES taxpayers.taxpayers(tenant_id,id) ON DELETE RESTRICT;
CREATE TABLE taxpayer_private.grant_reviews (
 tenant_id uuid NOT NULL,membership_id uuid NOT NULL,resource_kind varchar(10) NOT NULL CHECK(resource_kind='TAXPAYER'),
 capability varchar(32) NOT NULL,resource_id uuid NOT NULL,review_sequence bigint NOT NULL CHECK(review_sequence BETWEEN 1 AND 9007199254740991),
 decision varchar(7) NOT NULL CHECK(decision IN ('ENABLE','DISABLE')),reviewed_membership_version bigint NOT NULL CHECK(reviewed_membership_version BETWEEN 0 AND 9007199254740991),
 reviewed_role_code varchar(20) NOT NULL CHECK(reviewed_role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')),
 reviewed_by_membership_id uuid NOT NULL,reviewed_at timestamptz NOT NULL,command_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,membership_id,resource_kind,capability,resource_id,review_sequence),
 UNIQUE(tenant_id,membership_id,resource_kind,capability,resource_id,command_id),
 FOREIGN KEY(tenant_id,membership_id,resource_kind,capability,resource_id)
 REFERENCES access_control.resource_grants(tenant_id,membership_id,resource_kind,capability,resource_id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,reviewed_by_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,resource_id) REFERENCES taxpayers.taxpayers(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE taxpayer_private.commands (
 tenant_id uuid NOT NULL,command_id uuid NOT NULL,actor_membership_id uuid NOT NULL,operation varchar(16) NOT NULL,
 target_id uuid NOT NULL,fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),result_version bigint NOT NULL CHECK(result_version BETWEEN 0 AND 9007199254740991),
 event_id uuid NOT NULL,recorded_at timestamptz NOT NULL,
 PRIMARY KEY(tenant_id,command_id),
 FOREIGN KEY(tenant_id,target_id) REFERENCES taxpayers.taxpayers(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,actor_membership_id) REFERENCES identity.memberships(tenant_id,id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,event_id) REFERENCES journal.events(tenant_id,event_id) ON DELETE RESTRICT,
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id) ON DELETE RESTRICT,
 CHECK(operation IN ('CREATE','UPDATE_PROFILE','ARCHIVE','RESTORE'))
);
CREATE TABLE taxpayer_private.operation_scope (
 transaction_id xid8 PRIMARY KEY,tenant_id uuid NOT NULL,taxpayer_id uuid,capability varchar(32) NOT NULL,
 purpose varchar(7) NOT NULL CHECK(purpose IN ('WRITE','MEASURE'))
);
ALTER TABLE taxpayer_private.operation_scope ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayer_private.operation_scope FORCE ROW LEVEL SECURITY;
CREATE POLICY scope_owner ON taxpayer_private.operation_scope TO fiscuvia_migrator USING(true) WITH CHECK(true);
ALTER TABLE taxpayer_private.grant_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayer_private.grant_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY reviews_owner ON taxpayer_private.grant_reviews TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id() OR EXISTS(SELECT 1 FROM identity.memberships m
 WHERE m.tenant_id=grant_reviews.tenant_id AND m.id=grant_reviews.membership_id AND m.user_id=identity.current_actor_id()))
 WITH CHECK(tenant_id=app.current_tenant_id());
ALTER TABLE taxpayer_private.commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayer_private.commands FORCE ROW LEVEL SECURITY;
CREATE POLICY commands_owner ON taxpayer_private.commands TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());

CREATE FUNCTION taxpayer_private.grant_enabled(t uuid,m uuid,c text,r uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT EXISTS(SELECT 1 FROM identity.memberships mem
 JOIN identity.membership_roles role ON role.tenant_id=mem.tenant_id AND role.membership_id=mem.id
 JOIN LATERAL (SELECT * FROM taxpayer_private.grant_reviews review WHERE review.tenant_id=t AND review.membership_id=m
 AND review.resource_kind='TAXPAYER' AND review.capability=c AND review.resource_id=r ORDER BY review.review_sequence DESC LIMIT 1) last ON true
 WHERE mem.tenant_id=t AND mem.id=m AND mem.user_id=identity.current_actor_id() AND mem.status='ACTIVE'
 AND last.decision='ENABLE' AND last.reviewed_membership_version=mem.version AND last.reviewed_role_code=role.role_code
 AND (SELECT count(*) FROM identity.membership_roles all_roles WHERE all_roles.tenant_id=t AND all_roles.membership_id=m)=1)
$$;
CREATE FUNCTION taxpayer_private.row_allowed(t uuid,r uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT t=app.current_tenant_id() AND (
 (SESSION_USER='fiscuvia_migrator' AND identity.current_actor_id() IS NULL)
 OR access_control.resource_allowed(t,'TAXPAYER',r,'TAXPAYER_VIEW')
 OR EXISTS(SELECT 1 FROM taxpayer_private.operation_scope s WHERE s.transaction_id=pg_current_xact_id_if_assigned()
 AND s.tenant_id=t AND (s.purpose='MEASURE' OR (s.purpose='WRITE' AND s.taxpayer_id=r))))
$$;

ALTER TABLE taxpayers.taxpayers ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayers.taxpayers FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON taxpayers.taxpayers AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON taxpayers.taxpayers AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(taxpayer_private.row_allowed(tenant_id,id)) WITH CHECK(taxpayer_private.row_allowed(tenant_id,id));
CREATE POLICY owner_command ON taxpayers.taxpayers TO fiscuvia_migrator USING(true) WITH CHECK(true);

ALTER TABLE taxpayers.taxpayer_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayers.taxpayer_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON taxpayers.taxpayer_revisions AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON taxpayers.taxpayer_revisions AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(taxpayer_private.row_allowed(tenant_id,taxpayer_id)) WITH CHECK(taxpayer_private.row_allowed(tenant_id,taxpayer_id));
CREATE POLICY owner_command ON taxpayers.taxpayer_revisions TO fiscuvia_migrator USING(true) WITH CHECK(true);

ALTER TABLE taxpayers.taxpayer_regime_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayers.taxpayer_regime_references FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON taxpayers.taxpayer_regime_references AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON taxpayers.taxpayer_regime_references AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(taxpayer_private.row_allowed(tenant_id,taxpayer_id)) WITH CHECK(taxpayer_private.row_allowed(tenant_id,taxpayer_id));
CREATE POLICY owner_command ON taxpayers.taxpayer_regime_references TO fiscuvia_migrator USING(true) WITH CHECK(true);

ALTER TABLE taxpayers.taxpayer_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE taxpayers.taxpayer_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON taxpayers.taxpayer_assignments AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
CREATE POLICY resource_fence ON taxpayers.taxpayer_assignments AS RESTRICTIVE TO fiscuvia_runtime,fiscuvia_migrator
 USING(taxpayer_private.row_allowed(tenant_id,taxpayer_id)) WITH CHECK(taxpayer_private.row_allowed(tenant_id,taxpayer_id));
CREATE POLICY owner_command ON taxpayers.taxpayer_assignments TO fiscuvia_migrator USING(true) WITH CHECK(true);
CREATE POLICY taxpayer_read ON taxpayers.taxpayers FOR SELECT TO fiscuvia_runtime USING(true);
CREATE POLICY regime_read ON taxpayers.taxpayer_regime_references FOR SELECT TO fiscuvia_runtime USING(true);
GRANT SELECT ON taxpayers.taxpayers,taxpayers.taxpayer_regime_references TO fiscuvia_runtime;
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON taxpayers.taxpayer_revisions FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayers.taxpayer_revisions FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON taxpayers.taxpayer_regime_references FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayers.taxpayer_regime_references FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON taxpayers.taxpayer_assignments FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayers.taxpayer_assignments FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON taxpayer_private.grant_reviews FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayer_private.grant_reviews FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON taxpayer_private.commands FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayer_private.commands FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_delete BEFORE DELETE ON taxpayers.taxpayers FOR EACH ROW EXECUTE FUNCTION journal_private.immutable_history();
CREATE TRIGGER no_truncate BEFORE TRUNCATE ON taxpayers.taxpayers FOR EACH STATEMENT EXECUTE FUNCTION journal_private.immutable_history();
ALTER TABLE access_control_private.role_capabilities DROP CONSTRAINT role_capabilities_capability_check;
ALTER TABLE access_control_private.role_capabilities ADD CONSTRAINT role_capabilities_capability_check CHECK(capability IN
 ('TENANT_READ','TENANT_SETTINGS_MANAGE','TENANT_PLAN_VIEW','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE',
 'MEMBERSHIP_DISABLE','MEMBERSHIP_REACTIVATE','TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ARCHIVE','TAXPAYER_ASSIGN','TAXPAYER_EXPORT',
 'DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW'));
ALTER TABLE access_control.resource_grants DROP CONSTRAINT resource_grants_capability_check;
ALTER TABLE access_control.resource_grants ADD CONSTRAINT resource_grants_capability_check CHECK(capability IN
 ('TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ARCHIVE','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW'));
INSERT INTO access_control_private.role_capabilities VALUES
 ('FIRM_OWNER_ADMIN','TAXPAYER_CREATE','TENANT',true,'Owner approval D74-03 / PR #92 / 2026-11-12'),
 ('FIRM_OWNER_ADMIN','TAXPAYER_ARCHIVE','TAXPAYER',true,'Owner approval D74-03 / PR #92 / 2026-11-12');
INSERT INTO access_control_private.role_capabilities
 SELECT r,c,'TAXPAYER',false,'Owner approval D74-03 / PR #92 / 2026-11-12'
 FROM unnest(ARRAY['FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT']) r
 CROSS JOIN unnest(ARRAY['TAXPAYER_VIEW','TAXPAYER_EDIT']) c;
-- ASSIGN deliberately has no operational role policy in #34.
ALTER TABLE journal.events DROP CONSTRAINT events_event_code_check;
ALTER TABLE journal.events ADD CONSTRAINT events_event_code_check CHECK(event_code IN ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED','INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED',
 'AUTHORIZED_OPERATION_RECORDED','TENANT_CREATED','TENANT_PROFILE_UPDATED','TENANT_SUSPENDED','TENANT_REACTIVATED',
 'TENANT_QUOTA_CHANGED','QUOTA_RESERVED','QUOTA_RELEASED','MEMBERSHIP_REACTIVATED','MEMBERSHIP_ROLE_CHANGED','TAXPAYER_CREATED','TAXPAYER_PROFILE_UPDATED','TAXPAYER_ARCHIVED','TAXPAYER_RESTORED'));
ALTER TABLE journal.events DROP CONSTRAINT events_operation_check;
ALTER TABLE journal.events ADD CONSTRAINT events_operation_check CHECK(operation IN ('BOOTSTRAP','INVITATION_ACCEPT','TENANT_READ','TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE',
 'TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT','DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW',
 'TENANT_PROVISION','TENANT_SUSPEND','TENANT_REACTIVATE','TENANT_QUOTA_CHANGE','TAXPAYER_ARCHIVE','MEMBERSHIP_REACTIVATE'));
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
    IF p_capability IN ('TENANT_SETTINGS_MANAGE','MEMBERSHIP_DISABLE','MEMBERSHIP_REACTIVATE','MEMBERSHIP_ASSIGN_ROLE') THEN
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
          AND (p_resource IS NULL OR g.resource_id=p_resource)
          AND (p_kind<>'TAXPAYER' OR taxpayer_private.grant_enabled(p_tenant,member,p_capability,g.resource_id)) ORDER BY g.resource_id FOR SHARE) locked;
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


CREATE OR REPLACE FUNCTION access_control.resource_allowed(p_tenant uuid,p_kind text,p_resource uuid,p_capability text)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
    SELECT EXISTS(SELECT 1 FROM access_control_private.operation_context c JOIN access_control.resource_grants g
      ON g.tenant_id=c.tenant_id AND g.membership_id=c.membership_id AND g.resource_kind=c.resource_kind AND g.capability=c.capability
      WHERE c.transaction_id=pg_current_xact_id_if_assigned() AND c.actor_id=identity.current_actor_id()
        AND c.tenant_id=app.current_tenant_id() AND c.tenant_id=p_tenant AND c.resource_kind=p_kind AND c.capability=p_capability
        AND (c.resource_id IS NULL OR c.resource_id=p_resource) AND p_resource=ANY(c.authorized_resources)
        AND g.resource_id=p_resource AND g.active
        AND (p_kind<>'TAXPAYER' OR taxpayer_private.grant_enabled(p_tenant,c.membership_id,p_capability,p_resource)))
$$;

CREATE FUNCTION taxpayer_private.require_command(t uuid,cap text,r uuid) RETURNS uuid
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE member uuid;
BEGIN
 SELECT c.membership_id INTO member FROM access_control_private.operation_context c
 WHERE c.transaction_id=pg_current_xact_id_if_assigned() AND c.actor_id=identity.current_actor_id()
 AND c.tenant_id=t AND t=app.current_tenant_id() AND c.capability=cap
 AND ((cap='TAXPAYER_CREATE' AND c.resource_kind='TENANT' AND c.resource_id IS NULL AND r IS NULL)
 OR (cap IN ('TAXPAYER_EDIT','TAXPAYER_ARCHIVE') AND c.resource_kind='TAXPAYER' AND c.resource_id=r
 AND access_control.resource_allowed(t,'TAXPAYER',r,cap)));
 IF member IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 IF cap IN ('TAXPAYER_CREATE','TAXPAYER_ARCHIVE') THEN PERFORM identity.authorize_tenant(t,true); END IF;
 RETURN member;
END; $$;
CREATE FUNCTION taxpayer_private.set_write_scope(t uuid,r uuid,cap text) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 DELETE FROM taxpayer_private.operation_scope WHERE transaction_id IN
 (SELECT transaction_id FROM taxpayer_private.operation_scope WHERE transaction_id<>pg_current_xact_id() LIMIT 100 FOR UPDATE SKIP LOCKED);
 INSERT INTO taxpayer_private.operation_scope VALUES(pg_current_xact_id(),t,r,cap,'WRITE') ON CONFLICT(transaction_id)
 DO UPDATE SET tenant_id=EXCLUDED.tenant_id,taxpayer_id=EXCLUDED.taxpayer_id,capability=EXCLUDED.capability,purpose='WRITE';
END; $$;
-- Internal scalar measurement. Caller must already have the approved operation context.
CREATE FUNCTION taxpayer_private.measured_taxpayers(t uuid) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE prior taxpayer_private.operation_scope%ROWTYPE; used bigint; cap text;
BEGIN
 SELECT c.capability INTO cap FROM access_control_private.operation_context c
 WHERE c.transaction_id=pg_current_xact_id_if_assigned() AND c.tenant_id=t AND t=app.current_tenant_id()
 AND c.actor_id=identity.current_actor_id() AND c.capability IN ('TENANT_PLAN_VIEW','TAXPAYER_CREATE','TAXPAYER_ARCHIVE');
 IF cap IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 SELECT * INTO prior FROM taxpayer_private.operation_scope WHERE transaction_id=pg_current_xact_id();
 PERFORM taxpayer_private.set_write_scope(t,NULL,cap);
 UPDATE taxpayer_private.operation_scope SET purpose='MEASURE' WHERE transaction_id=pg_current_xact_id();
 SELECT count(*) INTO used FROM taxpayers.taxpayers WHERE tenant_id=t AND status='ACTIVE';
 IF prior.transaction_id IS NULL THEN DELETE FROM taxpayer_private.operation_scope WHERE transaction_id=pg_current_xact_id();
 ELSE UPDATE taxpayer_private.operation_scope SET taxpayer_id=prior.taxpayer_id,capability=prior.capability,purpose=prior.purpose
 WHERE transaction_id=pg_current_xact_id(); END IF;
 RETURN used;
END; $$;
CREATE FUNCTION entitlement_private.check_taxpayer_capacity(t uuid) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE config entitlement_private.limits%ROWTYPE; used bigint;
BEGIN
 SELECT * INTO config FROM entitlement_private.limits WHERE tenant_id=t AND resource='TAXPAYERS' FOR UPDATE;
 IF config.tenant_id IS NULL OR config.valid_from>clock_timestamp() OR config.valid_until<=clock_timestamp()
 THEN RAISE EXCEPTION USING ERRCODE='P3404',MESSAGE='TAXPAYER_CAPACITY_UNAVAILABLE'; END IF;
 used:=taxpayer_private.measured_taxpayers(t);
 IF used>=config.limit_value THEN RAISE EXCEPTION USING ERRCODE='P3404',MESSAGE='TAXPAYER_CAPACITY_UNAVAILABLE'; END IF;
END; $$;
CREATE FUNCTION taxpayer_private.canonical_regimes(refs jsonb) RETURNS jsonb
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE e jsonb; start_date date; end_date date; canonical jsonb;
BEGIN
 IF refs IS NULL OR jsonb_typeof(refs)<>'array' OR jsonb_array_length(refs)>10 THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 FOR e IN SELECT value FROM jsonb_array_elements(refs) LOOP
 IF jsonb_typeof(e)<>'object' OR NOT e ?& ARRAY['code','validFrom','validTo'] OR (SELECT count(*) FROM jsonb_object_keys(e))<>3
 OR jsonb_typeof(e->'code')<>'string' OR e->>'code' !~ '^[0-9]{3}$'
 OR jsonb_typeof(e->'validFrom') NOT IN ('string','null') OR jsonb_typeof(e->'validTo') NOT IN ('string','null') THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 IF (e->>'validFrom' IS NOT NULL AND e->>'validFrom' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
 OR (e->>'validTo' IS NOT NULL AND e->>'validTo' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 start_date:=(e->>'validFrom')::date;end_date:=(e->>'validTo')::date;
 IF (start_date IS NULL AND end_date IS NOT NULL) OR end_date<=start_date THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM jsonb_array_elements(refs) WITH ORDINALITY a(value,n)
 JOIN jsonb_array_elements(refs) WITH ORDINALITY b(value,n) ON a.n<b.n AND a.value->>'code'=b.value->>'code'
 WHERE a.value->>'validFrom' IS NULL OR b.value->>'validFrom' IS NULL
 OR daterange((a.value->>'validFrom')::date,(a.value->>'validTo')::date,'[)') && daterange((b.value->>'validFrom')::date,(b.value->>'validTo')::date,'[)'))
 THEN RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 SELECT coalesce(jsonb_agg(value ORDER BY value->>'code',value->>'validFrom' NULLS FIRST,value->>'validTo' NULLS LAST),'[]'::jsonb)
 INTO canonical FROM jsonb_array_elements(refs);
 RETURN canonical;
EXCEPTION WHEN invalid_datetime_format OR datetime_field_overflow THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID';
END; $$;
CREATE FUNCTION taxpayer_private.profile_regimes(t uuid,r uuid,v bigint) RETURNS jsonb
LANGUAGE sql STABLE SET search_path=pg_catalog,pg_temp AS $$
 SELECT coalesce(jsonb_agg(jsonb_build_object('code',regime_code,'validFrom',valid_from,'validTo',valid_to) ORDER BY ordinal),'[]'::jsonb)
 FROM taxpayers.taxpayer_regime_references WHERE tenant_id=t AND taxpayer_id=r AND taxpayer_version=v
$$;
CREATE FUNCTION taxpayer_private.protect_taxpayer_identity() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF ROW(NEW.tenant_id,NEW.id,NEW.rfc_normalized,NEW.person_type,NEW.created_at,NEW.created_by_membership_id)
 IS DISTINCT FROM ROW(OLD.tenant_id,OLD.id,OLD.rfc_normalized,OLD.person_type,OLD.created_at,OLD.created_by_membership_id)
 OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='TAXPAYER_IDENTITY_IMMUTABLE'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER immutable_identity BEFORE UPDATE ON taxpayers.taxpayers FOR EACH ROW EXECUTE FUNCTION taxpayer_private.protect_taxpayer_identity();
CREATE FUNCTION taxpayer_private.complete_revision() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM taxpayers.taxpayer_revisions r WHERE r.tenant_id=NEW.tenant_id AND r.taxpayer_id=NEW.id AND r.version=NEW.version
 AND ROW(r.rfc_normalized,r.person_type,r.legal_name,r.trade_name,r.status,r.created_at,r.updated_at,r.created_by_membership_id,r.updated_by_membership_id,r.archived_at,r.archived_by_membership_id)
 IS NOT DISTINCT FROM ROW(NEW.rfc_normalized,NEW.person_type,NEW.legal_name,NEW.trade_name,NEW.status,NEW.created_at,NEW.updated_at,NEW.created_by_membership_id,NEW.updated_by_membership_id,NEW.archived_at,NEW.archived_by_membership_id))
 OR NOT EXISTS(SELECT 1 FROM taxpayers.taxpayer_assignments a WHERE a.tenant_id=NEW.tenant_id AND a.taxpayer_id=NEW.id AND a.assignment_type='PRIMARY' AND a.ended_at IS NULL)
 THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='TAXPAYER_REVISION_INCOMPLETE'; END IF;
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER complete_revision AFTER INSERT OR UPDATE ON taxpayers.taxpayers DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION taxpayer_private.complete_revision();

CREATE FUNCTION taxpayers.command(t uuid,cmd uuid,target uuid,expected bigint,action text,rfc text,person text,name text,trade text,refs jsonb,reason text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
#variable_conflict use_variable
DECLARE cap text;member uuid;prior taxpayer_private.commands%ROWTYPE;fingerprint bytea;current taxpayers.taxpayers%ROWTYPE;
 result_version bigint;id uuid;stamp timestamptz:=clock_timestamp();event uuid;code text;normalized_refs jsonb;member_version bigint;role text;
BEGIN
 cap:=CASE action WHEN 'CREATE' THEN 'TAXPAYER_CREATE' WHEN 'UPDATE_PROFILE' THEN 'TAXPAYER_EDIT'
 WHEN 'ARCHIVE' THEN 'TAXPAYER_ARCHIVE' WHEN 'RESTORE' THEN 'TAXPAYER_ARCHIVE' ELSE NULL END;
 IF cap IS NULL THEN RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 member:=taxpayer_private.require_command(t,cap,target);
 IF cmd IS NULL OR (action='CREATE' AND (target IS NOT NULL OR expected IS NOT NULL))
 OR (action<>'CREATE' AND (target IS NULL OR expected IS NULL OR expected NOT BETWEEN 0 AND 9007199254740991))
 OR NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=cmd)
 THEN RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 IF action IN ('CREATE','UPDATE_PROFILE') THEN
 IF name IS NULL OR length(name) NOT BETWEEN 1 AND 300 OR name<>btrim(name) OR name ~ '[[:cntrl:]]'
 OR (trade IS NOT NULL AND (length(trade) NOT BETWEEN 1 AND 200 OR trade<>btrim(trade) OR trade ~ '[[:cntrl:]]'))
 OR (action='CREATE' AND NOT taxpayer_private.valid_rfc(rfc,person)) OR reason IS NOT NULL
 OR (action='UPDATE_PROFILE' AND (rfc IS NOT NULL OR person IS NOT NULL)) THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 normalized_refs:=taxpayer_private.canonical_regimes(refs);
 ELSE
 IF rfc IS NOT NULL OR person IS NOT NULL OR name IS NOT NULL OR trade IS NOT NULL OR refs IS NOT NULL OR reason IS NULL
 OR (action='ARCHIVE' AND reason NOT IN ('SERVICE_ENDED','DUPLICATE_REVIEW','OTHER'))
 OR (action='RESTORE' AND reason NOT IN ('SERVICE_RESUMED','CORRECTION')) THEN
 RAISE EXCEPTION USING ERRCODE='P3401',MESSAGE='TAXPAYER_INPUT_INVALID'; END IF;
 END IF;
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(t,member,action,target,expected,rfc,person,name,trade,normalized_refs,reason)::text,'UTF8'),'sha256');
 PERFORM pg_advisory_xact_lock(hashtextextended(t::text||cmd::text,3400));
 SELECT * INTO prior FROM taxpayer_private.commands WHERE tenant_id=t AND command_id=cmd;
 IF prior.command_id IS NOT NULL THEN
 IF prior.actor_membership_id<>member OR prior.operation<>action OR prior.fingerprint IS DISTINCT FROM fingerprint THEN
 RAISE EXCEPTION USING ERRCODE='P3406',MESSAGE='COMMAND_CONTENT_CONFLICT'; END IF;
 RETURN jsonb_build_object('targetId',prior.target_id,'version',prior.result_version,'replayed',true);
 END IF;
 IF EXISTS(SELECT 1 FROM journal.events WHERE tenant_id=t AND command_id=cmd) THEN
 RAISE EXCEPTION USING ERRCODE='P3406',MESSAGE='COMMAND_CONTENT_CONFLICT'; END IF;
 id:=CASE WHEN action='CREATE' THEN gen_random_uuid() ELSE target END;
 PERFORM taxpayer_private.set_write_scope(t,id,cap);
 IF action IN ('CREATE','RESTORE') THEN PERFORM entitlement_private.check_taxpayer_capacity(t); END IF;
 IF action='CREATE' THEN
 IF EXISTS(SELECT 1 FROM identity.membership_roles WHERE tenant_id=t AND membership_id=member AND role_code<>'FIRM_OWNER_ADMIN') THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 INSERT INTO taxpayers.taxpayers VALUES(t,id,rfc,person,name,trade,'ACTIVE',0,stamp,stamp,member,member,NULL,NULL);
 result_version:=0;code:='TAXPAYER_CREATED';
 ELSE
 SELECT * INTO current FROM taxpayers.taxpayers WHERE tenant_id=t AND taxpayers.id=id FOR UPDATE;
 IF current.id IS NULL THEN RAISE EXCEPTION USING ERRCODE='P3402',MESSAGE='TAXPAYER_NOT_FOUND'; END IF;
 IF current.version<>expected THEN RAISE EXCEPTION USING ERRCODE='P3403',MESSAGE='TAXPAYER_VERSION_CONFLICT'; END IF;
 IF current.version=9007199254740991 OR (action IN ('UPDATE_PROFILE','ARCHIVE') AND current.status<>'ACTIVE')
 OR (action='RESTORE' AND current.status<>'ARCHIVED') THEN RAISE EXCEPTION USING ERRCODE='P3405',MESSAGE='TAXPAYER_STATE_CONFLICT'; END IF;
 IF action='UPDATE_PROFILE' AND current.legal_name=name AND current.trade_name IS NOT DISTINCT FROM trade
 AND taxpayer_private.profile_regimes(t,id,current.version)=normalized_refs THEN
 RAISE EXCEPTION USING ERRCODE='P3405',MESSAGE='TAXPAYER_STATE_CONFLICT'; END IF;
 IF action<>'UPDATE_PROFILE' THEN normalized_refs:=taxpayer_private.profile_regimes(t,id,current.version); END IF;
 result_version:=current.version+1;
 UPDATE taxpayers.taxpayers SET legal_name=CASE WHEN action='UPDATE_PROFILE' THEN name ELSE legal_name END,
 trade_name=CASE WHEN action='UPDATE_PROFILE' THEN trade ELSE trade_name END,
 status=CASE WHEN action='ARCHIVE' THEN 'ARCHIVED' ELSE 'ACTIVE' END,version=result_version,updated_at=stamp,updated_by_membership_id=member,
 archived_at=CASE WHEN action='ARCHIVE' THEN stamp ELSE NULL END,archived_by_membership_id=CASE WHEN action='ARCHIVE' THEN member ELSE NULL END
 WHERE tenant_id=t AND taxpayers.id=id;
 code:=CASE action WHEN 'UPDATE_PROFILE' THEN 'TAXPAYER_PROFILE_UPDATED' WHEN 'ARCHIVE' THEN 'TAXPAYER_ARCHIVED' ELSE 'TAXPAYER_RESTORED' END;
 END IF;
 event:=journal_private.append_event(t,code,cap,'TAXPAYER',id);
 INSERT INTO taxpayers.taxpayer_revisions
 SELECT tenant_id,taxpayers.id,version,cmd,stamp,member,action,reason,rfc_normalized,person_type,legal_name,trade_name,status,
 created_at,updated_at,created_by_membership_id,updated_by_membership_id,archived_at,archived_by_membership_id
 FROM taxpayers.taxpayers WHERE tenant_id=t AND taxpayers.id=id;
 INSERT INTO taxpayers.taxpayer_regime_references
 SELECT t,id,result_version,(n-1)::smallint,value->>'code',(value->>'validFrom')::date,(value->>'validTo')::date,'MANUAL','PENDING_REVIEW',stamp,member
 FROM jsonb_array_elements(normalized_refs) WITH ORDINALITY e(value,n);
 IF action='CREATE' THEN
 INSERT INTO taxpayers.taxpayer_assignments VALUES(t,gen_random_uuid(),id,member,'PRIMARY',stamp,NULL,cmd,NULL);
 INSERT INTO access_control.resource_grants(tenant_id,membership_id,resource_kind,resource_id,capability,active,approval_reference)
 SELECT t,member,'TAXPAYER',id,c,true,cmd FROM unnest(ARRAY['TAXPAYER_VIEW','TAXPAYER_EDIT','TAXPAYER_ARCHIVE','TAXPAYER_ASSIGN']) c;
 SELECT version INTO member_version FROM identity.memberships WHERE tenant_id=t AND identity.memberships.id=member;
 role:='FIRM_OWNER_ADMIN';
 INSERT INTO taxpayer_private.grant_reviews
 SELECT t,member,'TAXPAYER',c,id,1,'ENABLE',member_version,role,member,stamp,cmd
 FROM unnest(ARRAY['TAXPAYER_VIEW','TAXPAYER_EDIT','TAXPAYER_ARCHIVE']) c;
 END IF;
 INSERT INTO taxpayer_private.commands VALUES(t,cmd,member,action,id,fingerprint,result_version,event,stamp);
 RETURN jsonb_build_object('targetId',id,'version',result_version,'replayed',false);
END; $$;
CREATE OR REPLACE FUNCTION entitlement_api.usage(p_tenant uuid) RETURNS TABLE(resource text,limit_value bigint,version bigint,valid_from timestamptz,valid_until timestamptz,
 measured bigint,reserved bigint,available bigint,over_limit boolean,integration text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'TENANT_PLAN_VIEW');
 RETURN QUERY SELECT kinds.r,l.limit_value,l.version,l.valid_from,l.valid_until,
 CASE WHEN kinds.r='USERS' THEN u.used WHEN kinds.r='TAXPAYERS' THEN p.used ELSE NULL END,
 CASE WHEN kinds.r='USERS' THEN u.held WHEN kinds.r='TAXPAYERS' THEN 0::bigint ELSE NULL END,
 CASE WHEN kinds.r IN ('USERS','TAXPAYERS') AND l.valid_from<=clock_timestamp() AND (l.valid_until IS NULL OR l.valid_until>clock_timestamp())
 THEN greatest(0,l.limit_value-CASE WHEN kinds.r='USERS' THEN u.used+u.held ELSE p.used END) ELSE NULL END,
 CASE WHEN kinds.r IN ('USERS','TAXPAYERS') AND l.limit_value IS NOT NULL THEN CASE WHEN kinds.r='USERS' THEN u.used+u.held ELSE p.used END>l.limit_value ELSE NULL END,
 CASE WHEN kinds.r IN ('USERS','TAXPAYERS') THEN 'MEASURED' ELSE 'NOT_INTEGRATED' END
 FROM unnest(ARRAY['USERS','TAXPAYERS','STORAGE_BYTES','JOBS_RUNNING','JOBS_PENDING','JOBS_PER_PERIOD']) WITH ORDINALITY kinds(r,position)
 LEFT JOIN entitlement_private.limits l ON l.tenant_id=p_tenant AND l.resource=kinds.r
 CROSS JOIN LATERAL(SELECT entitlement_private.measured_users(p_tenant) used,
 (SELECT count(*) FROM entitlement_private.reservations r WHERE r.tenant_id=p_tenant AND r.status='RESERVED') held) u CROSS JOIN LATERAL(SELECT taxpayer_private.measured_taxpayers(p_tenant) used) p ORDER BY kinds.position;
END;
$$;

REVOKE ALL ON ALL TABLES IN SCHEMA taxpayers FROM PUBLIC,fiscuvia_sessions;
REVOKE ALL ON ALL TABLES IN SCHEMA taxpayer_private FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA taxpayer_private,taxpayers FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
REVOKE ALL ON FUNCTION entitlement_private.check_taxpayer_capacity(uuid) FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION taxpayer_private.row_allowed(uuid,uuid) TO fiscuvia_runtime;
GRANT EXECUTE ON FUNCTION taxpayers.command(uuid,uuid,uuid,bigint,text,text,text,text,text,jsonb,text) TO fiscuvia_runtime;
CREATE FUNCTION taxpayers.approved_policy() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT (SELECT count(*)=8 AND bool_and(approval_reference='Owner approval D74-03 / PR #92 / 2026-11-12')
 FROM access_control_private.role_capabilities WHERE capability IN ('TAXPAYER_VIEW','TAXPAYER_EDIT','TAXPAYER_CREATE','TAXPAYER_ARCHIVE')
 AND ((capability IN ('TAXPAYER_VIEW','TAXPAYER_EDIT') AND resource_kind='TAXPAYER' AND NOT requires_mfa
 AND role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT'))
 OR (role_code='FIRM_OWNER_ADMIN' AND requires_mfa AND ((capability='TAXPAYER_CREATE' AND resource_kind='TENANT') OR (capability='TAXPAYER_ARCHIVE' AND resource_kind='TAXPAYER')))))
 AND NOT EXISTS(SELECT 1 FROM access_control_private.role_capabilities WHERE capability='TAXPAYER_ASSIGN')
 AND (SELECT count(*)=8 FROM access_control_private.role_capabilities WHERE capability LIKE 'TAXPAYER_%')
 AND (SELECT count(*)=7 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND pg_get_userbyid(c.relowner)='fiscuvia_migrator')
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN ('taxpayers','taxpayer_private') AND c.relkind='r')
 AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='taxpayer_private' AND c.relkind='r'
 AND (has_table_privilege('fiscuvia_runtime',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
 OR has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')))
 AND (SELECT count(*)=4 FROM pg_policy WHERE polname='resource_fence' AND NOT polpermissive AND polrelid IN
 ('taxpayers.taxpayers'::regclass,'taxpayers.taxpayer_revisions'::regclass,'taxpayers.taxpayer_regime_references'::regclass,'taxpayers.taxpayer_assignments'::regclass))
 AND (SELECT count(*)=4 FROM pg_policy WHERE polname='tenant_fence' AND NOT polpermissive
 AND pg_get_expr(polqual,polrelid) LIKE '%current_tenant_id%'
 AND pg_get_expr(polwithcheck,polrelid) LIKE '%current_tenant_id%' AND polrelid IN
 ('taxpayers.taxpayers'::regclass,'taxpayers.taxpayer_revisions'::regclass,'taxpayers.taxpayer_regime_references'::regclass,'taxpayers.taxpayer_assignments'::regclass))
 AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='taxpayers' AND c.relkind='r'
 AND (has_table_privilege('fiscuvia_runtime',c.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
 OR has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')))
 AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='taxpayer_private'
 AND (has_function_privilege('fiscuvia_sessions',p.oid,'EXECUTE') OR (p.proname<>'row_allowed' AND has_function_privilege('fiscuvia_runtime',p.oid,'EXECUTE'))))
 AND EXISTS(SELECT 1 FROM pg_constraint WHERE conname='grant_taxpayer_fk' AND conrelid='access_control.resource_grants'::regclass AND convalidated)
 AND (SELECT count(*)=11 FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O' AND tgname IN ('append_only','no_truncate') AND tgrelid IN
 ('taxpayers.taxpayer_revisions'::regclass,'taxpayers.taxpayer_regime_references'::regclass,'taxpayers.taxpayer_assignments'::regclass,'taxpayer_private.grant_reviews'::regclass,'taxpayer_private.commands'::regclass,'taxpayers.taxpayers'::regclass))
 AND (SELECT count(*)=4 FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O' AND
 ((tgrelid='taxpayers.taxpayers'::regclass AND tgname IN ('immutable_identity','complete_revision','no_delete'))
 OR (tgrelid='taxpayers.taxpayer_regime_references'::regclass AND tgname='regime_overlap')))
$$;
REVOKE ALL ON FUNCTION taxpayers.approved_policy() FROM PUBLIC,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION taxpayers.approved_policy() TO fiscuvia_runtime;
