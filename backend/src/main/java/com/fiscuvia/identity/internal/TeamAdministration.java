package com.fiscuvia.identity.internal;

import com.fiscuvia.identity.*;
import com.fiscuvia.audit.AuditJournal;
import com.fiscuvia.platform.ApiFailure;
import com.fiscuvia.platform.ApiErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Only authorized tenant transactions; no principal/role/issuer accepted from HTTP. */
@Service
class TeamAdministration {
    record Member(UUID membershipId,String displayName,String email,List<String> roles,String status,String identityStatus,
                  long version,Instant joinedAt,Instant changedAt,boolean canManage) {}
    record Invitation(UUID invitationId,String displayName,String email,String role,String status,long version,Instant createdAt,Instant expiresAt) {}
    record Members(List<Member> items,int page,int size,long total) {}
    record Invitations(List<Invitation> items,int page,int size,long total) {}
    record Result(UUID targetId,long version,boolean replayed) {}
    enum Action { SUSPEND, REVOKE, REACTIVATE, SET_ROLE, REVOKE_INVITATION }
    private final AuthorizedTenantOperations authorized;
    private final AuditJournal journal;
    private final JdbcTemplate sql;
    private final JsonMapper json;
    TeamAdministration(AuthorizedTenantOperations authorized,AuditJournal journal,DataSource source,JsonMapper json){
        this.authorized=authorized;this.journal=journal;this.sql=new JdbcTemplate(source);this.json=json;
    }
    Members members(UUID tenant,int page,int size,String search,String status){
        return authorized.execute(tenant,Capability.MEMBERSHIP_VIEW,ResourceTarget.tenant(),access->
            json.readValue(sql.queryForObject("SELECT identity.team_members(?,?,?,?,?)::text",String.class,tenant,page,size,search,status),Members.class));
    }
    Invitations invitations(UUID tenant,int page,int size,String search,String status){
        return authorized.execute(tenant,Capability.MEMBERSHIP_VIEW,ResourceTarget.tenant(),access->
            json.readValue(sql.queryForObject("SELECT identity.team_invitations(?,?,?,?,?)::text",String.class,tenant,page,size,search,status),Invitations.class));
    }
    Result command(UUID tenant,UUID command,UUID target,long expected,Action action,String value){
        if(action==null)throw new ApiFailure(ApiErrorCode.VALIDATION_FAILED);
        var capability=switch(action){
            case SET_ROLE -> Capability.MEMBERSHIP_ASSIGN_ROLE;
            case REACTIVATE -> Capability.MEMBERSHIP_REACTIVATE;
            case REVOKE_INVITATION -> Capability.MEMBERSHIP_INVITE;
            default -> Capability.MEMBERSHIP_DISABLE;
        };
        return authorized.execute(tenant,capability,ResourceTarget.tenant(),access->{
            journal.bindCommand(command);
            return json.readValue(sql.queryForObject("SELECT identity.team_command(?,?,?,?,?,?)::text",String.class,
                tenant,command,target,expected,action.name(),value),Result.class);
        });
    }
}
