package com.fiscuvia.entitlements.internal;

import com.fiscuvia.entitlements.Resource;
import com.fiscuvia.identity.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Query only. TENANT_PLAN_VIEW has no installed grants without express policy approval. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/entitlements")
class EntitlementController {
    record Usage(Resource resource,Long limit,Long version,Instant validFrom,Instant validUntil,
            Long measured,Long reserved,Long available,Boolean overLimit,String integration) {}
    private final AuthorizedTenantOperations operations;
    private final JdbcTemplate jdbc;
    EntitlementController(AuthorizedTenantOperations operations,DataSource source) {this.operations=operations;jdbc=new JdbcTemplate(source);}
    @GetMapping ResponseEntity<List<Usage>> get(@PathVariable UUID tenantId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(operations.execute(tenantId,Capability.TENANT_PLAN_VIEW,ResourceTarget.tenant(),access->{
            access.requireCurrent();
            return jdbc.query("SELECT * FROM entitlement_api.usage(?)",(r,n)->new Usage(Resource.valueOf(r.getString(1)),r.getObject(2,Long.class),r.getObject(3,Long.class),
                r.getTimestamp(4)==null?null:r.getTimestamp(4).toInstant(),r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),
                r.getObject(6,Long.class),r.getObject(7,Long.class),r.getObject(8,Long.class),r.getObject(9,Boolean.class),r.getString(10)),tenantId);
        }));
    }
}
