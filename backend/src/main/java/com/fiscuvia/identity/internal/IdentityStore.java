package com.fiscuvia.identity.internal;

import com.fiscuvia.identity.AuthorizedTenantOperations;
import com.fiscuvia.identity.AuthorizedAccess;
import com.fiscuvia.identity.Capability;
import com.fiscuvia.identity.ResourceTarget;
import com.fiscuvia.platform.AccessDecisionEvent;
import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.ApiFailure;
import com.fiscuvia.platform.TenantTransactions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class IdentityStore implements AuthorizedTenantOperations {
    record Actor(UUID id,long version) {}
    record Membership(UUID tenantId,UUID membershipId,String role) {}
    record Me(UUID userId,List<Membership> memberships,boolean privilegedMfaVerified) {}
    record Invitation(UUID invitationId,String token,Instant expiresAt) {
        @Override public String toString() { return "Invitation[private]"; }
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TenantTransactions tenants;
    private final IdentitySettings settings;

    IdentityStore(DataSource source,PlatformTransactionManager manager,TenantTransactions tenants,IdentitySettings settings) {
        jdbc=new JdbcTemplate(source); transactions=new TransactionTemplate(manager); this.tenants=tenants; this.settings=settings;
    }
    private <T> T transaction(Supplier<T> work) {
        try { return transactions.execute(status -> work.get()); }
        catch (DataAccessException rejected) {
            Throwable cause=rejected;
            while (!(cause instanceof java.sql.SQLException) && cause.getCause()!=null) cause=cause.getCause();
            String state=cause instanceof java.sql.SQLException sql ? sql.getSQLState() : "";
            throw new ApiFailure("42501".equals(state) ? ApiErrorCode.ACCESS_DENIED
                    : "23505".equals(state) ? ApiErrorCode.CONFLICT : ApiErrorCode.INTERNAL_ERROR);
        }
    }
    Actor resolve(String issuer,String subject,long mfaAt) {
        return transaction(() -> bind(issuer,subject,mfaAt));
    }
    private Actor bind(String issuer,String subject,long mfaAt) {
        if (issuer==null || !issuer.equals(settings.issuer()) || subject==null || subject.length()>255
                || subject.isBlank() || subject.contains("\n") || subject.contains("\r")) throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        String tx=jdbc.queryForObject("SELECT pg_current_xact_id()::text",String.class);
        String payload=issuer+"\n"+subject+"\n"+tx+"\n"+mfaAt;
        byte[] signature;
        try {
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(settings.proofKey()),"HmacSHA256"));
            signature=mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException impossible) { throw new IllegalStateException("Actor proof unavailable"); }
        return jdbc.queryForObject("SELECT user_id,auth_version FROM identity.bind_actor(?,?,?,?)",
                (row,n)->new Actor(row.getObject(1,UUID.class),row.getLong(2)),issuer,subject,mfaAt,HexFormat.of().formatHex(signature));
    }
    static AppPrincipal principal() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal actor))
            throw new ApiFailure(ApiErrorCode.AUTHENTICATION_REQUIRED);
        return actor;
    }
    private Actor validate(AppPrincipal principal) {
        if (principal.absoluteExpiry()<=Instant.now().getEpochSecond()) throw new ApiFailure(ApiErrorCode.AUTHENTICATION_REQUIRED);
        var actor=bind(principal.issuer(),principal.subject(),principal.mfaAt());
        if (!actor.id().equals(principal.userId()) || actor.version()!=principal.authVersion())
            throw new ApiFailure(ApiErrorCode.AUTHENTICATION_REQUIRED);
        return actor;
    }
    void validateSession(AppPrincipal principal) { transaction(()->{ validate(principal); return null; }); }
    Me me() {
        return transaction(()->{
            var principal=principal(); var actor=validate(principal);
            var members=jdbc.query("""
                    SELECT m.tenant_id,m.id,r.role_code FROM identity.memberships m
                    JOIN identity.membership_roles r ON r.tenant_id=m.tenant_id AND r.membership_id=m.id
                    WHERE m.user_id=? AND m.status='ACTIVE' ORDER BY m.tenant_id,r.role_code
                    """,(row,n)->new Membership(row.getObject(1,UUID.class),row.getObject(2,UUID.class),row.getString(3)),actor.id());
            return new Me(actor.id(),members,principal.mfaAt()>=Instant.now().getEpochSecond()-600 && principal.mfaAt()>0);
        });
    }
    @Override public <T> T execute(UUID tenant,Capability capability,ResourceTarget target,java.util.function.Function<AuthorizedAccess,T> work) {
        if (tenant==null || capability==null || target==null || work==null) throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        try {
            return transaction(()->{
                var actor=validate(principal());
                String code=jdbc.queryForObject("SELECT access_control.authorize_operation(?,?,?,?)",String.class,
                        tenant,capability.name(),target.kind().name(),target.id());
                var reason=AccessDecisionEvent.Reason.valueOf(code);
                decision(capability,target,reason);
                if(reason!=AccessDecisionEvent.Reason.AUTHORIZED)
                    throw new ApiFailure(target.id()!=null && reason!=AccessDecisionEvent.Reason.CONTEXT_REJECTED
                            ? ApiErrorCode.NOT_FOUND : ApiErrorCode.ACCESS_DENIED);
                return tenants.execute(tenant,()->{
                    var access=new TransactionAccess(actor.id(),tenant,capability,target,
                            jdbc.queryForObject("SELECT pg_current_xact_id()::text",String.class));
                    try { return work.apply(access); }
                    finally { access.active=false; }
                });
            });
        } catch(ApiFailure rejected) {
            if(rejected.code()==ApiErrorCode.AUTHENTICATION_REQUIRED) decision(capability,target,AccessDecisionEvent.Reason.AUTHENTICATION_REQUIRED);
            throw rejected;
        }
    }
    private final class TransactionAccess implements AuthorizedAccess {
        private final Actor actor; private final Tenant tenant; private final Capability capability;
        private final ResourceTarget target; private final String tx; private boolean active=true;
        TransactionAccess(UUID actor,UUID tenant,Capability capability,ResourceTarget target,String tx) {
            this.actor=new Actor(actor); this.tenant=new Tenant(tenant); this.capability=capability; this.target=target; this.tx=tx;
        }
        public Actor actor(){ requireCurrent(); return actor; }
        public Tenant tenant(){ requireCurrent(); return tenant; }
        public Capability capability(){ requireCurrent(); return capability; }
        public ResourceTarget target(){ requireCurrent(); return target; }
        public void requireCurrent() {
            if(!active || !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !tx.equals(jdbc.queryForObject("SELECT pg_current_xact_id_if_assigned()::text",String.class)))
                throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        }
    }
    private static void decision(Capability capability,ResourceTarget target,AccessDecisionEvent.Reason reason) {
        var operation=switch(capability) {
            case TENANT_READ,MEMBERSHIP_VIEW,TAXPAYER_VIEW,DOCUMENT_VIEW,REPORT_VIEW,AUDIT_VIEW -> AccessDecisionEvent.Operation.READ;
            case MEMBERSHIP_INVITE -> AccessDecisionEvent.Operation.INVITE;
            case TAXPAYER_CREATE -> AccessDecisionEvent.Operation.CREATE;
            case TAXPAYER_EDIT -> AccessDecisionEvent.Operation.EDIT;
            case TAXPAYER_EXPORT,REPORT_EXPORT -> AccessDecisionEvent.Operation.EXPORT;
            case DOCUMENT_DOWNLOAD -> AccessDecisionEvent.Operation.DOWNLOAD;
            case TAXPAYER_ASSIGN,MEMBERSHIP_ASSIGN_ROLE -> AccessDecisionEvent.Operation.ASSIGN;
            default -> AccessDecisionEvent.Operation.MANAGE;
        };
        var event=new AccessDecisionEvent(reason==AccessDecisionEvent.Reason.AUTHORIZED ? AccessDecisionEvent.Decision.PERMIT
                : AccessDecisionEvent.Decision.DENY,reason,AccessDecisionEvent.Category.valueOf(target.kind().name()),operation);
        org.slf4j.LoggerFactory.getLogger("com.fiscuvia.identity.authorization").atInfo().addKeyValue("accessDecision",event).log("Access decision");
    }
    Membership select(UUID tenant) {
        return execute(tenant,Capability.TENANT_READ,ResourceTarget.tenant(),access->jdbc.queryForObject("""
                SELECT m.tenant_id,m.id,r.role_code FROM identity.memberships m JOIN identity.membership_roles r
                ON r.tenant_id=m.tenant_id AND r.membership_id=m.id WHERE m.user_id=identity.current_actor_id()
                AND m.tenant_id=? AND m.status='ACTIVE' ORDER BY r.role_code LIMIT 1
                """,(row,n)->new Membership(row.getObject(1,UUID.class),row.getObject(2,UUID.class),row.getString(3)),tenant));
    }
    Invitation invite(UUID tenant,String subject,String role) {
        return execute(tenant,Capability.MEMBERSHIP_INVITE,ResourceTarget.tenant(),access->{
            jdbc.queryForObject("SELECT identity.authorize_tenant(?,true)",Object.class,tenant);
            byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); Instant expires=Instant.now().plusSeconds(86400);
            UUID id=jdbc.queryForObject("SELECT identity.create_invitation(?,?,?,?,?,?)",UUID.class,tenant,subject,settings.issuer(),role,
                    hash(token),java.sql.Timestamp.from(expires));
            return new Invitation(id,token,expires);
        });
    }
    void revokeInvite(UUID tenant,UUID id) {
        execute(tenant,Capability.MEMBERSHIP_INVITE,ResourceTarget.tenant(),access->{
            jdbc.queryForObject("SELECT identity.authorize_tenant(?,true)",Object.class,tenant);
            return jdbc.queryForObject("SELECT identity.revoke_invitation(?,?)",Boolean.class,tenant,id);
        });
    }
    void changeMember(UUID tenant,UUID id,String state) {
        execute(tenant,Capability.MEMBERSHIP_DISABLE,ResourceTarget.tenant(),access->{
            jdbc.queryForObject("SELECT identity.authorize_tenant(?,true)",Object.class,tenant);
            return jdbc.queryForObject("SELECT identity.change_membership(?,?,?)",Boolean.class,tenant,id,state);
        });
    }
    UUID accept(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        return transaction(()->{ validate(principal()); return jdbc.queryForObject("SELECT identity.accept_invitation(?)",UUID.class,hash(token)); });
    }
    private static byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Hash unavailable"); }
    }
}
