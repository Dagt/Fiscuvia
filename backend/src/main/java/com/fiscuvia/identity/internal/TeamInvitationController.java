package com.fiscuvia.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Only the explicitly approved invitation slice, not a global/member directory or role authority. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/team")
class TeamInvitationController {
    record Options(boolean emailInvitationsAvailable,String delivery) {}
    record Request(@NotBlank @Email @Size(max=254) String email,
        @NotNull @Pattern(regexp="FIRM_SUPERVISOR|ACCOUNTANT|ASSISTANT") String role) {}
    private final IdentityStore store;
    TeamInvitationController(IdentityStore store){this.store=store;}
    @GetMapping("/invitation-options") ResponseEntity<Options> options(@PathVariable UUID tenantId){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new Options(store.emailInvitationOptions(tenantId),"MANUAL"));
    }
    @PostMapping("/invitations") ResponseEntity<IdentityStore.EmailInvitation> invite(@PathVariable UUID tenantId,
        @RequestHeader("Idempotency-Key") UUID command,@Valid @RequestBody Request request){
        var result=store.inviteEmail(tenantId,command,request.email(),request.role());
        return ResponseEntity.status(result.replayed()?200:201).header("Cache-Control","no-store").body(result);
    }
}
