# ADR-013 — CI and review/merge gates

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-040  
**Tracking:** E01 #23

## Contexto y problema
Historial TrailerSaaS muestra importancia de checks al SHA y PR sin merge autónomo.

## Requisitos / restricciones
NFR-040. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
CI parcial por rutas que omita seguridad; aceptar fallas sin registrarlas.

## Decisión propuesta
Actions backend/web/OpenAPI/DB/RLS/secret scan + pruebas locales; PR Draft y merge humano.

## Razonamiento y ventajas
Reproducibilidad y evidencia verificable.

## Costos / consecuencias negativas
CI puede fallar por billing; declarar remote UNVERIFIED y ejecutar local.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01 #23. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
