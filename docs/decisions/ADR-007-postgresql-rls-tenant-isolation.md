# ADR-007 — PostgreSQL RLS tenant isolation

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-001..003  
**Tracking:** E02 #26/#30

## Contexto y problema
Fuga cross-tenant de CFDI/expediente sería incidente crítico.

## Requisitos / restricciones
NFR-001..003. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Sólo filtros en UI/repositorio; database por cliente (más operaciones).

## Decisión propuesta
RLS por tenant + FK compuesta + autorización por recurso, SET LOCAL transaccional bajo rol no BYPASSRLS.

## Razonamiento y ventajas
Defensa en profundidad y buen coste SaaS.

## Costos / consecuencias negativas
Riesgo de filtrado por pools/workers: pruebas fail-closed antes de piloto.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E02 #26/#30. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
