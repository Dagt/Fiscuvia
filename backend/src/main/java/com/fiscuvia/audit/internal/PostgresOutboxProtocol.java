package com.fiscuvia.audit.internal;

import com.fiscuvia.audit.OutboxProtocol;
import com.fiscuvia.platform.*;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** No component registration or default connection/authority. Exposed only through the factory. */
public final class PostgresOutboxProtocol implements OutboxProtocol {
    private final JdbcTemplate sql; private final TransactionTemplate transactions;
    public PostgresOutboxProtocol(DataSource source,PlatformTransactionManager manager){sql=new JdbcTemplate(source);transactions=new TransactionTemplate(manager);}
    public List<Claim> claim(int limit,int milliseconds){return transactions.execute(s->sql.query("SELECT * FROM journal.claim(?,?)",
        (r,n)->new Claim(r.getObject("event_id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("lease_token",UUID.class),
            r.getTimestamp("leased_until").toInstant(),r.getInt("attempts")),limit,milliseconds));}
    public <T> T process(Claim claim,Supplier<T> effect){
        return transactions.execute(s->{
            if(!Boolean.TRUE.equals(sql.queryForObject("SELECT journal.lock_delivery(?,?)",Boolean.class,claim.eventId(),claim.leaseToken())))
                throw new ApiFailure(ApiErrorCode.CONFLICT);
            // Decode current persisted data, not a payload/version asserted in a caller's Claim.
            var versions=sql.queryForList("SELECT schema_version FROM journal.read_delivery(?,?)",Integer.class,claim.eventId(),claim.leaseToken());
            if(versions.size()!=1)throw new ApiFailure(ApiErrorCode.CONFLICT);
            if(versions.getFirst()!=1){
                if(!Boolean.TRUE.equals(sql.queryForObject("SELECT journal.complete_delivery(?,?,?)",Boolean.class,claim.eventId(),claim.leaseToken(),"UNSUPPORTED_VERSION")))
                    throw new ApiFailure(ApiErrorCode.CONFLICT);
                return null;
            }
            T result=effect.get();
            if(!Boolean.TRUE.equals(sql.queryForObject("SELECT journal.complete_delivery(?,?,NULL)",Boolean.class,claim.eventId(),claim.leaseToken())))
                throw new ApiFailure(ApiErrorCode.CONFLICT);
            return result;
        });
    }
    public boolean retry(Claim claim,Failure reason){return Boolean.TRUE.equals(transactions.execute(s->sql.queryForObject(
        "SELECT journal.complete_delivery(?,?,?)",Boolean.class,claim.eventId(),claim.leaseToken(),reason.name())));}
}
