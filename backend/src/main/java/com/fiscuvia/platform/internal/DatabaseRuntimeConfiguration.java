package com.fiscuvia.platform.internal;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fail startup if the ordinary datasource cannot connect or has administrative privileges. */
@Configuration(proxyBeanMethods = false)
class DatabaseRuntimeConfiguration {

    @Bean
    @DependsOnDatabaseInitialization
    InitializingBean verifyRuntimeDatabase(DataSource dataSource) {
        return () -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("""
                            SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, rolreplication, rolinherit,
                                   current_user = pg_get_userbyid(datdba) AS owns_database
                            FROM pg_roles JOIN pg_database ON datname = current_database()
                            WHERE rolname = current_user
                            """)) {
                if (!result.next() || result.getBoolean("rolsuper") || result.getBoolean("rolcreatedb")
                        || result.getBoolean("rolcreaterole") || result.getBoolean("rolbypassrls")
                        || result.getBoolean("rolreplication") || result.getBoolean("rolinherit")
                        || result.getBoolean("owns_database")) {
                    throw new IllegalStateException("Runtime database role must not have administrative privileges.");
                }
                try (var roles = statement.executeQuery("""
                        SELECT EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname=current_user)
                            OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspname IN ('app','infrastructure')
                                AND nspowner=(SELECT oid FROM pg_roles WHERE rolname=current_user))
                            OR has_schema_privilege('app', 'CREATE')
                            OR has_schema_privilege('infrastructure', 'USAGE')
                            OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                                WHERE n.nspname='app' AND p.proowner=(SELECT oid FROM pg_roles WHERE rolname=current_user))
                        """)) {
                    roles.next();
                    if (roles.getBoolean(1)) throw new IllegalStateException("Runtime database privileges rejected.");
                }
                try (var tables = statement.executeQuery("""
                        SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                        WHERE n.nspname='app' AND c.relkind IN ('r','p') AND (
                            c.relowner=(SELECT oid FROM pg_roles WHERE rolname=current_user)
                            OR NOT c.relrowsecurity OR NOT c.relforcerowsecurity
                            OR NOT EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid
                                AND a.attname='tenant_id' AND a.attnotnull AND a.atttypid='uuid'::regtype AND NOT a.attisdropped)
                            OR has_table_privilege(c.oid, 'TRUNCATE') OR has_table_privilege(c.oid, 'TRIGGER')
                            OR has_table_privilege(c.oid, 'REFERENCES')
                            OR NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid AND NOT p.polpermissive
                                AND (SELECT oid FROM pg_roles WHERE rolname=current_user)=ANY(p.polroles)
                                AND p.polcmd='*' AND pg_get_expr(p.polqual,p.polrelid) LIKE '%current_tenant_id%'
                                AND pg_get_expr(p.polwithcheck,p.polrelid) LIKE '%current_tenant_id%'))
                        """)) {
                    tables.next();
                    if (tables.getInt(1) != 0) throw new IllegalStateException("Runtime tenant table protection rejected.");
                }
            }
        };
    }
}
