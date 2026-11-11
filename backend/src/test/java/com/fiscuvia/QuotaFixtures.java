package com.fiscuvia;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic limits for pre-existing security scenarios; never installs policy or a quota trigger. */
final class QuotaFixtures {
    static void seed(JdbcTemplate sql, UUID tenant) {
        sql.update("INSERT INTO entitlement_private.limits VALUES(?,'USERS',1000000,1,clock_timestamp(),NULL) ON CONFLICT DO NOTHING",tenant);
    }
}
