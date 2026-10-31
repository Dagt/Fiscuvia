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
class JournalMigrationIT {
    @Container final PostgresTestDatabase database=new PostgresTestDatabase();
    @TempDir Path migrations;
    @Test void upgradesExistingV4OnceAndPreservesEarlierChecksums() throws Exception {
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        assertThat(database.execInContainer("sh","/tmp/fiscuvia-upgrade-session-role.sh").getExitCode()).isZero();
        base(); var before=flyway().info().applied(); copyV5();
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        var after=flyway().info().applied(); assertThat(after).hasSize(6); // Includes Flyway schema creation.
        for(int i=0;i<before.length;i++) assertThat(after[i].getChecksum()).isEqualTo(before[i].getChecksum());
        try(var c=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
            var st=c.createStatement();var r=st.executeQuery("SELECT count(*) FROM journal_private.delivery_scopes")) {
            r.next();assertThat(r.getInt(1)).isZero();
        }
    }
    @Test void journalChecksumTamperingFailsWithoutRewritingHistory() throws Exception {
        base(); copyV5(); flyway().migrate();
        var checksum=flyway().info().current().getChecksum();
        Files.writeString(migrations.resolve("V5__audit_journal_and_outbox.sql"),"\n-- deliberate alteration\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void wholeJournalMigrationRollsBackAndCanBeRetried() throws Exception {
        base(); copyV5(); Files.writeString(migrations.resolve("V5__audit_journal_and_outbox.sql"),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);
                var statement=connection.createStatement();var rows=statement.executeQuery("SELECT to_regnamespace('journal_private'),to_regnamespace('journal')")) {
            rows.next(); for(int i=1;i<=2;i++) assertThat(rows.getObject(i)).isNull();
        }
        copyV5(); assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1); flyway().validate();
    }
    private void base() throws Exception { copy("V1__initialize_infrastructure.sql"); copy("V2__tenant_isolation_foundation.sql"); copy("V3__identity_memberships_and_sessions.sql"); copy("V4__resource_authorization_foundation.sql"); flyway().migrate(); }
    private void copyV5() throws Exception { copy("V5__audit_journal_and_outbox.sql"); }
    private void copy(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/db/migration/"+name)) { Files.writeString(migrations.resolve(name),new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
    }
    private Flyway flyway() { return Flyway.configure().dataSource(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)
            .locations("filesystem:"+migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load(); }
}
