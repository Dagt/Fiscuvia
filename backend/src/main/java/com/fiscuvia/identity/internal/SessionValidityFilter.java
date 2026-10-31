package com.fiscuvia.identity.internal;

import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.ApiFailure;
import com.fiscuvia.platform.HttpProblemResponder;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionValidityFilter extends OncePerRequestFilter {
    private final IdentityStore store;
    private final HttpProblemResponder problems;
    SessionValidityFilter(IdentityStore store,HttpProblemResponder problems) { this.store=store; this.problems=problems; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getPrincipal() instanceof AppPrincipal principal) {
            try { store.validateSession(principal); }
            catch (ApiFailure invalid) {
                if (invalid.code()==ApiErrorCode.INTERNAL_ERROR) { problems.write(ApiErrorCode.INTERNAL_ERROR,request,response); return; }
                if (request.getSession(false)!=null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                problems.write(ApiErrorCode.AUTHENTICATION_REQUIRED,request,response); return;
            }
        }
        chain.doFilter(request,response);
    }
}
