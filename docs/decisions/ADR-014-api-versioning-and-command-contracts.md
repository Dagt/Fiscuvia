# ADR-014 — API versioning and command contracts

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-020,NFR-021  
**Tracking:** E01 #24

## Contexto y problema
Estados fiscales y pagos exigen semántica de comandos y consumidores tipados.

## Requisitos / restricciones
NFR-020,NFR-021. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
PATCH arbitrario de estados; serializar entidades JPA.

## Decisión propuesta
REST /api/v1, OpenAPI, tenant path validado por backend, expectedVersion, Idempotency-Key y Problem Details.

## Razonamiento y ventajas
Compatibilidad, control de concurrencia, pruebas contrato.

## Costos / consecuencias negativas
Diseño de schemas por slice y mantener frontend alineado.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01 #24. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
