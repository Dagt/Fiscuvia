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
class AuthorizationMigrationIT {
    @Container final PostgresTestDatabase database=new PostgresTestDatabase();
    @TempDir Path migrations;
    @Test void upgradesExistingV3OnceAndPreservesEarlierChecksums() throws Exception {
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        base(); var before=flyway().info().applied(); copyV4();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        var after=flyway().info().applied(); assertThat(after).hasSize(5); // Includes Flyway schema creation.
        for(int i=0;i<before.length;i++) assertThat(after[i].getChecksum()).isEqualTo(before[i].getChecksum());
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
            var s=c.createStatement();var r=s.executeQuery("SELECT count(*),bool_and(resource_kind='TENANT' AND capability IN ('TENANT_READ','MEMBERSHIP_INVITE','MEMBERSHIP_DISABLE')) FROM access_control_private.role_capabilities")) {
            r.next(); assertThat(r.getInt(1)).isEqualTo(6); assertThat(r.getBoolean(2)).isTrue();
        }
    }
    @Test void authorizationChecksumTamperingFailsWithoutRewritingHistory() throws Exception {
        base(); copyV4(); flyway().migrate();
        var checksum=flyway().info().current().getChecksum();
        Files.writeString(migrations.resolve("V4__resource_authorization_foundation.sql"),"\n-- deliberate alteration\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void wholeAuthorizationMigrationRollsBackAndCanBeRetried() throws Exception {
        base(); copyV4(); Files.writeString(migrations.resolve("V4__resource_authorization_foundation.sql"),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
                var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regnamespace('access_control_private'),to_regnamespace('access_control')")) {
            rows.next(); for(int i=1;i<=2;i++) assertThat(rows.getObject(i)).isNull();
        }
        copyV4(); assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
    }
    private void base() throws Exception { copy("V1__initialize_infrastructure.sql"); copy("V2__tenant_isolation_foundation.sql"); copy("V3__identity_memberships_and_sessions.sql"); flyway().migrate(); }
    private void copyV4() throws Exception { copy("V4__resource_authorization_foundation.sql"); }
    private void copy(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/db/migration/"+name)) { Files.writeString(migrations.resolve(name),new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
    }
    private Flyway flyway() { return Flyway.configure().dataSource(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)
            .locations("filesystem:"+migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load(); }
}
