package com.fiscuvia.platform.internal;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Explicit primary pool: adding a private session pool must not replace the runtime pool. */
@Configuration(proxyBeanMethods = false)
class RuntimeDataSourceConfiguration {
    @Bean @Primary @ConfigurationProperties("spring.datasource")
    DataSourceProperties runtimeDataSourceProperties() { return new DataSourceProperties(); }
    @Bean @Primary @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
