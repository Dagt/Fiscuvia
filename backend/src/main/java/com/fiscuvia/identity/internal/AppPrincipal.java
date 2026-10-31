package com.fiscuvia.identity.internal;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Deliberately NOT OidcUser. No token, claims map, email, name or provider credentials in JDBC sessions. */
record AppPrincipal(UUID userId, String issuer, String subject, long authVersion,
        long authenticatedAt, long absoluteExpiry, long mfaAt) implements OAuth2User, Serializable {
    private static final long serialVersionUID = 1L;
    @Override public String getName() { return userId.toString(); }
    @Override public Map<String,Object> getAttributes() { return Map.of(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("AUTHENTICATED")); }
    @Override public String toString() { return "AppPrincipal[private]"; }
}
