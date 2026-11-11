-- #33 policy A explicitly approved by the owner, 2026-11-11, PR #91.
-- No fiscal permissions, owner transfer, self-edit or production email approval. V1--V10 immutable.
ALTER TABLE access_control_private.role_capabilities DROP CONSTRAINT role_capabilities_capability_check;
ALTER TABLE access_control_private.role_capabilities ADD CONSTRAINT role_capabilities_capability_check CHECK(capability IN
 ('TENANT_READ','TENANT_SETTINGS_MANAGE','TENANT_PLAN_VIEW','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE',
 'MEMBERSHIP_DISABLE','MEMBERSHIP_REACTIVATE','TAXPAYER_VIEW','TAXPAYER_CREATE','TAXPAYER_EDIT','TAXPAYER_ASSIGN','TAXPAYER_EXPORT',
 'DOCUMENT_VIEW','DOCUMENT_DOWNLOAD','REPORT_VIEW','REPORT_EXPORT','AUDIT_VIEW'));
INSERT INTO access_control_private.role_capabilities
 SELECT 'FIRM_OWNER_ADMIN',c,'TENANT',true,'Owner approval #33 / PR #91 policy A / 2026-11-11'
 FROM unnest(ARRAY['MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE']) c;
ALTER TABLE journal_private.denials DROP CONSTRAINT denials_operation_check;
ALTER TABLE journal_private.denials ADD CONSTRAINT denials_operation_check CHECK(operation IN
 ('TENANT_SETTINGS_MANAGE','MEMBERSHIP_VIEW','MEMBERSHIP_INVITE','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_DISABLE','MEMBERSHIP_REACTIVATE','AUDIT_VIEW'));
ALTER TABLE journal.events DROP CONSTRAINT events_event_code_check;
ALTER TABLE journal.events ADD CONSTRAINT events_event_code_check CHECK(event_code IN
 ('OWNER_BOOTSTRAPPED','INVITATION_ISSUED','INVITATION_REVOKED','INVITATION_ACCEPTED','MEMBERSHIP_SUSPENDED','MEMBERSHIP_REVOKED',
 'AUTHORIZED_OPERATION_RECORDED','TENANT_CREATED','TENANT_PROFILE_UPDATED','TENANT_SUSPENDED','TENANT_REACTIVATED',
 'TENANT_QUOTA_CHANGED','QUOTA_RESERVED','QUOTA_RELEASED','MEMBERSHIP_REACTIVATED','MEMBERSHIP_ROLE_CHANGED'));
ALTER TABLE journal.events ADD COLUMN previous_role_code text CHECK(previous_role_code IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT'));
ALTER TABLE journal.events ADD CONSTRAINT role_change_metadata CHECK
 ((event_code='MEMBERSHIP_ROLE_CHANGED' AND previous_role_code IS NOT NULL AND role_code IS NOT NULL AND role_code IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT') AND role_code<>previous_role_code)
 OR (event_code<>'MEMBERSHIP_ROLE_CHANGED' AND previous_role_code IS NULL));
ALTER TABLE identity.memberships ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 9007199254740991);
ALTER TABLE identity.invitations ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 9007199254740991);
CREATE FUNCTION identity_private.advance_team_version() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 IF NEW.tenant_id<>OLD.tenant_id OR NEW.id<>OLD.id THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='TEAM_ID_IMMUTABLE'; END IF;
 IF TG_TABLE_NAME='memberships' THEN
 IF OLD.status='REVOKED' AND NEW.status<>'REVOKED' THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 END IF;
 NEW.version:=OLD.version+1; RETURN NEW;
END;
$$;
CREATE TRIGGER team_version BEFORE UPDATE ON identity.memberships FOR EACH ROW EXECUTE FUNCTION identity_private.advance_team_version();
CREATE TRIGGER team_version BEFORE UPDATE ON identity.invitations FOR EACH ROW EXECUTE FUNCTION identity_private.advance_team_version();
CREATE INDEX memberships_directory ON identity.memberships(tenant_id,created_at,id);
CREATE INDEX invitations_directory ON identity.invitations(tenant_id,created_at,id);
CREATE TABLE identity_private.team_commands (
 tenant_id uuid NOT NULL, command_id uuid NOT NULL, actor_id uuid NOT NULL REFERENCES identity_private.users(id),
 operation text NOT NULL CHECK(operation IN ('SUSPEND','REVOKE','REACTIVATE','SET_ROLE','REVOKE_INVITATION')),
 target_id uuid NOT NULL, fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32), result_version bigint NOT NULL CHECK(result_version BETWEEN 0 AND 9007199254740991),
 PRIMARY KEY(tenant_id,command_id), FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id)
);
ALTER TABLE identity_private.team_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_private.team_commands FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON identity_private.team_commands TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
REVOKE ALL ON identity_private.team_commands FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;

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


