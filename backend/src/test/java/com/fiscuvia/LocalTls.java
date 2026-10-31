package com.fiscuvia;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Ephemeral local certificate; trust its exact key, never disable JVM TLS verification. */
final class LocalTls {
    final Path store;
    final String password=UUID.randomUUID().toString();
    final int port;
    LocalTls() {
        try {
            try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { port=socket.getLocalPort(); }
            store=Files.createTempDirectory("fiscuvia-oidc-tls-").resolve("server.p12");
            store.toFile().deleteOnExit(); store.getParent().toFile().deleteOnExit();
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),"-genkeypair","-alias","local",
                    "-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=Fiscuvia local test","-ext","SAN=ip:127.0.0.1",
                    "-storetype","PKCS12","-keystore",store.toString(),"-storepass",password,"-noprompt").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS) || process.exitValue()!=0) throw new IllegalStateException("TLS fixture unavailable");
        } catch(Exception error) { throw new IllegalStateException("TLS fixture unavailable",error); }
    }
    String origin() { return "https://127.0.0.1:"+port; }
    SSLContext context() {
        try {
            var keys=KeyStore.getInstance("PKCS12"); try(var stream=Files.newInputStream(store)) { keys.load(stream,password.toCharArray()); }
            var trust=KeyStore.getInstance(KeyStore.getDefaultType()); trust.load(null); trust.setCertificateEntry("local",keys.getCertificate("local"));
            var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(trust);
            var context=SSLContext.getInstance("TLS"); context.init(null,managers.getTrustManagers(),null); return context;
        } catch(Exception error) { throw new IllegalStateException("TLS fixture unavailable",error); }
    }
    void register(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("server.port",()->port); registry.add("server.ssl.enabled",()->true);
        registry.add("server.ssl.key-store",()->store.toUri().toString()); registry.add("server.ssl.key-store-password",()->password);
        registry.add("server.ssl.key-store-type",()->"PKCS12"); registry.add("fiscuvia.identity.origin",this::origin);
    }
}
