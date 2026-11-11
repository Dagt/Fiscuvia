package com.fiscuvia;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TeamAdministrationMigrationIT {
 static final String NAME="V11__approved_team_administration.sql";
 @Container final PostgresTestDatabase db=new PostgresTestDatabase();
 @TempDir Path dir;
 void base() throws Exception {try(var paths=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:paths.filter(p->p.getFileName().toString().matches("V(?:[1-9]|10)__.*\\.sql")).toList())Files.copy(p,dir.resolve(p.getFileName()));}flyway().migrate();}
 void copy() throws Exception {Files.copy(Path.of("src/main/resources/db/migration/"+NAME),dir.resolve(NAME),StandardCopyOption.REPLACE_EXISTING);}
 Flyway flyway(){return Flyway.configure().dataSource(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword).locations("filesystem:"+dir).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load();}
 @Test void upgradePreservesV1ThroughV10AndRunsOnlyOnce() throws Exception {
  base();var before=flyway().info().applied();copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();assertThat(flyway().migrate().migrationsExecuted).isZero();
  var after=flyway().info().applied();assertThat(after).hasSize(before.length+1);for(int n=0;n<before.length;n++)assertThat(after[n].getChecksum()).isEqualTo(before[n].getChecksum());
  try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM access_control_private.role_capabilities WHERE capability IN ('MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE')")){r.next();assertThat(r.getInt(1)).isEqualTo(3);}
 }
 @Test void checksumAlterationFailsWithoutRewritingHistory() throws Exception {base();copy();flyway().migrate();var checksum=flyway().info().current().getChecksum();Files.writeString(dir.resolve(NAME),"\n-- deliberate checksum alteration\n",StandardOpenOption.APPEND);assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);}
 @Test void failedMigrationRollsBackColumnsPolicyReceiptsAndFunctions() throws Exception {
  base();copy();Files.writeString(dir.resolve(NAME),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
  try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var q=c.createStatement();var r=q.executeQuery("SELECT to_regclass('identity_private.team_commands'),to_regprocedure('identity.team_members(uuid,integer,integer,text,text)'),(SELECT count(*) FROM information_schema.columns WHERE table_schema='identity' AND table_name='memberships' AND column_name='version'),(SELECT count(*) FROM access_control_private.role_capabilities WHERE capability='MEMBERSHIP_VIEW')")) {
   r.next();assertThat(r.getObject(1)).isNull();assertThat(r.getObject(2)).isNull();assertThat(r.getInt(3)).isZero();assertThat(r.getInt(4)).isZero();
  }
  copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();
 }
}
