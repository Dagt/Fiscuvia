package com.fiscuvia;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.session.*;
import tools.jackson.databind.json.JsonMapper;

/** Private filesystem controls for ephemeral browser tests. No HTTP endpoint or production class. */
final class TaxpayerBrowserFixtures {
    private TaxpayerBrowserFixtures() {}
    static void apply(Path control,ApplicationContext app,PostgresTestDatabase db,Set<UUID> allowed) throws Exception {
        if(!Files.exists(control))return;
        var json=new JsonMapper();var command=json.readTree(Files.readString(control));
        UUID tenant=UUID.fromString(command.path("tenant").asText());
        if(!allowed.contains(tenant))throw new IllegalArgumentException("Non-fixture tenant rejected");
        String kind=command.path("kind").asText();
        try(var c=DriverManager.getConnection(db.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,db.migrationPassword)) {
            c.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            if(kind.equals("grant")){
                UUID member=UUID.fromString(command.path("member").asText()),resource=UUID.fromString(command.path("taxpayer").asText());
                for(String cap:List.of("TAXPAYER_VIEW","TAXPAYER_EDIT"))TaxpayerFixtures.grant(sql,tenant,member,resource,cap,"TAXPAYER");
            }else if(kind.equals("quota")) {
                long version=sql.queryForObject("SELECT version FROM entitlement_private.limits WHERE tenant_id=? AND resource='TAXPAYERS'",Long.class,tenant);
                sql.queryForObject("SELECT entitlement_private.assign_limit(?,?,?,'TAXPAYERS',?,?,NULL)",Long.class,tenant,UUID.randomUUID(),UUID.randomUUID(),command.path("value").asLong(),version);
            }else if(kind.equals("expireMfa"))expireMfa(app,command.path("session").asText());
            else throw new IllegalArgumentException("Unknown fixture control");
            c.commit();
        }
        Files.delete(control);
        Files.writeString(control.resolveSibling("35-result.json"),json.writeValueAsString(Map.of("id",command.path("id").asText(),"ok",true)));
    }
    @SuppressWarnings("unchecked")
    private static void expireMfa(ApplicationContext app,String cookie) throws Exception {
        var repository=(SessionRepository<Session>)app.getBean(SessionRepository.class);
        String id=new String(Base64.getDecoder().decode(cookie),java.nio.charset.StandardCharsets.UTF_8);
        Session session=Objects.requireNonNull(repository.findById(id));
        org.springframework.security.core.context.SecurityContext original=session.getAttribute("SPRING_SECURITY_CONTEXT");
        Object p=original.getAuthentication().getPrincipal();var components=p.getClass().getRecordComponents();
        Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++){types[i]=components[i].getType();var getter=components[i].getAccessor();getter.setAccessible(true);values[i]=components[i].getName().equals("mfaAt")?1L:getter.invoke(p);}
        var constructor=p.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);
        var changed=(org.springframework.security.oauth2.core.user.OAuth2User)constructor.newInstance(values);
        var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(changed,changed.getAuthorities(),"local"));
        session.setAttribute("SPRING_SECURITY_CONTEXT",security);repository.save(session);
    }
}
