package com.fiscuvia.platform.internal.http;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = CorrelationFilter.class.getName() + ".id";

    static String id(HttpServletRequest request) {
        if (!(request.getAttribute(ATTRIBUTE) instanceof String)) {
            request.setAttribute(ATTRIBUTE, UUID.randomUUID().toString());
        }
        return (String) request.getAttribute(ATTRIBUTE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Generate at the trusted boundary; never reflect an arbitrary caller header into responses/logs.
        response.setHeader("X-Correlation-Id", id(request));
        chain.doFilter(request, response);
    }
}
