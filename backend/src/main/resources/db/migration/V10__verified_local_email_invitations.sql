-- #33: owner-authorized invitations only. No MEMBERSHIP_VIEW/ASSIGN_ROLE/reactivation grant.
-- Owner approval in the #33 conversation on 2026-11-11 is limited to Keycloak local/test.
-- V1--V9 immutable. Contact metadata is global identity data, never an RLS tenant bypass.
CREATE TABLE identity_private.verified_contacts (
 issuer text NOT NULL, subject varchar(255) NOT NULL,
 email varchar(254) NOT NULL CHECK(length(email)>3 AND email !~ '[[:cntrl:]]'),
 display_name varchar(160) CHECK(display_name IS NULL OR display_name !~ '[[:cntrl:]]'),
 verified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(issuer,subject), FOREIGN KEY(issuer,subject) REFERENCES identity_private.external_identities(issuer,subject)
);
CREATE INDEX verified_contact_address ON identity_private.verified_contacts(issuer,email);
ALTER TABLE identity_private.verified_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_private.verified_contacts FORCE ROW LEVEL SECURITY;
CREATE POLICY identity_owner ON identity_private.verified_contacts TO fiscuvia_migrator USING(true) WITH CHECK(true);

CREATE TABLE identity_private.email_invitation_commands (
 tenant_id uuid NOT NULL, command_id uuid NOT NULL, actor_id uuid NOT NULL REFERENCES identity_private.users(id),
 fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32), invitation_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,command_id),
 FOREIGN KEY(tenant_id,invitation_id) REFERENCES identity.invitations(tenant_id,id),
 FOREIGN KEY(tenant_id,command_id) REFERENCES journal.events(tenant_id,command_id)
);
ALTER TABLE identity_private.email_invitation_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_private.email_invitation_commands FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fence ON identity_private.email_invitation_commands TO fiscuvia_migrator
 USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id());
REVOKE ALL ON identity_private.verified_contacts,identity_private.email_invitation_commands FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;

-- Only the BFF's cryptographically verified OIDC callback can attest contact claims.
-- Domain-separated HMAC is bound to issuer/subject/xid/email/name; actor proof cannot substitute.
CREATE FUNCTION identity.record_verified_local_contact(p_issuer text,p_subject text,p_email text,p_name text,p_proof text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE key bytea; expected bytea; actor uuid:=identity.current_actor_id(); tx xid8:=pg_current_xact_id();
BEGIN
 IF actor IS NULL OR p_issuer IS NULL OR p_subject IS NULL OR p_issuer ~ '[[:cntrl:]]' OR p_subject ~ '[[:cntrl:]]'
 OR NOT EXISTS(SELECT 1 FROM identity_private.external_identities WHERE issuer=p_issuer AND subject=p_subject AND user_id=actor)
 OR (p_email IS NOT NULL AND (length(p_email) NOT BETWEEN 4 AND 254 OR p_email ~ '[[:cntrl:]]'))
 OR (p_name IS NOT NULL AND (length(p_name)>160 OR p_name ~ '[[:cntrl:]]'))
 OR p_proof IS NULL OR p_proof !~ '^[0-9a-f]{64}$' THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='CONTACT_PROOF_REJECTED'; END IF;
 SELECT secret INTO key FROM identity_private.proof_key WHERE singleton;
 expected:=identity_crypto.hmac(convert_to('FISCUVIA_LOCAL_CONTACT_V1'||E'\n'||p_issuer||E'\n'||p_subject||E'\n'||tx::text||E'\n'||coalesce(p_email,'')||E'\n'||coalesce(p_name,''),'UTF8'),key,'sha256');
 IF expected IS NULL OR identity_crypto.digest(expected,'sha256')<>identity_crypto.digest(decode(p_proof,'hex'),'sha256') THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='CONTACT_PROOF_REJECTED'; END IF;
 -- Serialize verified contact changes and exact-address resolution for this local issuer.
 -- No lock is acquired for an invalid attestation.
 PERFORM pg_advisory_xact_lock(hashtextextended(p_issuer,3303));
 IF p_email IS NULL THEN DELETE FROM identity_private.verified_contacts WHERE issuer=p_issuer AND subject=p_subject; RETURN; END IF;
 INSERT INTO identity_private.verified_contacts(issuer,subject,email,display_name) VALUES(p_issuer,p_subject,p_email,p_name)
 ON CONFLICT(issuer,subject) DO UPDATE SET email=EXCLUDED.email,display_name=EXCLUDED.display_name,verified_at=clock_timestamp();
END;
$$;

-- Protect new invitations through the original SQL path as well as the email adapter.
-- Expired rows need no destructive cleanup. Existing history is never rewritten.
CREATE FUNCTION identity_private.prevent_duplicate_invitation() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(jsonb_build_array(NEW.tenant_id,NEW.issuer,NEW.subject)::text,3301));
 IF EXISTS(SELECT 1 FROM identity.invitations i WHERE i.tenant_id=NEW.tenant_id AND i.issuer=NEW.issuer AND i.subject=NEW.subject
 AND i.consumed_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>clock_timestamp()) THEN
 RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='INVITATION_CONFLICT'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER invitation_recipient_unique BEFORE INSERT ON identity.invitations
 FOR EACH ROW EXECUTE FUNCTION identity_private.prevent_duplicate_invitation();

