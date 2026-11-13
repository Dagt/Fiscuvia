package com.fiscuvia.taxpayers.internal;

import com.fiscuvia.platform.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/taxpayers")
class TaxpayerController {
    record RegimeInput(@NotNull @Pattern(regexp="[0-9]{3}") String code,
        @JsonProperty(required=true) LocalDate validFrom,@JsonProperty(required=true) LocalDate validTo) {}
    record Create(@NotNull @Size(min=12,max=32) String rfc,@NotNull @Pattern(regexp="INDIVIDUAL|LEGAL_ENTITY") String personType,
        @NotNull @Size(min=1,max=600) String legalName,@JsonProperty(required=true) String tradeName,
        @NotNull @Size(max=10) List<@NotNull @Valid RegimeInput> regimes) {}
    record Profile(@ExpectedVersion Long expectedVersion,@NotNull @Size(min=1,max=600) String legalName,
        @JsonProperty(required=true) String tradeName,@NotNull @Size(max=10) List<@NotNull @Valid RegimeInput> regimes) {}
    record Archive(@ExpectedVersion Long expectedVersion,@NotNull @Pattern(regexp="SERVICE_ENDED|DUPLICATE_REVIEW|OTHER") String reasonCode) {}
    record Restore(@ExpectedVersion Long expectedVersion,@NotNull @Pattern(regexp="SERVICE_RESUMED|CORRECTION") String reasonCode) {}
    private final TaxpayerService service;
    TaxpayerController(TaxpayerService service){this.service=service;}
    private static <T> ResponseEntity<T> safe(T body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
    @GetMapping ResponseEntity<PageResponse<TaxpayerService.Taxpayer>> list(@PathVariable UUID tenantId,
        @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(required=false) String sort,
        @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="ACTIVE") String status){
        return safe(service.list(tenantId,PageQuery.parse(page,size,sort,Set.of("id","legalName","createdAt")),search,status));
    }
    @GetMapping("/{taxpayerId}") ResponseEntity<TaxpayerService.Taxpayer> get(@PathVariable UUID tenantId,@PathVariable UUID taxpayerId){return safe(service.get(tenantId,taxpayerId));}
    @PostMapping ResponseEntity<TaxpayerService.Receipt> create(@PathVariable UUID tenantId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Create body){
        var result=service.command(tenantId,ApiIdentifiers.idempotencyKey(key),null,null,TaxpayerService.Action.CREATE,body.rfc(),body.personType(),body.legalName(),body.tradeName(),body.regimes(),null);
        return ResponseEntity.status(result.replayed()?200:201).header("Cache-Control","no-store")
            .location(URI.create("/api/v1/tenants/"+tenantId+"/taxpayers/"+result.targetId())).body(result);
    }
    @PatchMapping("/{taxpayerId}") ResponseEntity<TaxpayerService.Receipt> edit(@PathVariable UUID tenantId,@PathVariable UUID taxpayerId,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Profile body){
        return safe(service.command(tenantId,ApiIdentifiers.idempotencyKey(key),taxpayerId,body.expectedVersion(),TaxpayerService.Action.UPDATE_PROFILE,null,null,body.legalName(),body.tradeName(),body.regimes(),null));
    }
    @PostMapping("/{taxpayerId}/archivals") ResponseEntity<TaxpayerService.Receipt> archive(@PathVariable UUID tenantId,@PathVariable UUID taxpayerId,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Archive body){
        return safe(service.command(tenantId,ApiIdentifiers.idempotencyKey(key),taxpayerId,body.expectedVersion(),TaxpayerService.Action.ARCHIVE,null,null,null,null,null,body.reasonCode()));
    }
    @PostMapping("/{taxpayerId}/restorations") ResponseEntity<TaxpayerService.Receipt> restore(@PathVariable UUID tenantId,@PathVariable UUID taxpayerId,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Restore body){
        return safe(service.command(tenantId,ApiIdentifiers.idempotencyKey(key),taxpayerId,body.expectedVersion(),TaxpayerService.Action.RESTORE,null,null,null,null,null,body.reasonCode()));
    }
}
