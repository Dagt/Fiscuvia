package com.fiscuvia.platform.internal.observability;

import java.util.Set;

/** Closed inventory of approved templates. Never fall back to a request URI. */
public final class SafeRoutes {
    private static final Set<String> ROUTES = Set.of("/api/v1/public/info", "/actuator/health",
            "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/metrics",
            "/actuator/metrics/{requiredMetricName}", "/error", "/api/v1/me", "/api/v1/auth/status",
            "/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/tenant",
            "/api/v1/auth/authorize/local", "/api/v1/auth/callback/local", "/api/v1/auth/invitations",
            "/api/v1/auth/invitations/accept", "/api/v1/auth/invitations/revoke", "/api/v1/auth/memberships/status",
            "/api/v1/tenants/{tenantId}/taxpayers/presentation", "/api/v1/tenants/{tenantId}/taxpayers/{taxpayerId}/presentation",
            "/api/v1/tenants/{tenantId}/taxpayers", "/api/v1/tenants/{tenantId}/taxpayers/{taxpayerId}",
            "/api/v1/tenants/{tenantId}/taxpayers/{taxpayerId}/archivals", "/api/v1/tenants/{tenantId}/taxpayers/{taxpayerId}/restorations",
            "/api/v1/tenants/{tenantId}/settings", "/api/v1/tenants/{tenantId}/entitlements",
            "/api/v1/tenants/{tenantId}/team/invitation-options", "/api/v1/tenants/{tenantId}/team/invitations",
            "/api/v1/tenants/{tenantId}/team/members", "/api/v1/tenants/{tenantId}/team/members/{memberId}/status",
            "/api/v1/tenants/{tenantId}/team/members/{memberId}/role", "/api/v1/tenants/{tenantId}/team/invitations/{invitationId}/revoke");
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    private SafeRoutes() {}
    public static String route(String template) { return ROUTES.contains(template == null ? "" : template) ? template : "UNMAPPED"; }
    public static String route(String template, String path) {
        String approved = route(template);
        // Actuator uses catch-all templates. Only exact, closed inventory paths may refine them.
        return "UNMAPPED".equals(approved) ? route(path) : approved;
    }
    public static String method(String method) { return METHODS.contains(method == null ? "" : method) ? method : "OTHER"; }
    public static String outcome(int status) { return status >= 500 ? "SERVER_ERROR" : status >= 400 ? "CLIENT_ERROR" : "SUCCESS"; }
}
