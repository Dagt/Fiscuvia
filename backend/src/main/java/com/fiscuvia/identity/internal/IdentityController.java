package com.fiscuvia.identity.internal;

import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.ApiFailure;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class IdentityController {
    record Csrf(String headerName,String token) {}
    record Status(boolean loginAvailable) {}
    record TenantRequest(@NotNull UUID tenantId) {}
    record InviteRequest(@NotNull UUID tenantId,@NotBlank @Size(max=255) String subject,
            @NotNull @Pattern(regexp="FIRM_SUPERVISOR|ACCOUNTANT|ASSISTANT") String role) {}
    record AcceptRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    record MembershipRequest(@NotNull UUID tenantId,@NotNull UUID membershipId,
            @NotNull @Pattern(regexp="SUSPENDED|REVOKED") String status) {}
    record RevokeRequest(@NotNull UUID tenantId,@NotNull UUID invitationId) {}
    private final IdentityStore store;
    private final IdentitySettings settings;
    IdentityController(IdentityStore store,IdentitySettings settings) { this.store=store; this.settings=settings; }
    @GetMapping("/auth/csrf") ResponseEntity<Csrf> csrf(CsrfToken csrf) {
        return noStore(new Csrf(csrf.getHeaderName(),csrf.getToken()));
    }
    @GetMapping("/auth/status") ResponseEntity<Status> status() { return noStore(new Status(settings.enabled())); }
    @PostMapping("/auth/login") ResponseEntity<Void> login(HttpServletRequest request) {
        if (!settings.enabled()) throw new ApiFailure(ApiErrorCode.ACCESS_DENIED);
        request.getSession().setAttribute(LoginRequestResolver.LOGIN_APPROVED,Boolean.TRUE);
        return ResponseEntity.status(303).header("Cache-Control","no-store").location(URI.create("/api/v1/auth/authorize/local")).build();
    }
    @GetMapping("/me") ResponseEntity<IdentityStore.Me> me() { return noStore(store.me()); }
    @PostMapping("/auth/tenant") ResponseEntity<IdentityStore.Membership> tenant(@Valid @RequestBody TenantRequest request) {
        return noStore(store.select(request.tenantId()));
    }
    @PostMapping("/auth/invitations") ResponseEntity<IdentityStore.Invitation> invite(@Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(201).header("Cache-Control","no-store").body(store.invite(request.tenantId(),request.subject(),request.role()));
    }
    @PostMapping("/auth/invitations/accept") ResponseEntity<Map<String,UUID>> accept(@Valid @RequestBody AcceptRequest request) {
        return noStore(Map.of("tenantId",store.accept(request.token())));
    }
    @PostMapping("/auth/invitations/revoke") ResponseEntity<Void> revoke(@Valid @RequestBody RevokeRequest request) {
        store.revokeInvite(request.tenantId(),request.invitationId()); return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @PostMapping("/auth/memberships/status") ResponseEntity<Void> status(@Valid @RequestBody MembershipRequest request) {
        store.changeMember(request.tenantId(),request.membershipId(),request.status()); return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    private static <T> ResponseEntity<T> noStore(T value) { return ResponseEntity.ok().header("Cache-Control","no-store").body(value); }
}
