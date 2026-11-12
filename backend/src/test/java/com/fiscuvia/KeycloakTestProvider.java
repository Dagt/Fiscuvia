package com.fiscuvia;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.time.Duration;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/** Actual local IdP. No passwords, OTP credentials, users or signing keys are bundled in the application JAR. */
final class KeycloakTestProvider extends GenericContainer<KeycloakTestProvider> {
    static final String IMAGE="quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b";
    final String clientSecret=UUID.randomUUID().toString();
    final String password=UUID.randomUUID().toString();
    final String otpSecret=java.util.HexFormat.of().formatHex(java.security.SecureRandom.getSeed(20));
    final java.security.interfaces.RSAPrivateKey signingKey;
    private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger> users=new java.util.concurrent.ConcurrentHashMap<>();
    static final UUID ALICE=UUID.fromString("2ac9bd3d-f034-45d1-b1b1-11afdc2e37ec");
    static final UUID BOB=UUID.fromString("81b6d0ca-5bb6-44bf-a948-e9b9272c917a");
    static final UUID CAROL=UUID.fromString("4b54d8ee-2452-46e2-9078-7e6a457ec908");
    KeycloakTestProvider(String origin) {
        super(DockerImageName.parse(IMAGE));
        withExposedPorts(8080);
        withCreateContainerCmdModifier(command->command.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"),new ExposedPort(8080))));
        withCommand("start-dev","--import-realm","--http-host=0.0.0.0","--log-level=error");
        withEnv("FISCUVIA_LOCAL_CLIENT_SECRET",clientSecret); withEnv("FISCUVIA_ORIGIN",origin);
        try {
            var json=JsonMapper.builder().build();
            var realm=(tools.jackson.databind.node.ObjectNode)json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("../infra/keycloak/realm-template.json")));
            var material=new LocalTls();
            var keys=java.security.KeyStore.getInstance("PKCS12");
            try(var input=java.nio.file.Files.newInputStream(material.store)) { keys.load(input,material.password.toCharArray()); }
            signingKey=(java.security.interfaces.RSAPrivateKey)keys.getKey("local",material.password.toCharArray());
            var component=realm.putObject("components").putArray("org.keycloak.keys.KeyProvider").addObject();
            component.put("name","ephemeral-test-signing").put("providerId","rsa");
            var config=component.putObject("config");
            config.putArray("priority").add("1000"); config.putArray("active").add("true"); config.putArray("enabled").add("true"); config.putArray("algorithm").add("RS256");
            config.putArray("privateKey").add(java.util.Base64.getEncoder().encodeToString(signingKey.getEncoded()));
            config.putArray("certificate").add(java.util.Base64.getEncoder().encodeToString(keys.getCertificate("local").getEncoded()));
            var users=realm.putArray("users");
            for (String name:java.util.List.of("alice","bob","carol")) for(int n=1;n<=32;n++) addUser(users,name+"-"+n,subject(name+"-"+n));
            withCopyToContainer(Transferable.of(json.writeValueAsBytes(realm),0444),"/opt/keycloak/data/import/fiscuvia-local.json");
        } catch (Exception invalid) { throw new IllegalStateException("Local realm unavailable",invalid); }
        waitingFor(Wait.forHttp("/realms/fiscuvia-local/.well-known/openid-configuration").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)));
    }
    private void addUser(ArrayNode users,String name,UUID subject) {
        var user=users.addObject(); user.put("id",subject.toString()); user.put("username",name); user.put("enabled",true); user.put("emailVerified",!name.equals("carol-1"));
        user.put("firstName","Demostración"); user.put("lastName","Local"); user.put("email",name+"@example.invalid");
        var credentials=user.putArray("credentials");
        credentials.addObject().put("type","password").put("value",password).put("temporary",false);
        // Keycloak stores the secret as base32 encoded text in secretData.
        credentials.addObject().put("type","otp").put("secretData","{\"value\":\""+base32(java.util.HexFormat.of().parseHex(otpSecret))+"\"}")
                .put("credentialData","{\"subType\":\"totp\",\"digits\":6,\"counter\":0,\"period\":30,\"algorithm\":\"HmacSHA1\",\"secretEncoding\":\"BASE32\"}");
    }
    String nextUser(String base) { return base+"-"+users.computeIfAbsent(base,key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet(); }
    static UUID subject(String username) { return UUID.nameUUIDFromBytes(("fiscuvia-test:"+username).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    static String base32(byte[] data) {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; StringBuilder out=new StringBuilder(); int buffer=0,bits=0;
        for (byte value:data) { buffer=(buffer<<8)|(value&255); bits+=8; while(bits>=5) { bits-=5; out.append(alphabet.charAt((buffer>>bits)&31)); } }
        if(bits>0) out.append(alphabet.charAt((buffer<<(5-bits))&31)); return out.toString();
    }
    String issuer() { return "http://localhost:"+getMappedPort(8080)+"/realms/fiscuvia-local"; }
    String otp() { return otp(0); }
    String otp(int stepOffset) {
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA1"); mac.init(new javax.crypto.spec.SecretKeySpec(java.util.HexFormat.of().parseHex(otpSecret),"HmacSHA1"));
            byte[] hash=mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(java.time.Instant.now().getEpochSecond()/30+stepOffset).array());
            int offset=hash[19]&15; int value=java.nio.ByteBuffer.wrap(hash,offset,4).getInt()&0x7fffffff;
            return String.format(java.util.Locale.ROOT,"%06d",value%1000000);
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
