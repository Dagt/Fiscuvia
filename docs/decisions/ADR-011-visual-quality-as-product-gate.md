# ADR-011 — Visual quality as product gate

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-030..036  
**Tracking:** E09 #52/#56

## Contexto y problema
Evitar frontends funcionales pero genéricos; diferenciación de marca requiere consistencia.

## Requisitos / restricciones
NFR-030..036. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Aplicar CSS al final; screenshot IA como prueba; dashboard stock.

## Decisión propuesta
Design tokens, kit de componentes y capturas reales por PR con revisión 1440/390, estados y accesibilidad.

## Razonamiento y ventajas
Control visual temprano y menor deuda de UI.

## Costos / consecuencias negativas
Más tiempo en UI, baseline de referencia debe incorporarse.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E09 #52/#56. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
