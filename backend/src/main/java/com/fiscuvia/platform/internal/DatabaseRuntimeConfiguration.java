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
                            SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls,
                                   current_user = pg_get_userbyid(datdba) AS owns_database
                            FROM pg_roles JOIN pg_database ON datname = current_database()
                            WHERE rolname = current_user
                            """)) {
                if (!result.next() || result.getBoolean("rolsuper") || result.getBoolean("rolcreatedb")
                        || result.getBoolean("rolcreaterole") || result.getBoolean("rolbypassrls")
                        || result.getBoolean("owns_database")) {
                    throw new IllegalStateException("Runtime database role must not have administrative privileges.");
                }
            }
        };
    }
}
