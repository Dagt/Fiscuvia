package com.fiscuvia.platform.internal.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import com.fiscuvia.platform.TenantTransactions;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class JdbcTenantTransactions implements TenantTransactions {
    private final DataSource source;
    private final TransactionTemplate transactions;
    JdbcTenantTransactions(DataSource source, PlatformTransactionManager manager) {
        this.source = source;
        this.transactions = new TransactionTemplate(manager); // REQUIRED; never expose REQUIRES_NEW/NESTED.
    }

    @Override public <T> T execute(UUID tenant, Supplier<T> work) {
        if (tenant == null || work == null) throw new TenantContextRejected();
        return transactions.execute(status -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new TenantContextRejected();
            Connection connection = DataSourceUtils.getConnection(source);
            try {
                if (connection.getAutoCommit() || !DataSourceUtils.isConnectionTransactional(connection, source))
                    throw new TenantContextRejected();
                try (var query = connection.prepareStatement("SELECT app.establish_tenant_context(?)")) {
                    query.setObject(1, tenant);
                    query.executeQuery().close();
                }
                T result = work.get();
                try (var query = connection.prepareStatement("SELECT app.current_tenant_id()")) {
                    try (var rows = query.executeQuery()) {
                        if (!rows.next() || !tenant.equals(rows.getObject(1, UUID.class))) throw new TenantContextRejected();
                    }
                }
                return result;
            } catch (SQLException rejected) { throw new TenantContextRejected(); }
            finally { DataSourceUtils.releaseConnection(connection, source); }
        });
    }
}
