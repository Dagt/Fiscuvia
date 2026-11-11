package com.fiscuvia.entitlements.internal;

import com.fiscuvia.entitlements.*;
import com.fiscuvia.identity.AuthorizedAccess;
import com.fiscuvia.identity.Capability;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class JdbcCapacity implements Capacity {
    private final JdbcTemplate jdbc;
    private final com.fiscuvia.audit.AuditJournal journal;
    JdbcCapacity(DataSource source, com.fiscuvia.audit.AuditJournal journal) { jdbc=new JdbcTemplate(source); this.journal=journal; }
    public Result reserve(AuthorizedAccess access, Resource resource, UUID command, UUID reference, long units) {
        require(access); Objects.requireNonNull(resource); Objects.requireNonNull(command); Objects.requireNonNull(reference);
        if(units<=0 || units>9007199254740991L) throw new IllegalArgumentException("Invalid capacity units");
        if(resource!=Resource.USERS) return Result.NOT_INTEGRATED;
        if(units!=1) throw new IllegalArgumentException("One membership per reservation");
        journal.bindCommand(command);
        return Result.valueOf(jdbc.queryForObject("SELECT entitlement_api.reserve_user(?,?,?)",String.class,access.tenant().tenantId(),command,reference));
    }
    public Result release(AuthorizedAccess access, UUID reservation, UUID command) {
        require(access); Objects.requireNonNull(command); Objects.requireNonNull(reservation); journal.bindCommand(command);
        return Result.valueOf(jdbc.queryForObject("SELECT entitlement_api.release_user(?,?,?)",String.class,access.tenant().tenantId(),reservation,command));
    }
    private void require(AuthorizedAccess access) {
        access.requireCurrent();
        if(access.capability()!=Capability.MEMBERSHIP_INVITE) throw new SecurityException("Capacity authority required");
    }
}
