package com.fiscuvia;

import com.fiscuvia.audit.AuditJournal;
import com.fiscuvia.identity.*;
import com.fiscuvia.platform.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/** Real HTTP adapter exclusively in test-classes, behind the production session/CSRF filters. */
@RestController
class ThreatFixtureController {
    // An actual handler behind the default-deny rule: 404 alone would not detect permitAll drift.
    @GetMapping("/api/v1/__threat-denied-route") String deniedProbe(){return "SYNTHETIC_HANDLER_MUST_NOT_EXECUTE";}
    static final String ROOT="/api/v1/auth/invitations/__threat-test/";
    static volatile CountDownLatch entered,release;
    final AuthorizedTenantOperations operations;final AuditJournal journal;final JdbcTemplate sql;final PlatformTransactionManager manager;
    ThreatFixtureController(AuthorizedTenantOperations operations,AuditJournal journal,DataSource source,PlatformTransactionManager manager){
        this.operations=operations;this.journal=journal;sql=new JdbcTemplate(source);this.manager=manager;
    }
    @GetMapping(ROOT+"{tenant}/list") Map<String,Object> list(@PathVariable UUID tenant,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="false") boolean hold) {
        var query=PageQuery.parse(page,size,"id,asc",Set.of("id"));
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->{
            if(hold)barrier();
            return Map.of("items",sql.queryForList("SELECT id FROM threat_fixture.resources ORDER BY id LIMIT ? OFFSET ?",UUID.class,query.size(),query.offset()),
                "count",sql.queryForObject("SELECT count(*) FROM threat_fixture.resources",Integer.class));
        });
    }
    @GetMapping(ROOT+"{tenant}/item/{id}") Map<String,Object> item(@PathVariable UUID tenant,@PathVariable UUID id){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),scope->{
            var rows=sql.queryForList("SELECT id,version FROM threat_fixture.resources WHERE id=?",id);
            if(rows.isEmpty())throw new ApiFailure(ApiErrorCode.NOT_FOUND);return rows.getFirst();
        });
    }
    @GetMapping(ROOT+"{tenant}/export") List<UUID> export(@PathVariable UUID tenant){
        return operations.execute(tenant,Capability.TAXPAYER_EXPORT,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->sql.queryForList("SELECT id FROM threat_fixture.resources ORDER BY id",UUID.class));
    }
    record Batch(List<UUID> ids) {}
    @PostMapping(ROOT+"{tenant}/batch/{command}") AuditJournal.Receipt batch(@PathVariable UUID tenant,@PathVariable UUID command,
            @RequestBody Batch request,@RequestParam(defaultValue="normal") String mode){
        return operations.execute(tenant,Capability.TAXPAYER_EDIT,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->{
            var receipt=journal.execute(command,()->{
                if(mode.equals("hold"))barrier();
                for(UUID id:request.ids())if(sql.update("UPDATE threat_fixture.resources SET version=version+1 WHERE id=?",id)!=1)
                    throw new ApiFailure(ApiErrorCode.NOT_FOUND);
                if(mode.equals("rollback"))throw new ApiFailure(ApiErrorCode.CONFLICT);
            });
            if(mode.equals("after-journal"))throw new ApiFailure(ApiErrorCode.CONFLICT);
            return receipt;
        });
    }
    @GetMapping(ROOT+"{tenant}/requires-new") Map<String,Object> requiresNew(@PathVariable UUID tenant){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->{
            int outer=sql.queryForObject("SELECT pg_backend_pid()",Integer.class);
            var isolated=new TransactionTemplate(manager);isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var result=isolated.execute(status->{
                boolean denied=false;try{scope.requireCurrent();}catch(ApiFailure expected){denied=expected.code()==ApiErrorCode.ACCESS_DENIED;}
                return Map.of("denied",denied,"rows",sql.queryForObject("SELECT count(*) FROM threat_fixture.resources",Integer.class),
                    "innerPid",sql.queryForObject("SELECT pg_backend_pid()",Integer.class));
            });
            scope.requireCurrent();return Map.of("inner",result,"outerPid",outer,"restoredPid",sql.queryForObject("SELECT pg_backend_pid()",Integer.class));
        });
    }
    @GetMapping(ROOT+"{tenant}/retained") String retained(@PathVariable UUID tenant){
        AuthorizedAccess old=operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->scope);
        old.requireCurrent();return "unreachable";
    }
    @GetMapping(ROOT+"{tenant}/switch/{other}") String switchTenant(@PathVariable UUID tenant,@PathVariable UUID other){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->
            operations.execute(other,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->"unreachable"));
    }
    @GetMapping(ROOT+"{tenant}/switch-capability") String switchCapability(@PathVariable UUID tenant){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->
            operations.execute(tenant,Capability.TAXPAYER_EDIT,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->"unreachable"));
    }
    @GetMapping(ROOT+"{tenant}/threads") Map<String,Object> threads(@PathVariable UUID tenant){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),scope->{
            try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
                return workers.submit(()->{
                    boolean denied=false;try{scope.requireCurrent();}catch(ApiFailure expected){denied=true;}
                    return Map.<String,Object>of("denied",denied,"rows",sql.queryForObject("SELECT count(*) FROM threat_fixture.resources",Integer.class));
                }).get(10,TimeUnit.SECONDS);
            }catch(Exception failure){throw new ApiFailure(ApiErrorCode.INTERNAL_ERROR);}
        });
    }
    static void barrier(){entered.countDown();try{if(!release.await(12,TimeUnit.SECONDS))throw new ApiFailure(ApiErrorCode.CONFLICT);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new ApiFailure(ApiErrorCode.CONFLICT);}}
}
