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
E02 #29. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
