package com.fiscuvia.identity.internal;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Structural fail-closed checks; not protection against a compromised DBA/BFF. */
@Component
class LocalInvitationGuards implements ApplicationRunner {
    private final JdbcTemplate sql;
    LocalInvitationGuards(DataSource source){sql=new JdbcTemplate(source);}
    @Override public void run(ApplicationArguments ignored) {
        for(String table:java.util.List.of("identity_private.verified_contacts","identity_private.email_invitation_commands")) {
            Boolean safe=sql.queryForObject("""
                SELECT c.relrowsecurity AND c.relforcerowsecurity AND pg_get_userbyid(c.relowner)='fiscuvia_migrator'
                AND NOT has_table_privilege('fiscuvia_runtime',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                AND NOT has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='identity_private' AND c.relname=?
                """,Boolean.class,table.substring(table.indexOf('.')+1));
            if(!Boolean.TRUE.equals(safe))throw new IllegalStateException("Identity contact boundary rejected");
        }
        if(!Boolean.TRUE.equals(sql.queryForObject("""
            SELECT count(*)=1 FROM pg_trigger t JOIN pg_proc p ON p.oid=t.tgfoid
            WHERE t.tgrelid='identity.invitations'::regclass AND t.tgname='invitation_recipient_unique'
            AND t.tgenabled='O' AND t.tgtype=7 AND p.proname='prevent_duplicate_invitation'
            AND p.prosecdef AND pg_get_userbyid(p.proowner)='fiscuvia_migrator'
            """,Boolean.class)))throw new IllegalStateException("Invitation recipient boundary rejected");
    }
}