-- Directory authority exposes only members/recipients of the authorized firm, never a global contact search.
CREATE FUNCTION identity.team_members(p_tenant uuid,p_page integer,p_size integer,p_search text,p_status text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_VIEW');
 PERFORM identity.authorize_tenant(p_tenant,true);
 IF p_page IS NULL OR p_page NOT BETWEEN 0 AND 200 OR p_size IS NULL OR p_size NOT BETWEEN 1 AND 50
 OR p_search IS NULL OR length(p_search)>80 OR p_search ~ '[[:cntrl:]]'
 OR p_status IS NULL OR p_status NOT IN ('ALL','ACTIVE','SUSPENDED','REVOKED') THEN
 RAISE EXCEPTION USING ERRCODE='P3310',MESSAGE='TEAM_QUERY_REJECTED'; END IF;
 RETURN (WITH allowed AS MATERIALIZED (
 SELECT m.id,m.created_at,jsonb_build_object('membershipId',m.id,'displayName',c.display_name,'email',c.email,
 'roles',r.roles,'status',m.status,'identityStatus',u.status,'version',m.version,'joinedAt',m.created_at,'changedAt',m.changed_at,
 'canManage',m.user_id<>identity.current_actor_id() AND cardinality(r.roles)=1 AND NOT('FIRM_OWNER_ADMIN'=ANY(r.roles))) AS item
 FROM identity.memberships m JOIN identity_private.users u ON u.id=m.user_id
 CROSS JOIN LATERAL (SELECT coalesce(array_agg(role_code ORDER BY role_code),'{}'::varchar[]) AS roles
 FROM identity.membership_roles WHERE tenant_id=m.tenant_id AND membership_id=m.id) r
 LEFT JOIN LATERAL (SELECT CASE WHEN count(*)=1 THEN min(v.display_name) END AS display_name,
 CASE WHEN count(*)=1 THEN min(v.email) END AS email FROM identity_private.external_identities e
 JOIN identity_private.verified_contacts v USING(issuer,subject) WHERE e.user_id=m.user_id) c ON true
 WHERE m.tenant_id=p_tenant AND (p_status='ALL' OR m.status=p_status)
 AND (p_search='' OR position(lower(p_search) IN lower(coalesce(c.display_name,'')||' '||coalesce(c.email,'')))>0))
 SELECT jsonb_build_object('items',coalesce((SELECT jsonb_agg(item ORDER BY created_at,id) FROM
 (SELECT * FROM allowed ORDER BY created_at,id LIMIT p_size OFFSET p_page*p_size) page_rows),'[]'::jsonb),
 'page',p_page,'size',p_size,'total',(SELECT count(*) FROM allowed)));
END;
$$;
CREATE FUNCTION identity.team_invitations(p_tenant uuid,p_page integer,p_size integer,p_search text,p_status text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_VIEW');
 PERFORM identity.authorize_tenant(p_tenant,true);
 IF p_page IS NULL OR p_page NOT BETWEEN 0 AND 200 OR p_size IS NULL OR p_size NOT BETWEEN 1 AND 50
 OR p_search IS NULL OR length(p_search)>80 OR p_search ~ '[[:cntrl:]]'
 OR p_status IS NULL OR p_status NOT IN ('ALL','PENDING','ACCEPTED','REVOKED','EXPIRED') THEN
 RAISE EXCEPTION USING ERRCODE='P3310',MESSAGE='TEAM_QUERY_REJECTED'; END IF;
 RETURN (WITH scoped AS MATERIALIZED (
 SELECT i.id,i.created_at,i.version,i.expires_at,i.role_code,c.display_name,c.email,
 CASE WHEN i.consumed_at IS NOT NULL THEN 'ACCEPTED' WHEN i.revoked_at IS NOT NULL THEN 'REVOKED'
 WHEN i.expires_at<=statement_timestamp() THEN 'EXPIRED' ELSE 'PENDING' END AS state
 FROM identity.invitations i LEFT JOIN identity_private.verified_contacts c USING(issuer,subject) WHERE i.tenant_id=p_tenant),
 allowed AS MATERIALIZED(SELECT id,created_at,jsonb_build_object('invitationId',id,'displayName',display_name,'email',email,
 'role',role_code,'status',state,'version',version,'createdAt',created_at,'expiresAt',expires_at) AS item FROM scoped
 WHERE (p_status='ALL' OR state=p_status) AND (p_search='' OR position(lower(p_search) IN lower(coalesce(display_name,'')||' '||coalesce(email,'')))>0))
 SELECT jsonb_build_object('items',coalesce((SELECT jsonb_agg(item ORDER BY created_at,id) FROM
 (SELECT * FROM allowed ORDER BY created_at,id LIMIT p_size OFFSET p_page*p_size) page_rows),'[]'::jsonb),
 'page',p_page,'size',p_size,'total',(SELECT count(*) FROM allowed)));
END;
$$;
-- Exact old/new role enums only. No arbitrary metadata; journal and outbox are inserted atomically.
CREATE FUNCTION journal_private.append_team_role(p_tenant uuid,p_member uuid,p_before text,p_after text) RETURNS void
LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
DECLARE ctx journal_private.command_context%ROWTYPE; actor uuid:=identity.current_actor_id(); event uuid;
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_ASSIGN_ROLE');
 SELECT * INTO ctx FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id();
 IF actor IS NULL OR ctx.command_id IS NULL OR p_tenant IS DISTINCT FROM app.current_tenant_id() THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 INSERT INTO journal.events(tenant_id,actor_kind,actor_id,event_code,operation,correlation_id,command_id,resource_kind,resource_id,role_code,previous_role_code)
 VALUES(p_tenant,'USER',actor,'MEMBERSHIP_ROLE_CHANGED','MEMBERSHIP_ASSIGN_ROLE',ctx.correlation_id,ctx.command_id,'MEMBERSHIP',p_member,p_after,p_before) RETURNING event_id INTO event;
 INSERT INTO journal.outbox(tenant_id,event_id,occurred_at,correlation_id,causation_id,deduplication_key,payload)
 SELECT tenant_id,event_id,occurred_at,correlation_id,event_id,event_id,jsonb_build_object('auditEventId',event_id::text)
 FROM journal.events WHERE tenant_id=p_tenant AND event_id=event;
END;
$$;
CREATE FUNCTION identity.team_command(p_tenant uuid,p_command uuid,p_target uuid,p_expected bigint,p_action text,p_value text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE cap text; actor uuid:=identity.current_actor_id(); prior identity_private.team_commands%ROWTYPE; fingerprint bytea;
 member identity.memberships%ROWTYPE; invitation identity.invitations%ROWTYPE; roles text[]; result_version bigint;
BEGIN
 cap:=CASE p_action WHEN 'SET_ROLE' THEN 'MEMBERSHIP_ASSIGN_ROLE' WHEN 'REACTIVATE' THEN 'MEMBERSHIP_REACTIVATE'
 WHEN 'REVOKE_INVITATION' THEN 'MEMBERSHIP_INVITE' WHEN 'SUSPEND' THEN 'MEMBERSHIP_DISABLE' WHEN 'REVOKE' THEN 'MEMBERSHIP_DISABLE' END;
 IF cap IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 PERFORM tenant_private.require_operation(p_tenant,cap);
 PERFORM identity.authorize_tenant(p_tenant,true);
 IF p_command IS NULL OR p_target IS NULL OR p_expected IS NULL OR p_expected NOT BETWEEN 0 AND 9007199254740991
 OR p_value IS NULL OR (p_action='SET_ROLE' AND p_value NOT IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT'))
 OR (p_action<>'SET_ROLE' AND p_value<>'') THEN RAISE EXCEPTION USING ERRCODE='P3310',MESSAGE='TEAM_COMMAND_REJECTED'; END IF;
 IF NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=p_command) THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 -- Root UPDATE is already held for membership writes. Invitation revocation additionally uses a command lock.
 PERFORM pg_advisory_xact_lock(hashtextextended(jsonb_build_array(p_tenant,p_command)::text,3311));
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_action,p_target,p_expected,p_value)::text,'UTF8'),'sha256');
 SELECT * INTO prior FROM identity_private.team_commands WHERE tenant_id=p_tenant AND command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.actor_id<>actor OR prior.fingerprint<>fingerprint THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN jsonb_build_object('targetId',p_target,'version',prior.result_version,'replayed',true);
 END IF;
 IF p_action='REVOKE_INVITATION' THEN
 SELECT * INTO invitation FROM identity.invitations WHERE tenant_id=p_tenant AND id=p_target FOR UPDATE;
 IF invitation.id IS NULL THEN RAISE EXCEPTION USING ERRCODE='P3311',MESSAGE='TEAM_TARGET_UNAVAILABLE'; END IF;
 IF invitation.version<>p_expected THEN RAISE EXCEPTION USING ERRCODE='P3312',MESSAGE='TEAM_VERSION_CONFLICT'; END IF;
 IF invitation.consumed_at IS NOT NULL OR invitation.revoked_at IS NOT NULL OR invitation.expires_at<=clock_timestamp() THEN
 RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 PERFORM identity.revoke_invitation(p_tenant,p_target);
 SELECT version INTO result_version FROM identity.invitations WHERE tenant_id=p_tenant AND id=p_target;
 ELSE
 SELECT * INTO member FROM identity.memberships WHERE tenant_id=p_tenant AND id=p_target FOR UPDATE;
 IF member.id IS NULL THEN RAISE EXCEPTION USING ERRCODE='P3311',MESSAGE='TEAM_TARGET_UNAVAILABLE'; END IF;
 SELECT array_agg(role_code::text ORDER BY role_code) INTO roles FROM identity.membership_roles WHERE tenant_id=p_tenant AND membership_id=p_target;
 IF member.user_id=actor OR 'FIRM_OWNER_ADMIN'=ANY(roles) THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 IF member.version<>p_expected THEN RAISE EXCEPTION USING ERRCODE='P3312',MESSAGE='TEAM_VERSION_CONFLICT'; END IF;
 IF member.status='REVOKED' OR coalesce(cardinality(roles),0)<>1 THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 IF p_action='SET_ROLE' THEN
 IF roles[1]=p_value THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 UPDATE identity.memberships SET changed_at=clock_timestamp() WHERE tenant_id=p_tenant AND id=p_target;
 UPDATE identity.membership_roles SET role_code=p_value WHERE tenant_id=p_tenant AND membership_id=p_target AND role_code=roles[1];
 PERFORM journal_private.append_team_role(p_tenant,p_target,roles[1],p_value);
 ELSIF p_action='REACTIVATE' THEN
 IF member.status<>'SUSPENDED' THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 PERFORM 1 FROM identity_private.users WHERE id=member.user_id AND status='ACTIVE' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 -- Original V9 trigger is the only admission gate, including reservations and concurrent activations.
 UPDATE identity.memberships SET status='ACTIVE',changed_at=clock_timestamp() WHERE tenant_id=p_tenant AND id=p_target;
 ELSE
 IF p_action='SUSPEND' AND member.status<>'ACTIVE' THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='TEAM_STATE_CONFLICT'; END IF;
 PERFORM identity.change_membership(p_tenant,p_target,CASE p_action WHEN 'SUSPEND' THEN 'SUSPENDED' ELSE 'REVOKED' END);
 END IF;
 SELECT version INTO result_version FROM identity.memberships WHERE tenant_id=p_tenant AND id=p_target;
 END IF;
 INSERT INTO identity_private.team_commands VALUES(p_tenant,p_command,actor,p_action,p_target,fingerprint,result_version);
 RETURN jsonb_build_object('targetId',p_target,'version',result_version,'replayed',false);
END;
$$;
CREATE FUNCTION identity.approved_team_policy() RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
 SELECT (SELECT count(*) FROM access_control_private.role_capabilities WHERE capability IN ('MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE'))=3
 AND (SELECT count(*) FROM access_control_private.role_capabilities WHERE capability IN ('MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE')
 AND role_code='FIRM_OWNER_ADMIN' AND resource_kind='TENANT' AND requires_mfa AND approval_reference='Owner approval #33 / PR #91 policy A / 2026-11-11')=3
$$;
REVOKE ALL ON FUNCTION identity_private.advance_team_version(),journal_private.append_team_role(uuid,uuid,text,text),
 identity.team_members(uuid,integer,integer,text,text),identity.team_invitations(uuid,integer,integer,text,text),
 identity.team_command(uuid,uuid,uuid,bigint,text,text),identity.approved_team_policy() FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION identity.team_members(uuid,integer,integer,text,text),identity.team_invitations(uuid,integer,integer,text,text),
 identity.team_command(uuid,uuid,uuid,bigint,text,text),identity.approved_team_policy() TO fiscuvia_runtime;
