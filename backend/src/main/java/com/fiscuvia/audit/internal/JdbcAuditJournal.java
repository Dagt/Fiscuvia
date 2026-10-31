package com.fiscuvia.audit.internal;

import com.fiscuvia.audit.AuditJournal;
import com.fiscuvia.platform.*;
import java.util.UUID;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class JdbcAuditJournal implements AuditJournal {
    private final JdbcTemplate sql;
    private final byte[] key;
    JdbcAuditJournal(DataSource source,@org.springframework.beans.factory.annotation.Value("${fiscuvia.identity.proof-key}") String key) {
        sql=new JdbcTemplate(source);this.key=HexFormat.of().parseHex(key);
    }
    private String transaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        return sql.queryForObject("SELECT pg_current_xact_id()::text",String.class);
    }
    private String proof(String value) {
        try { var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException impossible){throw new ApiFailure(ApiErrorCode.INTERNAL_ERROR);}
    }
    public void bindCommand(UUID command) {
        if(command==null)throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        String tx=transaction();UUID correlation=RequestCorrelation.currentOrCreate();
        sql.queryForObject("SELECT journal.bind_command(?,?,?)",Object.class,command,correlation,
            proof("journal\n"+tx+"\n"+correlation+"\n"+command));
    }
    public Receipt execute(UUID command,Runnable work) {
        java.util.Objects.requireNonNull(work);bindCommand(command);
        UUID previous=sql.queryForObject("SELECT journal.begin_command(?)",UUID.class,command);
        if(previous!=null)return new Receipt(previous,EventCode.AUTHORIZED_OPERATION_RECORDED,1,true);
        work.run();
        return new Receipt(sql.queryForObject("SELECT journal.finish_command(?)",UUID.class,command),EventCode.AUTHORIZED_OPERATION_RECORDED,1,false);
    }
    public void recordDenial(Denial denied) {
        String tx=transaction();UUID actor=sql.queryForObject("SELECT identity.current_actor_id()",UUID.class);
        if(actor==null)throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        String payload="denial\n"+tx+"\n"+actor+"\n"+denied.commandId()+"\n"+denied.correlationId()+"\n"+denied.operation()+"\n"+denied.reason();
        sql.queryForObject("SELECT journal.record_denial(?,?,?,?,?)",Object.class,denied.commandId(),denied.correlationId(),
            denied.operation().name(),denied.reason().name(),proof(payload));
    }
}
