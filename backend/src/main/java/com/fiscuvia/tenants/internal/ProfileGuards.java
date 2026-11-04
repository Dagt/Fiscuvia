package com.fiscuvia.tenants.internal;

import javax.sql.DataSource;
import com.fiscuvia.platform.StartupGuardFailure;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
class ProfileGuards {
    @Bean @DependsOnDatabaseInitialization InitializingBean verifyProfilePrivileges(DataSource source){return ()->{
        try(var c=source.getConnection();var s=c.createStatement();var rows=s.executeQuery("""
            SELECT NOT has_schema_privilege('tenant_private','USAGE') AND NOT has_schema_privilege('tenant_api','CREATE')
              AND NOT has_schema_privilege('fiscuvia_sessions','tenant_api','USAGE')
              AND NOT has_schema_privilege('fiscuvia_sessions','tenant_private','USAGE')
              AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                   WHERE n.nspname='tenant_private' AND c.relkind='r')=3
              AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tenant_private' AND c.relkind='r' AND (
                  NOT c.relrowsecurity OR NOT c.relforcerowsecurity OR pg_get_userbyid(c.relowner)<>'fiscuvia_migrator'
                  OR has_table_privilege(c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                  OR has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')))
              AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='tenant_private' AND has_function_privilege(p.oid,'EXECUTE'))
              AND EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.tenants'::regclass AND tgname='initialize_profile' AND tgenabled='O')
              AND pg_get_functiondef('identity.own_firms()'::regprocedure) ~ 'm[.]user_id[[:space:]]*=[[:space:]]*identity[.]current_actor_id[(][)]'
            """)) {
            if(!rows.next()||!rows.getBoolean(1))throw new StartupGuardFailure(StartupGuardFailure.Kind.DATABASE_PRIVILEGES);
        }
    };}
}
