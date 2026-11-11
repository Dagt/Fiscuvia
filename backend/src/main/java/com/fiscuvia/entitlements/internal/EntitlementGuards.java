package com.fiscuvia.entitlements.internal;

import javax.sql.DataSource;
import com.fiscuvia.platform.StartupGuardFailure;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
class EntitlementGuards {
    @Bean @DependsOnDatabaseInitialization InitializingBean verifyEntitlementPrivileges(DataSource source) { return ()->{
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("""
            SELECT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='identity.memberships'::regclass
              AND tgname='entitlement_capacity' AND tgenabled='O' AND tgfoid IN (SELECT p.oid FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='entitlement_private' AND p.proname='membership_capacity' AND p.pronargs=0) AND tgtype=23)
            AND entitlement_api.approved_view_policy()
            AND NOT has_schema_privilege('entitlement_private','USAGE')
            AND NOT has_schema_privilege('entitlement_api','CREATE')
            AND NOT has_schema_privilege('fiscuvia_sessions','entitlement_api','USAGE')
            AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='entitlement_private' AND c.relkind='r')=3
            AND NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='entitlement_private' AND c.relkind='r'
              AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity OR pg_get_userbyid(c.relowner)<>'fiscuvia_migrator'
              OR has_table_privilege(c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
              OR has_table_privilege('fiscuvia_sessions',c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')))
            AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
              WHERE n.nspname='entitlement_private' AND has_function_privilege(p.oid,'EXECUTE'))
            """)) {if(!r.next()||!r.getBoolean(1))throw new StartupGuardFailure(StartupGuardFailure.Kind.DATABASE_PRIVILEGES);}
    };}
}
