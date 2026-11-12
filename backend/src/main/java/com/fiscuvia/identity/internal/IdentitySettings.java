package com.fiscuvia.identity.internal;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiscuvia.identity")
record IdentitySettings(boolean enabled, String issuer, String clientId, String clientSecret,
        String origin, String mfaPolicy, String proofKey, String sessionPassword, String contactPolicy) {
    IdentitySettings {
        if(contactPolicy==null)contactPolicy="disabled";
        if(!java.util.Set.of("disabled","keycloak-local-verified-email-v1").contains(contactPolicy)
            || (!enabled && !"disabled".equals(contactPolicy)))throw new IllegalStateException("Local contact policy unavailable");
        if (proofKey == null || !proofKey.matches("[0-9a-f]{64}") || sessionPassword == null || sessionPassword.isBlank())
            throw new IllegalStateException("Identity private configuration required");
        URI base = URI.create(origin);
        if (!"https".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) || base.getRawQuery()!=null
                || base.getRawUserInfo()!=null || base.getRawFragment()!=null || !base.getPath().isEmpty())
            throw new IllegalStateException("Only the explicit local HTTPS origin is enabled");
        if (enabled) {
            URI idp = URI.create(issuer);
            if (!"http".equals(idp.getScheme()) || !java.util.Set.of("127.0.0.1","localhost").contains(idp.getHost())
                    || !idp.getPath().equals("/realms/fiscuvia-local") || idp.getRawQuery()!=null || idp.getRawUserInfo()!=null
                    || idp.getRawFragment()!=null || clientSecret==null || clientSecret.isBlank())
                throw new IllegalStateException("Only the controlled local OIDC issuer is enabled");
        }
    }
    boolean localContactsEnabled(){return enabled && "keycloak-local-verified-email-v1".equals(contactPolicy);}
    @Override public String toString() { return "IdentitySettings[private]"; }
}
