package com.fiscuvia.audit;

import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit factory; never supplies a runtime datasource or provisions permissions. */
public final class OutboxProtocols {
    private OutboxProtocols(){}
    public static OutboxProtocol postgres(DataSource source,PlatformTransactionManager manager){
        return new com.fiscuvia.audit.internal.PostgresOutboxProtocol(source,manager);
    }
}
