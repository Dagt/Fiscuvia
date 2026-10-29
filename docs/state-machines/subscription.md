# SaaS subscription lifecycle (Future/Proposed)

`TRIAL` -> `ACTIVE` -> `PAST_DUE` -> `GRACE_PERIOD` -> `SUSPENDED_READ_ONLY` -> `ACTIVE` tras pago, o `CANCELLED` tras política de terminación. El orden exacto se decidirá con proveedor cobro/legal; esta máquina es **conceptual**.

No eliminar tenant/datos por fallo de webhooks. Verificar firmas webhook, idempotencia, eventos fuera de orden, retención, exportación, cuotas, estado de acceso en read-only. Factura del SaaS ≠ CFDI generado por contribuyente.
