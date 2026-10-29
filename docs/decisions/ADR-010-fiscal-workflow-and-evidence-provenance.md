# ADR-010 — Fiscal workflow and evidence provenance

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-020..031, BR-024..030  
**Tracking:** E05/E06

## Contexto y problema
Presentada, pagada, revisada y validada oficialmente no son equivalentes.

## Requisitos / restricciones
FR-020..031, BR-024..030. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Un status único o casilla 'cumplida'; confiar automáticamente en XML.

## Decisión propuesta
Estados separados work/filing/payment; comandos explícitos y eventos append-only; evidencia con origen y fecha.

## Razonamiento y ventajas
Trazabilidad y menos falsos positivos.

## Costos / consecuencias negativas
Necesita revisión contable OQ-001–005, complica modelo UI/API.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E05/E06. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
