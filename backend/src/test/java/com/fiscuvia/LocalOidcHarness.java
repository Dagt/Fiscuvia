package com.fiscuvia;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Test-classpath-only browser fixture. Local resources owned by Ryuk and this shutdown hook. */
public final class LocalOidcHarness {
    private LocalOidcHarness() {}
    public static void main(String[] arguments) throws Exception {
        Path output=Path.of(arguments[0]); Files.deleteIfExists(output);
        var tls=new LocalTls(); var database=new PostgresTestDatabase();
        var idp=new KeycloakTestProvider("https://127.0.0.1:4174");
        database.start(); idp.start();
        DynamicPropertyRegistry registry=(name,value)->System.setProperty(name,value.get().toString());
        database.register(registry); tls.register(registry);
        System.setProperty("spring.profiles.active","test");
        System.setProperty("fiscuvia.identity.enabled","true"); System.setProperty("fiscuvia.identity.issuer",idp.issuer());
        System.setProperty("fiscuvia.identity.client-secret",idp.clientSecret); System.setProperty("fiscuvia.identity.origin","https://127.0.0.1:4174");
        System.setProperty("fiscuvia.identity.mfa-policy","keycloak-local-otp-v1");
        var application=SpringApplication.run(FiscuviaApplication.class);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            application.close(); idp.stop(); database.stop(); try { Files.deleteIfExists(output); } catch(Exception ignored) { }
        }));
        var keys=java.security.KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(tls.store)) { keys.load(input,tls.password.toCharArray()); }
        String certificate="-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(keys.getCertificate("local").getEncoded())+"\n-----END CERTIFICATE-----\n";
        // Private ignored handoff to the Node process; never upload as an artifact.
        var settings=Map.of("backendOrigin",tls.origin(),"pfx",tls.store.toString(),"passphrase",tls.password,
                "certificate",certificate,"password",idp.password,"otpSecret",idp.otpSecret,"issuer",idp.issuer());
        Files.writeString(output,new tools.jackson.databind.json.JsonMapper().writeValueAsString(settings),StandardOpenOption.CREATE_NEW);
        // Explicit fixture approvals, not an application registration/bootstrap endpoint. Only test identities may be approved here.
        var approvals=new LinkedHashMap<String,List<UUID>>();
        approvals.put("alice-1",List.of(UUID.randomUUID(),UUID.randomUUID())); approvals.put("bob-1",List.of(UUID.randomUUID()));
        approvals.put("alice-2",List.of(UUID.randomUUID(),UUID.randomUUID()));
        approvals.put("bob-3",List.of(UUID.randomUUID()));
        for(int i=3;i<=8;i++)approvals.put("alice-"+i,List.of(UUID.randomUUID(),UUID.randomUUID()));
        approvals.put("bob-2",List.of(UUID.randomUUID()));
        // Settings permission comes exclusively from the real approved V7 migration.
        // Fixtures provision synthetic firms/identities, never role-capability grants.
        while(!Thread.currentThread().isInterrupted()) {
            if(System.in.available()>0 && new String(System.in.readNBytes(System.in.available()),java.nio.charset.StandardCharsets.UTF_8).contains("stop")) {
                System.exit(0); // Shutdown hook closes the context and both owned containers before the JVM exits.
            }
            for(var iterator=approvals.entrySet().iterator();iterator.hasNext();) {
                var approval=iterator.next();
                try(var connection=DriverManager.getConnection(database.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,database.migrationPassword)) {
                    var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                    var users=sql.queryForList("SELECT user_id FROM identity_private.external_identities WHERE issuer=? AND subject=?",UUID.class,idp.issuer(),KeycloakTestProvider.subject(approval.getKey()).toString());
                    if(users.size()!=1) continue;
                    int position=0;
                    for(UUID command:approval.getValue()) {
                        connection.setAutoCommit(false);
                        String name=List.of("alice-1","alice-8").contains(approval.getKey()) ? (position==0?"Despacho Norte DEMO":"Despacho Sur DEMO")
                            : approval.getKey().startsWith("alice-") ? (position==0?"Despacho Horizonte DEMO":"Despacho de Servicios Profesionales y Asesoria Integral para Equipos Contables de Prueba DEMO")
                            : approval.getKey().equals("bob-3") ? "Despacho suspendido DEMO" : "Despacho Centro DEMO";
                        UUID tenant=sql.queryForObject("SELECT tenant_private.provision(?,?,?,?,'contacto@example.invalid',NULL,'America/Mexico_City','es-MX')",UUID.class,
                            command,users.getFirst(),command,name); connection.commit();position++;
                        if(approval.getKey().equals("bob-3")) {
                            sql.queryForObject("SELECT tenant_private.change_status(?,?,?,0,'SUSPENDED')",Long.class,tenant,UUID.randomUUID(),UUID.randomUUID());connection.commit();
                        }
                    }
                    iterator.remove();
                }
            }
            Thread.sleep(200);
        }
    }
}