CREATE FUNCTION identity.invite_verified_local_email(p_tenant uuid,p_command uuid,p_email text,p_role text,p_hash bytea,p_expires timestamptz)
RETURNS TABLE(invitation_id uuid,expires_at timestamptz,created boolean)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE actor uuid:=identity.current_actor_id(); issuer_key text; recipient_subject text; recipient uuid;
 prior identity_private.email_invitation_commands%ROWTYPE; fingerprint bytea; invitation uuid; matches integer;
BEGIN
 PERFORM tenant_private.require_operation(p_tenant,'MEMBERSHIP_INVITE');
 PERFORM identity.authorize_tenant(p_tenant,true);
 IF p_command IS NULL OR p_email IS NULL OR length(p_email)>254 OR p_email ~ '[[:cntrl:]]'
 OR p_role IS NULL OR p_role NOT IN ('FIRM_SUPERVISOR','ACCOUNTANT','ASSISTANT')
 OR p_hash IS NULL OR octet_length(p_hash)<>32 OR p_expires IS NULL
 OR p_expires<=clock_timestamp() OR p_expires>clock_timestamp()+interval '24 hours 30 seconds' THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='INVITATION_REJECTED'; END IF;
 IF NOT EXISTS(SELECT 1 FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id_if_assigned() AND command_id=p_command) THEN
 RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='JOURNAL_CONTEXT_REJECTED'; END IF;
 fingerprint:=identity_crypto.digest(convert_to(jsonb_build_array(p_email,p_role)::text,'UTF8'),'sha256');
 PERFORM pg_advisory_xact_lock(hashtextextended(jsonb_build_array(p_tenant,p_command)::text,3302));
 SELECT * INTO prior FROM identity_private.email_invitation_commands WHERE tenant_id=p_tenant AND command_id=p_command;
 IF prior.command_id IS NOT NULL THEN
 IF prior.actor_id<>actor OR prior.fingerprint<>fingerprint THEN RAISE EXCEPTION USING ERRCODE='23505',MESSAGE='COMMAND_CONFLICT'; END IF;
 RETURN QUERY SELECT i.id,i.expires_at,false FROM identity.invitations i WHERE i.tenant_id=p_tenant AND i.id=prior.invitation_id; RETURN;
 END IF;
 -- No issuer supplied by HTTP/SQL caller. Ambiguous/multiple provider links fail closed.
 SELECT count(*),min(e.issuer) INTO matches,issuer_key FROM identity_private.external_identities e WHERE e.user_id=actor;
 IF matches<>1 THEN RAISE EXCEPTION USING ERRCODE='P3301',MESSAGE='INVITATION_REJECTED'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(issuer_key,3303));
 SELECT count(*) INTO matches FROM identity_private.verified_contacts c JOIN identity_private.external_identities e USING(issuer,subject)
 JOIN identity_private.users u ON u.id=e.user_id AND u.status='ACTIVE' WHERE c.issuer=issuer_key AND c.email=p_email;
 IF matches<>1 THEN RAISE EXCEPTION USING ERRCODE='P3301',MESSAGE='INVITATION_REJECTED'; END IF;
 SELECT c.subject,e.user_id INTO recipient_subject,recipient FROM identity_private.verified_contacts c
 JOIN identity_private.external_identities e USING(issuer,subject) JOIN identity_private.users u ON u.id=e.user_id AND u.status='ACTIVE'
 WHERE c.issuer=issuer_key AND c.email=p_email FOR SHARE OF c,e,u;
 IF recipient IS NULL OR EXISTS(SELECT 1 FROM identity.memberships WHERE tenant_id=p_tenant AND user_id=recipient) THEN
 RAISE EXCEPTION USING ERRCODE='P3301',MESSAGE='INVITATION_REJECTED'; END IF;
 invitation:=identity.create_invitation(p_tenant,recipient_subject,issuer_key,p_role,p_hash,p_expires);
 INSERT INTO identity_private.email_invitation_commands VALUES(p_tenant,p_command,actor,fingerprint,invitation);
 RETURN QUERY SELECT invitation,p_expires,true;
END;
$$;
REVOKE ALL ON FUNCTION identity.record_verified_local_contact(text,text,text,text,text),
 identity.invite_verified_local_email(uuid,uuid,text,text,bytea,timestamptz),identity_private.prevent_duplicate_invitation()
 FROM PUBLIC,fiscuvia_runtime,fiscuvia_sessions;
GRANT EXECUTE ON FUNCTION identity.record_verified_local_contact(text,text,text,text,text),
 identity.invite_verified_local_email(uuid,uuid,text,text,bytea,timestamptz) TO fiscuvia_runtime;
