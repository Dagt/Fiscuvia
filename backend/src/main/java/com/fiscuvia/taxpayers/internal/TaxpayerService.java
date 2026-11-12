package com.fiscuvia.taxpayers.internal;

import com.fiscuvia.audit.AuditJournal;
import com.fiscuvia.identity.*;
import com.fiscuvia.platform.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class TaxpayerService {
    record Regime(String code,LocalDate validFrom,LocalDate validTo,String source,String reviewStatus) {}
    record Taxpayer(UUID id,String rfc,String personType,String legalName,String tradeName,String status,long version,
                    Instant createdAt,Instant updatedAt,List<Regime> regimes,String rfcValidation,String officialVerification) {}
    record Receipt(UUID targetId,long version,boolean replayed) {}
    enum Action { CREATE, UPDATE_PROFILE, ARCHIVE, RESTORE }
    private final AuthorizedTenantOperations operations;
    private final AuditJournal journal;
    private final JdbcTemplate sql;
    private final JsonMapper json;
    TaxpayerService(AuthorizedTenantOperations operations,AuditJournal journal,DataSource source,JsonMapper json){
        this.operations=operations;this.journal=journal;this.sql=new JdbcTemplate(source);this.json=json;
    }
    private static final String ROW="""
        SELECT jsonb_build_object('id',t.id,'rfc',t.rfc_normalized,'personType',t.person_type,'legalName',t.legal_name,
        'tradeName',t.trade_name,'status',t.status,'version',t.version,'createdAt',t.created_at,'updatedAt',t.updated_at,
        'rfcValidation','RFC_LOCAL_STRUCTURE_V1','officialVerification','NOT_PERFORMED','regimes',
        (SELECT coalesce(jsonb_agg(jsonb_build_object('code',r.regime_code,'validFrom',r.valid_from,'validTo',r.valid_to,
        'source',r.source,'reviewStatus',r.review_status) ORDER BY r.ordinal),'[]'::jsonb)
        FROM taxpayers.taxpayer_regime_references r WHERE r.tenant_id=t.tenant_id AND r.taxpayer_id=t.id AND r.taxpayer_version=t.version))::text
        FROM taxpayers.taxpayers t
        """;
    Taxpayer get(UUID tenant,UUID id){
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),access->{
            var values=sql.query(ROW+" WHERE t.tenant_id=? AND t.id=?",(r,n)->json.readValue(r.getString(1),Taxpayer.class),tenant,id);
            if(values.isEmpty())throw new ApiFailure(ApiErrorCode.NOT_FOUND);return values.getFirst();
        });
    }
    PageResponse<Taxpayer> list(UUID tenant,PageQuery page,String search,String status){
        if(search==null||search.codePointCount(0,search.length())>80||search.codePoints().anyMatch(Character::isISOControl)
            ||!(status.equals("ACTIVE")||status.equals("ARCHIVED")))throw TaxpayerValidation.invalid();
        // Values remain bound; sort fragments come exclusively from this closed server map.
        String column=switch(page.sort()){case "legalName"->"t.legal_name";case "createdAt"->"t.created_at";default->"t.id";};
        String order=page.direction()==PageQuery.Direction.ASC?"ASC":"DESC";
        String term="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),access->{
            var values=sql.query(ROW+" WHERE t.tenant_id=? AND t.status=? AND (t.legal_name ILIKE ? ESCAPE E'\\\\' OR t.rfc_normalized ILIKE ? ESCAPE E'\\\\') ORDER BY "+column+" "+order+",t.id ASC LIMIT ? OFFSET ?",
                (r,n)->json.readValue(r.getString(1),Taxpayer.class),tenant,status,term,term,page.size()+1,page.offset());
            boolean next=values.size()>page.size();return new PageResponse<>(List.copyOf(values.subList(0,Math.min(values.size(),page.size()))),page.page(),page.size(),next);
        });
    }
    Receipt command(UUID tenant,UUID cmd,UUID id,Long expected,Action action,String rfc,String person,String name,String trade,
                    List<TaxpayerController.RegimeInput> regimes,String reason){
        String normalizedRfc=action==Action.CREATE?TaxpayerValidation.rfc(rfc,person):null;
        String normalizedName=action==Action.CREATE||action==Action.UPDATE_PROFILE?TaxpayerValidation.name(name,300,false):null;
        String normalizedTrade=action==Action.CREATE||action==Action.UPDATE_PROFILE?TaxpayerValidation.name(trade,200,true):null;
        Capability cap=switch(action){case CREATE->Capability.TAXPAYER_CREATE;case UPDATE_PROFILE->Capability.TAXPAYER_EDIT;default->Capability.TAXPAYER_ARCHIVE;};
        ResourceTarget target=action==Action.CREATE?ResourceTarget.tenant():ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id);
        return operations.execute(tenant,cap,target,access->{
            access.requireCurrent();journal.bindCommand(cmd);
            try {
                String result=sql.queryForObject("SELECT taxpayers.command(?,?,?,?,?,?,?,?,?,?::jsonb,?)::text",String.class,
                    tenant,cmd,id,expected,action.name(),normalizedRfc,person,normalizedName,normalizedTrade,regimes==null?null:json.writeValueAsString(regimes),reason);
                return json.readValue(result,Receipt.class);
            } catch(DataAccessException failure){
                Throwable cause=failure;while(!(cause instanceof java.sql.SQLException)&&cause.getCause()!=null)cause=cause.getCause();
                String state=cause instanceof java.sql.SQLException error?error.getSQLState():"";
                throw new ApiFailure(switch(state){case "P3401"->ApiErrorCode.VALIDATION_FAILED;case "P3402"->ApiErrorCode.NOT_FOUND;
                    case "P3403"->ApiErrorCode.STALE_VERSION;case "P3404"->ApiErrorCode.QUOTA_UNAVAILABLE;
                    case "P3405","23505"->ApiErrorCode.CONFLICT;case "P3406"->ApiErrorCode.IDEMPOTENCY_KEY_REUSED;
                    case "42501"->ApiErrorCode.ACCESS_DENIED;default->ApiErrorCode.INTERNAL_ERROR;});
            }
        });
    }
}
