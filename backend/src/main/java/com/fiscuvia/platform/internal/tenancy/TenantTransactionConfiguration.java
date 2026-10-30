package com.fiscuvia.platform.internal.tenancy;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration(proxyBeanMethods = false)
class TenantTransactionConfiguration {
    @Bean JdbcTransactionManager transactionManager(DataSource source) {
        var manager = new JdbcTransactionManager(source);
        // Rolling back a savepoint can erase a first binding. Require full transaction boundaries.
        manager.setNestedTransactionAllowed(false);
        manager.setValidateExistingTransaction(true);
        return manager;
    }
}
