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
    }
}
