# Document state machine (Proposed)

`PENDING_UPLOAD` -> `UPLOADED` -> `VALIDATING` -> `AVAILABLE`, o `REJECTED`/`QUARANTINED`. Eliminación lógica `ARCHIVED` con política de retención aprobada; blob original inmutable/hash.

`InitializeUpload` crea metadata temporal y URL firmada con cuota; `FinalizeUpload` verifica size/MIME/hash + propiedad de objeto; `ScanAndValidate` determina AVAILABLE o REJECTED; `LinkEvidence` requiere AVAILABLE y mismo tenant. `Download` verifica rol/recurso y emite URL breve.

No servir PENDING como evidencia. No compartir enlaces entre tenants, ni revelar bucket internamente. Retry finalize idempotente; cleanup sólo orphan no referenciado. Tests MIME spoof, upload incompleto, accidental cross-tenant, retry y link expirado.
