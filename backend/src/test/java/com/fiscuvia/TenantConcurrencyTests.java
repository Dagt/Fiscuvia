package com.fiscuvia;

import java.util.*;
import java.util.concurrent.*;
import com.fiscuvia.platform.TenantTransactions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test") @SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=4")
@Testcontainers @DirtiesContext
class TenantConcurrencyTests {
    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @Autowired TenantTransactions tenants;
    @Autowired JdbcTemplate sql;
    TenantRlsFixtures fixture;
    static boolean installed;
    @BeforeEach void seed() throws Exception {
        fixture = new TenantRlsFixtures(DATABASE);
        if (!installed) { fixture.install(); installed = true; }
        fixture.seed();
    }

    @Test @Timeout(30)
    void concurrentTenantsUseDistinctConnectionsWithoutContextLeakOrCleanupBlocking() throws Exception {
        var pids = ConcurrentHashMap.<Integer>newKeySet();
        var barrier = new CyclicBarrier(4);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) {
                UUID tenant = i % 2 == 0 ? fixture.a : fixture.b;
                UUID record = tenant.equals(fixture.a) ? fixture.recordA : fixture.recordB;
                futures.add(workers.submit(() -> tenants.execute(tenant, () -> {
                    pids.add(sql.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception error) { throw new AssertionError(error); }
                    for (int query = 0; query < 20; query++) {
                        assertThat(sql.queryForList("SELECT id FROM rls_fixture.records", UUID.class)).containsExactly(record);
                        assertThat(sql.queryForObject("SELECT app.current_tenant_id()", UUID.class)).isEqualTo(tenant);
                    }
                    return null;
                })));
            }
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThat(pids).hasSize(4);
        assertThat(sql.queryForList("SELECT id FROM rls_fixture.records", UUID.class)).isEmpty();
    }
    @Test @Timeout(30)
    void reusedPoolSurvivesConcurrentCommitAndExceptionRollback() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 32; i++) {
                int number = i; UUID tenant = i % 2 == 0 ? fixture.a : fixture.b;
                UUID record = tenant.equals(fixture.a) ? fixture.recordA : fixture.recordB;
                futures.add(workers.submit(() -> {
                    try {
                        tenants.execute(tenant, () -> {
                            assertThat(sql.queryForList("SELECT id FROM rls_fixture.records", UUID.class)).containsExactly(record);
                            if (number % 3 == 0) throw new SyntheticRollback(); return null;
                        });
                    } catch (SyntheticRollback expected) { assertThat(number % 3).isZero(); }
                    assertThat(sql.queryForObject("SELECT app.current_tenant_id()", UUID.class)).isNull();
                    assertThat(sql.queryForList("SELECT id FROM rls_fixture.records", UUID.class)).isEmpty();
                }));
            }
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
    }
    static class SyntheticRollback extends RuntimeException {}
}
