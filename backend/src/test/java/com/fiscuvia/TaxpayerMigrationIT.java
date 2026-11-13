package com.fiscuvia;

import java.nio.file.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TaxpayerMigrationIT {
    static final String NAME="V12__taxpayer_domain_and_secure_apis.sql";
    @Container final PostgresTestDatabase db=new PostgresTestDatabase();
    @TempDir Path dir;
    Flyway flyway(){return Flyway.configure().dataSource(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword)
        .locations("filesystem:"+dir).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load();}
    void base() throws Exception {
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:files.filter(p->p.getFileName().toString().matches("V(?:[1-9]|10|11)__.*\\.sql")).toList())Files.copy(p,dir.resolve(p.getFileName()));}
        flyway().migrate();
    }
    void copy() throws Exception {Files.copy(Path.of("src/main/resources/db/migration/"+NAME),dir.resolve(NAME),StandardCopyOption.REPLACE_EXISTING);}
    @Test void presentationUpgradeIsAdditiveAtomicAndPreservesV1ThroughV12() throws Exception {
        base();copy();flyway().migrate();var before=flyway().info().applied();
        String name="V13__taxpayer_presentation.sql";Path source=Path.of("src/main/resources/db/migration/"+name);
        Files.copy(source,dir.resolve(name));Files.writeString(dir.resolve(name),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        assertThat(flyway().info().current().getVersion().toString()).isEqualTo("12");
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT to_regprocedure('taxpayers.presentation(uuid,uuid)')")){
            r.next();assertThat(r.getObject(1)).isNull();
        }
        Files.copy(source,dir.resolve(name),StandardCopyOption.REPLACE_EXISTING);assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        var after=flyway().info().applied();for(int n=0;n<before.length;n++)assertThat(after[n].getChecksum()).isEqualTo(before[n].getChecksum());
        flyway().validate();assertThat(flyway().migrate().migrationsExecuted).isZero();
    }
    @Test void upgradePreservesEveryHistoricalChecksumAndRunsOnlyOnce() throws Exception {
        base();var before=flyway().info().applied();copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        var after=flyway().info().applied();assertThat(after).hasSize(before.length+1);
        for(int n=0;n<before.length;n++)assertThat(after[n].getChecksum()).isEqualTo(before[n].getChecksum());
        flyway().validate();assertThat(flyway().migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT taxpayers.approved_policy()")){
            r.next();assertThat(r.getBoolean(1)).isTrue();
        }
    }
    @Test void checksumDriftIsRejected() throws Exception {
        base();copy();flyway().migrate();var checksum=flyway().info().current().getChecksum();
        Files.writeString(dir.resolve(NAME),"\n-- intentional checksum drift\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);
    }
    @Test void failedDdlRollsBackAllTablesPoliciesAndFunctions() throws Exception {
        base();copy();Files.writeString(dir.resolve(NAME),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);
        assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT to_regclass('taxpayers.taxpayers'),(SELECT count(*) FROM access_control_private.role_capabilities WHERE capability='TAXPAYER_VIEW')")){
            r.next();assertThat(r.getObject(1)).isNull();assertThat(r.getInt(2)).isZero();
        }
        copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void unexpectedLegacyOrphanGrantStopsUpgradeWithoutDeletingOrInventingRecords() throws Exception {
        base();var tenant=java.util.UUID.randomUUID();var member=java.util.UUID.randomUUID();
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword)){
            c.setAutoCommit(false);var s=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));
            s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);s.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",tenant);
            QuotaFixtures.seed(s,tenant);s.update("INSERT INTO identity_private.users(id) VALUES(?)",member);
            s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,member,member);
            s.update("INSERT INTO access_control.resource_grants VALUES(?,?,'TAXPAYER',?,'TAXPAYER_VIEW',true,?)",tenant,member,java.util.UUID.randomUUID(),java.util.UUID.randomUUID());
            assertThat(s.queryForObject("SELECT count(*) FROM access_control.resource_grants WHERE resource_kind='TAXPAYER'",Integer.class)).isEqualTo(1);c.commit();
        }
        copy();assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class).hasMessageContaining("taxpayer_legacy_gate");
        assertThat(flyway().info().current().getVersion().toString()).isEqualTo("11");
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword)){
            c.setAutoCommit(false);var s=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            assertThat(s.queryForObject("SELECT count(*) FROM access_control.resource_grants",Integer.class)).isEqualTo(1);
            assertThat(s.queryForObject("SELECT to_regclass('taxpayers.taxpayers')",String.class)).isNull();c.commit();
        }
    }
}
