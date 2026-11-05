package com.fiscuvia;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Real upgrade from immutable V1-V6; no fixture grants. */
@Testcontainers
class TenantSettingsPolicyMigrationIT {
    static final String V7="V7__approve_limited_tenant_settings_policy.sql";
    static final String APPROVAL="https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209";
    @Container final PostgresTestDatabase database=new PostgresTestDatabase();
    @TempDir Path migrations;
    Connection migration() throws SQLException {return DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);}
    List<String> policies() throws Exception {
        var result=new ArrayList<String>();try(var c=migration();var s=c.createStatement();var r=s.executeQuery("SELECT row(role_code,capability,resource_kind,requires_mfa,approval_reference)::text FROM access_control_private.role_capabilities ORDER BY role_code,capability,resource_kind")) {
            while(r.next())result.add(r.getString(1));
        }return result;
    }
    @Test void v7AddsOnlyTheApprovedPolicyOnceAndPreservesV1ThroughV6Checksums() throws Exception {
        base();var before=flyway().info().applied();var originalPolicies=policies();copy(V7);
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        var after=flyway().info().applied();assertThat(after).hasSize(before.length+1);
        for(int i=0;i<before.length;i++)assertThat(after[i].getChecksum()).isEqualTo(before[i].getChecksum());
        var installed=policies();assertThat(installed).hasSize(originalPolicies.size()+1).containsAll(originalPolicies);
        try(var c=migration();var s=c.createStatement();var r=s.executeQuery("SELECT role_code,resource_kind,requires_mfa,approval_reference FROM access_control_private.role_capabilities WHERE capability='TENANT_SETTINGS_MANAGE'")) {
            assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("FIRM_OWNER_ADMIN");assertThat(r.getString(2)).isEqualTo("TENANT");
            assertThat(r.getBoolean(3)).isTrue();assertThat(r.getString(4)).isEqualTo(APPROVAL);assertThat(r.next()).isFalse();
        }
    }
    @Test void failedV7RollsBackTheGrantAndCanBeRetriedWithoutChangingV6() throws Exception {
        base();var before=policies();copy(V7);Files.writeString(migrations.resolve(V7),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        assertThat(policies()).isEqualTo(before);assertThat(flyway().info().current().getVersion().toString()).isEqualTo("6");
        copy(V7);assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();
    }
    @Test void appliedV7RejectsChecksumTampering() throws Exception {
        base();copy(V7);flyway().migrate();var checksum=flyway().info().current().getChecksum();
        Files.writeString(migrations.resolve(V7),"\n-- deliberate checksum alteration\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void runtimeAndSessionsCannotReadOrAlterPolicyAfterV7() throws Exception {
        base();copy(V7);flyway().migrate();
        for(var role:Map.of(PostgresTestDatabase.RUNTIME_USER,database.runtimePassword,"fiscuvia_sessions",database.sessionPassword).entrySet()) {
            try(var c=DriverManager.getConnection(database.getJdbcUrl(),role.getKey(),role.getValue());var s=c.createStatement()) {
                for(String query:List.of("SELECT * FROM access_control_private.role_capabilities",
                    "UPDATE access_control_private.role_capabilities SET requires_mfa=false WHERE capability='TENANT_SETTINGS_MANAGE'",
                    "DELETE FROM access_control_private.role_capabilities WHERE capability='TENANT_SETTINGS_MANAGE'"))
                    assertThatThrownBy(()->s.execute(query)).isInstanceOf(SQLException.class).extracting(e->((SQLException)e).getSQLState()).isEqualTo("42501");
            }
        }
    }
    void base() throws Exception {
        for(String name:List.of("V1__initialize_infrastructure.sql","V2__tenant_isolation_foundation.sql","V3__identity_memberships_and_sessions.sql",
            "V4__resource_authorization_foundation.sql","V5__audit_journal_and_outbox.sql","V6__tenant_provisioning_and_profiles.sql"))copy(name);
        flyway().migrate();assertThat(policies()).noneMatch(p->p.contains("TENANT_SETTINGS_MANAGE"));
    }
    void copy(String name) throws Exception {try(var in=getClass().getResourceAsStream("/db/migration/"+name)){Files.writeString(migrations.resolve(name),new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    Flyway flyway(){return Flyway.configure().dataSource(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)
        .locations("filesystem:"+migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load();}
}
