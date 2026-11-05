package com.fiscuvia.tenants.internal;

import com.fiscuvia.platform.*;
import com.fiscuvia.identity.*;
import com.fiscuvia.audit.AuditJournal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** No platform operator endpoint: provisioning/lifecycle retain their private administrative boundary. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/settings")
class TenantController {
    record Profile(String commercialName,String contactEmail,String phone,String timeZone,String locale,
            String status,long version,Instant createdAt,Instant updatedAt,boolean canManage,String logoReference) {}
    record Update(@ExpectedVersion Long expectedVersion,
            @NotBlank @Size(max=160) @Pattern(regexp="[^\\p{Cntrl}]+") String commercialName,
            @Size(max=254) @Email @Pattern(regexp="[^\\p{Cntrl}]*") String contactEmail,
            @Pattern(regexp="[+0-9 ()-]{7,30}") String phone,
            @NotNull @Pattern(regexp="America/(Mexico_City|Cancun|Chihuahua|Ciudad_Juarez|Hermosillo|Mazatlan|Monterrey|Tijuana)") String timeZone,
            @NotNull @Pattern(regexp="es-MX") String locale) {}
    record Updated(long version) {}
    private final AuthorizedTenantOperations operations;
    private final AuditJournal journal;
    private final JdbcTemplate jdbc;
    TenantController(AuthorizedTenantOperations operations,AuditJournal journal,DataSource source) {
        this.operations=operations;this.journal=journal;this.jdbc=new JdbcTemplate(source);
    }
    @GetMapping ResponseEntity<Profile> get(@PathVariable UUID tenantId) {
        return noStore(operations.execute(tenantId,Capability.TENANT_READ,ResourceTarget.tenant(),access->{
            access.requireCurrent();
            return jdbc.queryForObject("SELECT * FROM tenant_api.profile(?)",(r,n)->new Profile(r.getString(1),r.getString(2),r.getString(3),
                    r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant(),r.getBoolean(10),null),tenantId);
        }));
    }
    @PostMapping ResponseEntity<Updated> update(@PathVariable UUID tenantId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Update request) {
        UUID command=ApiIdentifiers.idempotencyKey(key);
        return noStore(operations.execute(tenantId,Capability.TENANT_SETTINGS_MANAGE,ResourceTarget.tenant(),access->{
            access.requireCurrent();journal.bindCommand(command);
            try {
                return new Updated(jdbc.queryForObject("SELECT tenant_api.update_profile(?,?,?,?,?,?,?,?)",Long.class,tenantId,command,
                        request.expectedVersion(),request.commercialName().strip(),blankToNull(request.contactEmail()),blankToNull(request.phone()),request.timeZone(),request.locale()));
            } catch(DataAccessException failure) {
                Throwable cause=failure;while(!(cause instanceof java.sql.SQLException)&&cause.getCause()!=null)cause=cause.getCause();
                String state=cause instanceof java.sql.SQLException sql?sql.getSQLState():"";
                throw new ApiFailure(switch(state) {
                    case "40001"->ApiErrorCode.STALE_VERSION;
                    case "23505"->ApiErrorCode.IDEMPOTENCY_KEY_REUSED;
                    case "22023","23514","22001","23502"->ApiErrorCode.VALIDATION_FAILED;
                    case "42501"->ApiErrorCode.ACCESS_DENIED;
                    default->ApiErrorCode.INTERNAL_ERROR;
                });
            }
        }));
    }
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.strip();}
    private static <T> ResponseEntity<T> noStore(T value){return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
}
