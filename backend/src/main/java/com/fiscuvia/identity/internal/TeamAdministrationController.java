package com.fiscuvia.identity.internal;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/team")
class TeamAdministrationController {
    record Status(@NotNull @Pattern(regexp="ACTIVE|SUSPENDED|REVOKED") String status,
                  @NotNull @Min(0) @Max(9007199254740991L) Long expectedVersion) {}
    record Role(@NotNull @Pattern(regexp="FIRM_SUPERVISOR|ACCOUNTANT|ASSISTANT") String role,
                @NotNull @Min(0) @Max(9007199254740991L) Long expectedVersion) {}
    record Version(@NotNull @Min(0) @Max(9007199254740991L) Long expectedVersion) {}
    private final TeamAdministration team;
    TeamAdministrationController(TeamAdministration team){this.team=team;}
    private static <T> ResponseEntity<T> safe(T body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
    @GetMapping("/members") ResponseEntity<TeamAdministration.Members> members(@PathVariable UUID tenantId,
        @RequestParam(defaultValue="0") @Min(0) @Max(200) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size,
        @RequestParam(defaultValue="") @Size(max=80) String search,
        @RequestParam(defaultValue="ALL") @Pattern(regexp="ALL|ACTIVE|SUSPENDED|REVOKED") String status){
        return safe(team.members(tenantId,page,size,search,status));
    }
    @GetMapping("/invitations") ResponseEntity<TeamAdministration.Invitations> invitations(@PathVariable UUID tenantId,
        @RequestParam(defaultValue="0") @Min(0) @Max(200) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size,
        @RequestParam(defaultValue="") @Size(max=80) String search,
        @RequestParam(defaultValue="ALL") @Pattern(regexp="ALL|PENDING|ACCEPTED|REVOKED|EXPIRED") String status){
        return safe(team.invitations(tenantId,page,size,search,status));
    }
    @PostMapping("/members/{memberId}/status") ResponseEntity<TeamAdministration.Result> status(@PathVariable UUID tenantId,@PathVariable UUID memberId,
        @RequestHeader("Idempotency-Key") UUID command,@Valid @RequestBody Status body){
        var action=switch(body.status()){case "ACTIVE"->TeamAdministration.Action.REACTIVATE;case "SUSPENDED"->TeamAdministration.Action.SUSPEND;default->TeamAdministration.Action.REVOKE;};
        return safe(team.command(tenantId,command,memberId,body.expectedVersion(),action,""));
    }
    @PostMapping("/members/{memberId}/role") ResponseEntity<TeamAdministration.Result> role(@PathVariable UUID tenantId,@PathVariable UUID memberId,
        @RequestHeader("Idempotency-Key") UUID command,@Valid @RequestBody Role body){
        return safe(team.command(tenantId,command,memberId,body.expectedVersion(),TeamAdministration.Action.SET_ROLE,body.role()));
    }
    @PostMapping("/invitations/{invitationId}/revoke") ResponseEntity<TeamAdministration.Result> revoke(@PathVariable UUID tenantId,@PathVariable UUID invitationId,
        @RequestHeader("Idempotency-Key") UUID command,@Valid @RequestBody Version body){
        return safe(team.command(tenantId,command,invitationId,body.expectedVersion(),TeamAdministration.Action.REVOKE_INVITATION,""));
    }
}
