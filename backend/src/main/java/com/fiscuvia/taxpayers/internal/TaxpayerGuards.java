package com.fiscuvia.taxpayers.internal;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class TaxpayerGuards implements ApplicationRunner {
    private final JdbcTemplate sql;
    TaxpayerGuards(DataSource source){sql=new JdbcTemplate(source);}
    @Override public void run(ApplicationArguments ignored){
        if(!Boolean.TRUE.equals(sql.queryForObject("SELECT taxpayers.approved_policy()",Boolean.class)))
            throw new IllegalStateException("Taxpayer policy boundary rejected");
        if(!Boolean.TRUE.equals(sql.queryForObject("""
            SELECT count(*)=1 AND bool_and(p.prosecdef AND pg_get_userbyid(p.proowner)='fiscuvia_migrator'
            AND p.proconfig @> ARRAY['search_path=pg_catalog, pg_temp']
            AND has_function_privilege('fiscuvia_runtime',p.oid,'EXECUTE')
            AND NOT has_function_privilege('fiscuvia_sessions',p.oid,'EXECUTE'))
            FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
            WHERE n.nspname='taxpayers' AND p.proname='presentation'
            """,Boolean.class))) throw new IllegalStateException("Taxpayer presentation boundary rejected");
    }
}
