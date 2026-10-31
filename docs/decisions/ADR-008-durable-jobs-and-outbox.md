# ADR-008 — Durable jobs and outbox

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-022, NFR-024  
**Tracking:** E02 #29

## Contexto y problema
Generación mensual, alertas, reportes y SAT futuro son operaciones reintentables.

## Requisitos / restricciones
FR-022, NFR-024. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Colas en memoria; Kafka/Rabbit al inicio.

## Decisión propuesta
Outbox transaccional y worker PostgreSQL con locking, backoff, límites e idempotency.

## Razonamiento y ventajas
Consistencia de transacción y reintentos sin broker extra.

## Costos / consecuencias negativas
Jobs tenant-aware y deduplicación, recuperación crash; tests Testcontainers.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento

### Implementación acotada propuesta en #29 — 2026-10-31

La solicitud explícita del propietario autoriza infraestructura persistente y protocolo de claim/ack/retry probado, sin worker fiscal ni cuentas/servicios productivos. [Journal/atomicidad](../security/AUDIT-JOURNAL.md) y [outbox/fencing/runbook](../delivery/OUTBOX.md) documentan V5, payload AUDIT_RECORDED mínimo, at-least-once, cinco intentos y backoff, roles de transporte sin app/identity y scopes vacíos al instalar. No se serializan sesiones ni se concede contexto RLS de negocio a partir de un tenant_id en una fila.

Se proponen para revisión, sin habilitarlos: operación/custodia de cuentas de transporte, topología multi-tenant, cadencia/límites/retención/alertas, autoridad limitada de servicio o revalidación de usuario en cada comando y contratos de consumidores reales. Efectos externos requieren deduplicación/fencing downstream; no hay exactly-once ni actor owner ficticio. Este apéndice no marca Accepted el ADR ni resuelve #74/OQ de negocio. No se implementa #30.

E02 #29. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
