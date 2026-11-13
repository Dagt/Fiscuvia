package com.fiscuvia;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Disposable database fixtures only. Never a production grant/review API. */
final class TaxpayerFixtures {
    private TaxpayerFixtures() {}

    static void grant(JdbcTemplate sql, UUID tenant, UUID member, UUID resource, String capability, String kind) {
        if (kind.equals("TAXPAYER")) {
            sql.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?::text,734))",Object.class,tenant);
            if (sql.queryForObject("SELECT count(*) FROM taxpayers.taxpayers WHERE tenant_id=? AND id=?",Integer.class,tenant,resource)==0) {
                int ordinal=sql.queryForObject("SELECT count(*)+1 FROM taxpayers.taxpayers WHERE tenant_id=?",Integer.class,tenant);
                String rfc="ZZZ000101"+String.format(java.util.Locale.ROOT,"%03d",ordinal);
                UUID command=UUID.randomUUID();
                sql.update("INSERT INTO journal_private.command_context VALUES(pg_current_xact_id(),?,?) ON CONFLICT(transaction_id) DO UPDATE SET command_id=excluded.command_id,correlation_id=excluded.correlation_id",command,UUID.randomUUID());
                sql.queryForObject("SELECT journal_private.append_event(?,'TAXPAYER_CREATED','TAXPAYER_CREATE','TAXPAYER',?)",UUID.class,tenant,resource);
                sql.update("INSERT INTO taxpayers.taxpayers(tenant_id,id,rfc_normalized,person_type,legal_name,status,version,created_at,updated_at,created_by_membership_id,updated_by_membership_id) VALUES(?,?,?,'LEGAL_ENTITY','Synthetic fixture','ACTIVE',0,transaction_timestamp(),transaction_timestamp(),?,?)",tenant,resource,rfc,member,member);
                sql.update("INSERT INTO taxpayers.taxpayer_revisions SELECT tenant_id,id,version,?,updated_at,?,'CREATE',NULL,rfc_normalized,person_type,legal_name,trade_name,status,created_at,updated_at,created_by_membership_id,updated_by_membership_id,archived_at,archived_by_membership_id FROM taxpayers.taxpayers WHERE tenant_id=? AND id=?",command,member,tenant,resource);
                sql.update("INSERT INTO taxpayers.taxpayer_assignments VALUES(?,?,?,?,'PRIMARY',transaction_timestamp(),NULL,?,NULL)",tenant,UUID.randomUUID(),resource,member,command);
                sql.update("DELETE FROM journal_private.command_context WHERE transaction_id=pg_current_xact_id()");
            }
        }
        sql.update("INSERT INTO access_control.resource_grants(tenant_id,membership_id,resource_kind,resource_id,capability,active,approval_reference) VALUES(?,?,?,?,?,true,?)",tenant,member,kind,resource,capability,UUID.randomUUID());
        if (kind.equals("TAXPAYER")) {
            sql.update("INSERT INTO taxpayer_private.grant_reviews SELECT ?,?,'TAXPAYER',?,?,1,'ENABLE',m.version,r.role_code,?,transaction_timestamp(),v.command_id FROM identity.memberships m JOIN identity.membership_roles r ON r.tenant_id=m.tenant_id AND r.membership_id=m.id JOIN taxpayers.taxpayer_revisions v ON v.tenant_id=m.tenant_id AND v.taxpayer_id=? AND v.version=0 WHERE m.tenant_id=? AND m.id=?",tenant,member,capability,resource,member,resource,tenant,member);
        }
    }
}
