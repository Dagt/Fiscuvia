package com.fiscuvia;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class MembershipCapacityMigrationIT {
    final java.util.UUID legacy=java.util.UUID.randomUUID();
    @Container final PostgresTestDatabase database=new PostgresTestDatabase();
    @TempDir Path migrations;
    @Test void upgradesExistingV8OnceAndPreservesEarlierChecksums() throws Exception {
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        base(); var before=flyway().info().applied(); copyV9();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        var after=flyway().info().applied(); assertThat(after).hasSize(10); // Includes Flyway schema creation.
        for(int i=0;i<before.length;i++) assertThat(after[i].getChecksum()).isEqualTo(before[i].getChecksum());
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);var st=c.createStatement()) {
            c.setAutoCommit(false);try(var bind=c.prepareStatement("SELECT app.establish_tenant_context(?)")){bind.setObject(1,legacy);bind.execute();}
            try(var r=st.executeQuery("SELECT count(*) FROM entitlement_private.limits")){r.next();assertThat(r.getInt(1)).isZero();}
            try(var r=st.executeQuery("SELECT count(*) FROM app.tenants")){r.next();assertThat(r.getInt(1)).isEqualTo(1);} try(var r=st.executeQuery("SELECT count(*) FROM identity.memberships WHERE status='ACTIVE'")){r.next();assertThat(r.getInt(1)).isEqualTo(1);} c.commit();
        }
    }
    @Test void entitlementChecksumTamperingFailsWithoutRewritingHistory() throws Exception {
        base(); copyV9(); flyway().migrate();
        var checksum=flyway().info().current().getChecksum();
        Files.writeString(migrations.resolve("V9__approve_plan_view_and_membership_capacity.sql"),"\n-- deliberate alteration\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void wholeEntitlementMigrationRollsBackAndCanBeRetried() throws Exception {
        base(); copyV9(); Files.writeString(migrations.resolve("V9__approve_plan_view_and_membership_capacity.sql"),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
                var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regprocedure('entitlement_api.approved_view_policy()'),(SELECT tgname FROM pg_trigger WHERE tgname='entitlement_capacity')")) {
            rows.next(); for(int i=1;i<=2;i++) assertThat(rows.getObject(i)).isNull();
        }
        copyV9(); assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
    }
    private void base() throws Exception {
        copy("V1__initialize_infrastructure.sql"); copy("V2__tenant_isolation_foundation.sql"); copy("V3__identity_memberships_and_sessions.sql"); copy("V4__resource_authorization_foundation.sql"); copy("V5__audit_journal_and_outbox.sql"); copy("V6__tenant_provisioning_and_profiles.sql"); copy("V7__approve_limited_tenant_settings_policy.sql"); copy("V8__tenant_entitlements.sql"); flyway().migrate();
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)){
            c.setAutoCommit(false);try(var bind=c.prepareStatement("SELECT app.establish_tenant_context(?)")){bind.setObject(1,legacy);bind.execute();}
            try(var insert=c.prepareStatement("INSERT INTO app.tenants(tenant_id) VALUES(?)")){insert.setObject(1,legacy);insert.executeUpdate();}
            try(var u=c.prepareStatement("INSERT INTO identity_private.users(id) VALUES(?)")){u.setObject(1,legacy);u.executeUpdate();}
            try(var m=c.prepareStatement("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')")){m.setObject(1,legacy);m.setObject(2,legacy);m.setObject(3,legacy);m.executeUpdate();}
            c.commit();
        }
    }
    private void copyV9() throws Exception { copy("V9__approve_plan_view_and_membership_capacity.sql"); }
    private void copy(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/db/migration/"+name)) { Files.writeString(migrations.resolve(name),new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
    }
    private Flyway flyway() { return Flyway.configure().dataSource(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)
            .locations("filesystem:"+migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load(); }
}
