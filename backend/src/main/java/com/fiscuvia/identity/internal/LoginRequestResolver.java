package com.fiscuvia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Login initiation is POST+CSRF, followed by a single-use server-side redirect flag. */
final class LoginRequestResolver implements OAuth2AuthorizationRequestResolver {
    static final String LOGIN_APPROVED=LoginRequestResolver.class.getName()+".approved";
    private final OAuth2AuthorizationRequestResolver delegate;
    LoginRequestResolver(OAuth2AuthorizationRequestResolver delegate) { this.delegate=delegate; }
    private void check(HttpServletRequest request) {
        var session=request.getSession(false);
        if (session==null || !Boolean.TRUE.equals(session.getAttribute(LOGIN_APPROVED)))
            throw new OAuth2AuthenticationException("login_initiation_required");
        session.removeAttribute(LOGIN_APPROVED);
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!request.getRequestURI().equals("/api/v1/auth/authorize/local")) return null;
        check(request); return delegate.resolve(request);
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String id) { check(request); return delegate.resolve(request,id); }
}
