package com.fiscuvia.identity.internal;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentitySettings.class)
class SessionStorageConfiguration {
    @Bean @SpringSessionDataSource
    HikariDataSource sessionDataSource(DataSourceProperties properties, IdentitySettings settings) {
        var source = new HikariDataSource();
        source.setJdbcUrl(properties.getUrl());
        source.setUsername("fiscuvia_sessions"); source.setPassword(settings.sessionPassword());
        source.setPoolName("fiscuvia-sessions"); source.setMaximumPoolSize(3); source.setMinimumIdle(0);
        source.setConnectionTimeout(3000); source.setValidationTimeout(1000);
        return source;
    }
    @Bean @SpringSessionTransactionManager
    JdbcTransactionManager sessionTransactionManager(@Qualifier("sessionDataSource") DataSource source) {
        return new JdbcTransactionManager(source);
    }
    @Bean @DependsOnDatabaseInitialization
    InitializingBean verifyIdentityRuntime(DataSource source) {
        return ()->{
            try(var connection=source.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("""
                    SELECT current_user='fiscuvia_runtime'
                    AND NOT has_database_privilege(current_database(),'CREATE')
                    AND NOT has_schema_privilege('identity','CREATE') AND NOT has_schema_privilege('identity_private','USAGE')
                    AND NOT has_schema_privilege('identity_crypto','USAGE') AND NOT has_schema_privilege('auth_sessions','USAGE')
                    AND NOT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='identity_crypto' AND p.prosecdef)
                    AND NOT has_table_privilege('identity.memberships','INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                    AND NOT has_table_privilege('identity.membership_roles','INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                    AND NOT has_table_privilege('identity.invitations','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                    AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                         WHERE n.nspname='identity' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity)=3
                    AND (SELECT count(*) FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                         WHERE n.nspname='identity' AND c.relname IN ('memberships','membership_roles') AND NOT p.polpermissive
                         AND (SELECT oid FROM pg_roles WHERE rolname=current_user)=ANY(p.polroles))=2
                    """)) {
                if(!rows.next() || !rows.getBoolean(1)) throw new com.fiscuvia.platform.StartupGuardFailure(com.fiscuvia.platform.StartupGuardFailure.Kind.DATABASE_PRIVILEGES);
            }
        };
    }
    @Bean @DependsOnDatabaseInitialization
    InitializingBean verifyPrivateSessionRole(@Qualifier("sessionDataSource") DataSource source) {
        return ()->{
            try(var connection=source.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("""
                    SELECT current_user='fiscuvia_sessions' AND NOT(rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication OR rolinherit)
                    AND NOT has_database_privilege(current_database(),'CREATE')
                    AND NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user))
                    AND NOT has_schema_privilege('identity_private','USAGE') AND NOT has_schema_privilege('identity','USAGE')
                    AND NOT has_schema_privilege('app','USAGE') AND NOT has_schema_privilege('infrastructure','USAGE')
                    AND NOT has_schema_privilege('auth_sessions','CREATE')
                    AND has_table_privilege('auth_sessions.spring_session','SELECT') AND has_table_privilege('auth_sessions.spring_session','INSERT')
                    AND has_table_privilege('auth_sessions.spring_session','UPDATE') AND has_table_privilege('auth_sessions.spring_session','DELETE')
                    AND has_table_privilege('auth_sessions.spring_session_attributes','SELECT') AND has_table_privilege('auth_sessions.spring_session_attributes','INSERT')
                    AND has_table_privilege('auth_sessions.spring_session_attributes','UPDATE') AND has_table_privilege('auth_sessions.spring_session_attributes','DELETE')
                    FROM pg_roles WHERE rolname=current_user
                    """)) {
                if(!rows.next() || !rows.getBoolean(1)) throw new com.fiscuvia.platform.StartupGuardFailure(com.fiscuvia.platform.StartupGuardFailure.Kind.DATABASE_PRIVILEGES);
            }
        };
    }
    @Bean @DependsOnDatabaseInitialization
    InitializingBean initializeActorProof(Flyway flyway, IdentitySettings settings) {
        return () -> {
            // Prepared values, no Flyway placeholders/checksums containing secrets. A different key fails closed.
            try (var connection=flyway.getConfiguration().getDataSource().getConnection()) {
                try (var insert=connection.prepareStatement("INSERT INTO identity_private.proof_key VALUES(true,?) ON CONFLICT DO NOTHING")) {
                    insert.setBytes(1,java.util.HexFormat.of().parseHex(settings.proofKey())); insert.executeUpdate();
                }
                try (var select=connection.prepareStatement("SELECT secret FROM identity_private.proof_key WHERE singleton"); var row=select.executeQuery()) {
                    if (!row.next() || !java.security.MessageDigest.isEqual(row.getBytes(1),java.util.HexFormat.of().parseHex(settings.proofKey())))
                        throw new com.fiscuvia.platform.StartupGuardFailure(com.fiscuvia.platform.StartupGuardFailure.Kind.CONFIGURATION_REJECTED);
                }
            }
        };
    }
}
