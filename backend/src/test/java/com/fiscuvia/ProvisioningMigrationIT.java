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
class ProvisioningMigrationIT {
    final java.util.UUID legacy=java.util.UUID.randomUUID();
    @Container final PostgresTestDatabase database=new PostgresTestDatabase();
    @TempDir Path migrations;
    @Test void upgradesExistingV5OnceAndPreservesEarlierChecksums() throws Exception {
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        base(); var before=flyway().info().applied(); copyV6();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        var after=flyway().info().applied(); assertThat(after).hasSize(7); // Includes Flyway schema creation.
        for(int i=0;i<before.length;i++) assertThat(after[i].getChecksum()).isEqualTo(before[i].getChecksum());
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
            var st=c.prepareStatement("SELECT commercial_name,status,version FROM tenant_private.profiles WHERE tenant_id=?")) {
            st.setObject(1,legacy);try(var r=st.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isNull();assertThat(r.getString(2)).isEqualTo("ACTIVE");assertThat(r.getLong(3)).isZero();}
        }
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
            var st=c.createStatement();var r=st.executeQuery("SELECT count(*) FROM journal_private.delivery_scopes")) {
            r.next();assertThat(r.getInt(1)).isZero();
        }
    }
    @Test void provisioningChecksumTamperingFailsWithoutRewritingHistory() throws Exception {
        base(); copyV6(); flyway().migrate();
        var checksum=flyway().info().current().getChecksum();
        Files.writeString(migrations.resolve("V6__tenant_provisioning_and_profiles.sql"),"\n-- deliberate alteration\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void wholeProvisioningMigrationRollsBackAndCanBeRetried() throws Exception {
        base(); copyV6(); Files.writeString(migrations.resolve("V6__tenant_provisioning_and_profiles.sql"),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
                var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regnamespace('tenant_private'),to_regnamespace('tenant_api')")) {
            rows.next(); for(int i=1;i<=2;i++) assertThat(rows.getObject(i)).isNull();
        }
        copyV6(); assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
    }
    private void base() throws Exception {
        copy("V1__initialize_infrastructure.sql"); copy("V2__tenant_isolation_foundation.sql"); copy("V3__identity_memberships_and_sessions.sql"); copy("V4__resource_authorization_foundation.sql"); copy("V5__audit_journal_and_outbox.sql"); flyway().migrate();
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)){
            c.setAutoCommit(false);try(var bind=c.prepareStatement("SELECT app.establish_tenant_context(?)")){bind.setObject(1,legacy);bind.execute();}
            try(var insert=c.prepareStatement("INSERT INTO app.tenants(tenant_id) VALUES(?)")){insert.setObject(1,legacy);insert.executeUpdate();}c.commit();
        }
    }
    private void copyV6() throws Exception { copy("V6__tenant_provisioning_and_profiles.sql"); }
    private void copy(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/db/migration/"+name)) { Files.writeString(migrations.resolve(name),new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
    }
    private Flyway flyway() { return Flyway.configure().dataSource(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)
            .locations("filesystem:"+migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load(); }
}
