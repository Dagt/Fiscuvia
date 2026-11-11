package com.fiscuvia;

import com.fiscuvia.audit.*;
import com.fiscuvia.identity.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.session.*;
import static org.assertj.core.api.Assertions.*;

/** Shared synthetic persona catalog; identities are created only by real OIDC login. */
final class ThreatHarness {
    record Actor(String username,UUID user,OidcBrowser browser) {
        @Override public String toString(){return "Actor[synthetic/private]";}
    }
    static final class Actors implements AutoCloseable {
        final Map<String,Actor> values=new LinkedHashMap<>();
        final LocalTls tls;final KeycloakTestProvider idp;
        Actors(LocalTls tls,KeycloakTestProvider idp){this.tls=tls;this.idp=idp;}
        void ensure() throws Exception {
            int i=0;
            for(String name:List.of("a","peer","b","multi","supervisor","assistant","owner","suspended","revoked","none")){
                var prior=values.get(name);
                if(prior==null || prior.browser.get("/api/v1/me").statusCode()!=200){
                    if(prior!=null)prior.browser.close();
                    var browser=new OidcBrowser(tls,idp);var me=browser.login(List.of("alice","bob","carol").get(i%3));
                    values.put(name,new Actor(browser.username,UUID.fromString(me.path("userId").asText()),browser));
                }
                i++;
            }
        }
        Actor get(String name){return Objects.requireNonNull(values.get(name));}
        public void close(){values.values().forEach(a->a.browser.close());}
    }
    final PostgresTestDatabase database;final Actors actors;
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    final UUID a1=UUID.randomUUID(),a2=UUID.randomUUID(),b1=UUID.randomUUID(),c1=UUID.randomUUID();
    final Map<String,UUID> members=new HashMap<>();
    final String demoRfc="DEMO-"+UUID.randomUUID();
    ThreatHarness(PostgresTestDatabase database,Actors actors) throws Exception {
        this.database=database;this.actors=actors;
        for(UUID tenant:List.of(a,b,c))admin(tenant,s->s.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",tenant));
        for(UUID tenant:List.of(a,c))admin(tenant,s->s.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,tenant,actor("owner").user,UUID.randomUUID()));
        for(String name:List.of("a","peer","multi","supervisor","assistant","suspended","revoked"))
            members.put(name,member(a,actor(name).user,switch(name){case "supervisor"->"FIRM_SUPERVISOR";case "assistant"->"ASSISTANT";default->"ACCOUNTANT";}));
        members.put("b",member(b,actor("b").user,"ACCOUNTANT"));members.put("multi-b",member(b,actor("multi").user,"ACCOUNTANT"));
        admin(a,s->s.update("INSERT INTO threat_fixture.resources(tenant_id,id,synthetic_rfc) VALUES(?,?,?),(?,?,?)",a,a1,demoRfc,a,a2,"DEMO-"+UUID.randomUUID()));
        admin(a,s->s.update("UPDATE identity.memberships SET status='SUSPENDED' WHERE id=?",members.get("suspended")));
        admin(a,s->s.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",members.get("revoked")));
        admin(b,s->s.update("INSERT INTO threat_fixture.resources(tenant_id,id,synthetic_rfc) VALUES(?,?,?)",b,b1,demoRfc));
        admin(c,s->s.update("INSERT INTO threat_fixture.resources(tenant_id,id,synthetic_rfc) VALUES(?,?,?)",c,c1,"DEMO-"+UUID.randomUUID()));
        for(String cap:List.of("TAXPAYER_VIEW","TAXPAYER_EDIT")){grant(a,members.get("a"),a1,cap);grant(a,members.get("multi"),a1,cap);grant(b,members.get("multi-b"),b1,cap);grant(b,members.get("b"),b1,cap);}
        grant(a,members.get("peer"),a2,"TAXPAYER_VIEW");
        grant(a,members.get("suspended"),a1,"TAXPAYER_VIEW");grant(a,members.get("revoked"),a1,"TAXPAYER_VIEW");
    }
    Actor actor(String name){return actors.get(name);}
    Connection migration() throws SQLException{return DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword);}
    void admin(UUID tenant,Consumer<JdbcTemplate> work) throws Exception {
        try(var connection=migration()){
            connection.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            if(tenant!=null)sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            work.accept(sql);connection.commit();
        }
    }
    List<Map<String,Object>> rows(UUID tenant,String query,Object... parameters) throws Exception {
        var result=new ArrayList<Map<String,Object>>();admin(tenant,s->result.addAll(s.queryForList(query,parameters)));return result;
    }
    UUID member(UUID tenant,UUID user,String role) throws Exception {
        UUID id=UUID.randomUUID();admin(tenant,s->{QuotaFixtures.seed(s,tenant);s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,id,user);
            s.update("INSERT INTO identity.membership_roles VALUES(?,?,?)",tenant,id,role);});return id;
    }
    void grant(UUID tenant,UUID member,UUID resource,String capability) throws Exception {
        admin(tenant,s->s.update("INSERT INTO access_control.resource_grants VALUES(?,?,'TAXPAYER',?,?,true,?)",tenant,member,resource,capability,UUID.randomUUID()));
    }
    int version(UUID tenant,UUID resource) throws Exception {return ((Number)rows(tenant,"SELECT version FROM threat_fixture.resources WHERE id=?",resource).getFirst().get("version")).intValue();}
    int successes(UUID tenant) throws Exception {return rows(tenant,"SELECT event_id FROM journal.events WHERE event_code='AUTHORIZED_OPERATION_RECORDED'").size();}
    void bind(JdbcTemplate sql,Actor actor) {
        // Internal test adapter only: exact identity has already authenticated with real Keycloak.
        String tx=sql.queryForObject("SELECT pg_current_xact_id()::text",String.class);
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.HexFormat.of().parseHex(database.actorKey),"HmacSHA256"));
            String payload=actors.idp.issuer()+"\n"+KeycloakTestProvider.subject(actor.username)+"\n"+tx+"\n0";
            String proof=java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            sql.queryForList("SELECT * FROM identity.bind_actor(?,?,0,?)",actors.idp.issuer(),KeycloakTestProvider.subject(actor.username).toString(),proof);
        }catch(java.security.GeneralSecurityException impossible){throw new IllegalStateException("Synthetic actor binding unavailable");}
    }
    void authorize(JdbcTemplate sql,Actor actor,UUID tenant,String cap) {
        bind(sql,actor);assertThat(sql.queryForObject("SELECT access_control.authorize_operation(?,?,'TAXPAYER',NULL)",String.class,tenant,cap)).isEqualTo("AUTHORIZED");
        sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
    }
    static void until(BooleanSupplier condition) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<deadline){if(condition.getAsBoolean())return;java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));}
        throw new AssertionError("Expected observable database condition was not reached");
    }
    boolean blocked(String role) {
        try {return (Boolean)rows(null,"SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.usename=? AND NOT l.granted) AS waiting",role).getFirst().get("waiting");}
        catch(Exception unavailable){throw new IllegalStateException("Lock probe unavailable");}
    }
    String function(String signature) throws Exception {return (String)rows(null,"SELECT pg_get_functiondef(?::regprocedure) AS definition",signature).getFirst().get("definition");}
    void definition(String definition) throws Exception {admin(null,s->s.execute(definition));}

    static <S extends Session> Runnable principalField(SessionRepository<S> repository,Actor actor,String field,Object value) {
        String id=new String(Base64.getDecoder().decode(actor.browser.sessionCookie().replace("\"","")),java.nio.charset.StandardCharsets.UTF_8);
        S session=repository.findById(id);assertThat(session).isNotNull();
        org.springframework.security.core.context.SecurityContext original=session.getAttribute("SPRING_SECURITY_CONTEXT");
        Object p=original.getAuthentication().getPrincipal();
        try {
            var components=p.getClass().getRecordComponents();Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
            for(int i=0;i<components.length;i++){types[i]=components[i].getType();var getter=components[i].getAccessor();getter.setAccessible(true);values[i]=components[i].getName().equals(field)?value:getter.invoke(p);}
            var constructor=p.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);
            var changed=(org.springframework.security.oauth2.core.user.OAuth2User)constructor.newInstance(values);
            var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            security.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(changed,changed.getAuthorities(),"local"));
            session.setAttribute("SPRING_SECURITY_CONTEXT",security);repository.save(session);
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Synthetic session fixture unavailable");}
        return ()->{S current=repository.findById(id);if(current!=null){current.setAttribute("SPRING_SECURITY_CONTEXT",original);repository.save(current);}};
    }

    final class Transport implements AutoCloseable {
        final com.zaxxer.hikari.HikariDataSource source=new com.zaxxer.hikari.HikariDataSource();
        final JdbcTemplate sql;final OutboxProtocol protocol;final String role="delivery_"+UUID.randomUUID().toString().replace("-","");
        final long roleOid;
        Transport(UUID tenant) throws Exception {
            String password=UUID.randomUUID().toString();
            try(var connection=DriverManager.getConnection(database.getJdbcUrl(),database.getUsername(),database.getPassword());var s=connection.createStatement()){
                s.execute("CREATE ROLE "+role+" LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '"+password+"'");
                s.execute("GRANT CONNECT ON DATABASE fiscuvia_local TO "+role);
            }
            roleOid=((Number)rows(null,"SELECT oid FROM pg_roles WHERE rolname=?",role).getFirst().get("oid")).longValue();
            admin(null,s->{s.update("INSERT INTO journal_private.delivery_scopes VALUES(?::oid,?,?,true)",roleOid,tenant,UUID.randomUUID());
                s.execute("GRANT USAGE ON SCHEMA journal,threat_fixture TO "+role);
                s.execute("GRANT EXECUTE ON FUNCTION journal.claim(integer,integer),journal.lock_delivery(uuid,uuid),journal.read_delivery(uuid,uuid),journal.complete_delivery(uuid,uuid,text) TO "+role);
                s.execute("GRANT SELECT,INSERT ON threat_fixture.receipts TO "+role);});
            source.setJdbcUrl(database.getJdbcUrl());source.setUsername(role);source.setPassword(password);source.setMaximumPoolSize(3);source.setMinimumIdle(0);
            source.setConnectionTimeout(3000);sql=new JdbcTemplate(source);protocol=OutboxProtocols.postgres(source,new org.springframework.jdbc.support.JdbcTransactionManager(source));
        }
        int receipt(UUID id){return sql.update("INSERT INTO threat_fixture.receipts VALUES(?) ON CONFLICT DO NOTHING",id);}
        public void close(){source.close();}
    }
}
