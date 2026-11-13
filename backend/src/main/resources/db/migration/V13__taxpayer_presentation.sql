-- #35: closed presentation hints for the existing D74-03 operations. No grants.
CREATE FUNCTION taxpayer_private.action_available(t uuid,m uuid,r uuid,cap text) RETURNS boolean
LANGUAGE sql STABLE SET search_path=pg_catalog,pg_temp AS $$
 SELECT cap IN ('TAXPAYER_EDIT','TAXPAYER_ARCHIVE') AND EXISTS(
 SELECT 1 FROM identity.membership_roles role
 JOIN access_control_private.role_capabilities policy ON policy.role_code=role.role_code
 JOIN access_control.resource_grants g ON g.tenant_id=t AND g.membership_id=m AND g.resource_id=r
 AND g.resource_kind='TAXPAYER' AND g.capability=cap AND g.active
 JOIN identity_private.actor_context actor ON actor.transaction_id=pg_current_xact_id_if_assigned()
 WHERE role.tenant_id=t AND role.membership_id=m AND policy.capability=cap AND policy.resource_kind='TAXPAYER'
 AND role.role_code IN ('FIRM_OWNER_ADMIN','FIRM_SUPERVISOR','ACCOUNTANT')
 AND (cap<>'TAXPAYER_ARCHIVE' OR role.role_code='FIRM_OWNER_ADMIN')
 AND (NOT (policy.requires_mfa OR cap='TAXPAYER_ARCHIVE') OR actor.mfa_at BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30)
 AND taxpayer_private.grant_enabled(t,m,cap,r))
$$;
CREATE FUNCTION taxpayers.presentation(t uuid,r uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE member uuid; needs_mfa boolean; recent boolean; create_ok boolean:=false; block text:='NOT_AUTHORIZED';
 edit_ok boolean:=false; archive_ok boolean:=false; restore_ok boolean:=false; current_version bigint; current_status text; capacity bigint; used bigint;
BEGIN
 SELECT c.membership_id INTO member FROM access_control_private.operation_context c
 WHERE c.transaction_id=pg_current_xact_id_if_assigned() AND c.actor_id=identity.current_actor_id()
 AND c.tenant_id=t AND t=app.current_tenant_id() AND c.capability='TAXPAYER_VIEW' AND c.resource_kind='TAXPAYER'
 AND c.resource_id IS NOT DISTINCT FROM r;
 IF member IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 IF r IS NOT NULL THEN
 IF NOT access_control.resource_allowed(t,'TAXPAYER',r,'TAXPAYER_VIEW') THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 SELECT version,status INTO current_version,current_status FROM taxpayers.taxpayers WHERE tenant_id=t AND id=r;
 IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='ACCESS_DENIED'; END IF;
 edit_ok:=current_status='ACTIVE' AND taxpayer_private.action_available(t,member,r,'TAXPAYER_EDIT');
 archive_ok:=current_status='ACTIVE' AND taxpayer_private.action_available(t,member,r,'TAXPAYER_ARCHIVE');
 restore_ok:=current_status='ARCHIVED' AND taxpayer_private.action_available(t,member,r,'TAXPAYER_ARCHIVE');
 ELSE
 SELECT p.requires_mfa INTO needs_mfa FROM identity.membership_roles role
 JOIN access_control_private.role_capabilities p ON p.role_code=role.role_code
 WHERE role.tenant_id=t AND role.membership_id=member AND role.role_code='FIRM_OWNER_ADMIN'
 AND p.capability='TAXPAYER_CREATE' AND p.resource_kind='TENANT' AND p.requires_mfa
 AND (SELECT count(*) FROM identity.membership_roles x WHERE x.tenant_id=t AND x.membership_id=member)=1;
 IF FOUND THEN
 SELECT mfa_at BETWEEN extract(epoch FROM clock_timestamp())::bigint-600 AND extract(epoch FROM clock_timestamp())::bigint+30
 INTO recent FROM identity_private.actor_context WHERE transaction_id=pg_current_xact_id_if_assigned();
 block:='MFA_REQUIRED';
 IF NOT needs_mfa OR coalesce(recent,false) THEN
 block:='QUOTA_UNAVAILABLE';
 SELECT limit_value INTO capacity FROM entitlement_private.limits WHERE tenant_id=t AND resource='TAXPAYERS'
 AND valid_from<=clock_timestamp() AND (valid_until IS NULL OR valid_until>clock_timestamp());
 IF capacity IS NOT NULL THEN
 -- Only the actor independently eligible for CREATE can receive this capacity hint.
 -- The original VIEW context stays immutable. No count or private identifiers leave SQL.
 IF EXISTS(SELECT 1 FROM taxpayer_private.operation_scope WHERE transaction_id=pg_current_xact_id_if_assigned())
 THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='CONTEXT_REJECTED'; END IF;
 PERFORM taxpayer_private.set_write_scope(t,NULL,'TAXPAYER_CREATE');
 UPDATE taxpayer_private.operation_scope SET purpose='MEASURE' WHERE transaction_id=pg_current_xact_id();
 SELECT count(*) INTO used FROM taxpayers.taxpayers WHERE tenant_id=t AND status='ACTIVE';
 DELETE FROM taxpayer_private.operation_scope WHERE transaction_id=pg_current_xact_id();
 create_ok:=used<capacity; IF create_ok THEN block:='NONE'; END IF;
 END IF;
 END IF;
 END IF;
 END IF;
 RETURN jsonb_build_object('canCreate',create_ok,'createBlock',block,'canEdit',edit_ok,'canArchive',archive_ok,'canRestore',restore_ok,'version',current_version);
END; $$;
REVOKE ALL ON FUNCTION taxpayer_private.action_available(uuid,uuid,uuid,text),taxpayers.presentation(uuid,uuid) FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION taxpayers.presentation(uuid,uuid) TO fiscuvia_runtime;
