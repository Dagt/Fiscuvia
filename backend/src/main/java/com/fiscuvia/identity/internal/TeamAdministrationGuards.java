package com.fiscuvia.identity.internal;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reject drift of the approved policy/private receipt boundary rather than opening team access. */
@Component
class TeamAdministrationGuards implements ApplicationRunner {
    private final JdbcTemplate sql;
    TeamAdministrationGuards(DataSource source){sql=new JdbcTemplate(source);}
    @Override public void run(ApplicationArguments ignored){
        Boolean safe=sql.queryForObject("""
            SELECT identity.approved_team_policy()
            AND (SELECT c.relrowsecurity AND c.relforcerowsecurity AND pg_get_userbyid(c.relowner)='fiscuvia_migrator'
              AND NOT has_table_privilege('fiscuvia_runtime',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
              AND NOT has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
              FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='identity_private' AND c.relname='team_commands')
            AND (SELECT count(*)=2 FROM pg_trigger WHERE tgrelid IN ('identity.memberships'::regclass,'identity.invitations'::regclass)
              AND tgname='team_version' AND tgenabled='O' AND tgtype=19)
            """,Boolean.class);
        if(!Boolean.TRUE.equals(safe))throw new IllegalStateException("Team policy boundary rejected");
    }
}
