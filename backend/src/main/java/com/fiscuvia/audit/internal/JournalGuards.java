package com.fiscuvia.audit.internal;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import com.fiscuvia.platform.StartupGuardFailure;

@Configuration(proxyBeanMethods=false)
class JournalGuards {
    @Bean @DependsOnDatabaseInitialization InitializingBean verifyJournal(DataSource source){return ()->{
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("""
            SELECT NOT has_schema_privilege('journal_private','USAGE') AND NOT has_schema_privilege('journal','CREATE')
              AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='journal' AND p.proname IN ('claim','lock_delivery','read_delivery','complete_delivery')
                AND has_function_privilege(p.oid,'EXECUTE'))
              AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('journal','journal_private') AND c.relkind='r' AND (
                  NOT c.relrowsecurity OR NOT c.relforcerowsecurity OR
                  has_table_privilege(c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') OR
                  pg_get_userbyid(c.relowner)<>'fiscuvia_migrator'))
              AND (SELECT count(*) FROM pg_trigger t WHERE NOT t.tgisinternal AND t.tgenabled='O'
                AND t.tgrelid IN (SELECT c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE (n.nspname='journal' AND c.relname IN ('events','outbox')) OR (n.nspname='journal_private' AND c.relname='denials')))=6
              AND (SELECT count(*) FROM pg_policy p WHERE NOT p.polpermissive AND p.polname='tenant_fence'
                AND p.polrelid IN ('journal.events'::regclass,'journal.outbox'::regclass)
                AND pg_get_expr(p.polqual,p.polrelid) LIKE '%current_tenant_id%'
                AND pg_get_expr(p.polwithcheck,p.polrelid) LIKE '%current_tenant_id%')=2
            """)) {
            if(!r.next()||!r.getBoolean(1))throw new StartupGuardFailure(StartupGuardFailure.Kind.DATABASE_PRIVILEGES);
        }
    };}
}
