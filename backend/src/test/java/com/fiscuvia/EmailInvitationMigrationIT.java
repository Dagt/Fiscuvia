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
class EmailInvitationMigrationIT {
 static final String NAME="V10__verified_local_email_invitations.sql";
 @Container final PostgresTestDatabase db=new PostgresTestDatabase();
 @TempDir Path dir;
 void base() throws Exception {try(var paths=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:paths.filter(p->p.getFileName().toString().matches("V[1-9]__.*\\.sql")).toList())Files.copy(p,dir.resolve(p.getFileName()));}flyway().migrate();}
 void copy() throws Exception {Files.copy(Path.of("src/main/resources/db/migration/"+NAME),dir.resolve(NAME),StandardCopyOption.REPLACE_EXISTING);}
 Flyway flyway(){return Flyway.configure().dataSource(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword).locations("filesystem:"+dir).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load();}
 @Test void upgradePreservesV1ThroughV9AndRunsOnlyOnce() throws Exception {
  base();var before=flyway().info().applied();copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();assertThat(flyway().migrate().migrationsExecuted).isZero();
  var after=flyway().info().applied();assertThat(after).hasSize(before.length+1);for(int n=0;n<before.length;n++)assertThat(after[n].getChecksum()).isEqualTo(before[n].getChecksum());
  try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM access_control_private.role_capabilities WHERE capability IN ('MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE')")){r.next();assertThat(r.getInt(1)).isZero();}
 }
 @Test void checksumAlterationFailsWithoutRewritingHistory() throws Exception {base();copy();flyway().migrate();var checksum=flyway().info().current().getChecksum();Files.writeString(dir.resolve(NAME),"\n-- deliberate checksum alteration\n",StandardOpenOption.APPEND);assertThatThrownBy(()->flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");assertThat(flyway().info().current().getChecksum()).isEqualTo(checksum);}
 @Test void failedMigrationRollsBackContactsReceiptsAndTrigger() throws Exception {
  base();copy();Files.writeString(dir.resolve(NAME),"\nTHIS IS NOT SQL;\n",StandardOpenOption.APPEND);assertThatThrownBy(()->flyway().migrate()).isInstanceOf(FlywayException.class);
  try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword);var s=c.createStatement();var r=s.executeQuery("SELECT to_regclass('identity_private.verified_contacts'),to_regclass('identity_private.email_invitation_commands'),(SELECT count(*) FROM pg_trigger WHERE tgname='invitation_recipient_unique')")){r.next();assertThat(r.getObject(1)).isNull();assertThat(r.getObject(2)).isNull();assertThat(r.getInt(3)).isZero();}
  copy();assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);flyway().validate();
 }
}
