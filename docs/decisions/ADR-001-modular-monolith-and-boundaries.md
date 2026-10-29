# ADR-001 — Modular monolith and boundaries

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-001; NFR-020  
**Tracking:** E01/E02

## Contexto y problema
Dividir un SaaS fiscal con múltiples dominios sin sobrecoste de microservicios.

## Requisitos / restricciones
FR-001; NFR-020. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Microservicios (ops complejas); monolito sin límites (alto acoplamiento).

## Decisión propuesta
Spring Modulith con módulos identity, tenants, taxpayers, obligations, workflow, tasks, documents, fees, reporting, jobs y CFDI/SAT futuros.

## Razonamiento y ventajas
Consistencia transaccional, un despliegue y evolución incremental.

## Costos / consecuencias negativas
Requiere tests de límites, eventos explícitos y disciplina de dependencias.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01/E02. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
