# ADR-004 — React Web and design system

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-030..036  
**Tracking:** E01 issue #21; E09 issue #52

## Contexto y problema
Producto B2B que requiere excelente UI y responsividad desde el inicio.

## Requisitos / restricciones
NFR-030..036. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Plantilla genérica sin tokens; backend templates; mobile nativo en MVP.

## Decisión propuesta
React 19/TS/Vite, shadcn/Radix, Tailwind, TanStack, RHF+Zod; sistema visual Fiscuvia obligatorio.

## Razonamiento y ventajas
Separación frontend/backend y componentes maduros; dirección estética fijada.

## Costos / consecuencias negativas
Debe validarse pantalla real, accesibilidad, cambios CSS y bundle.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E01 issue #21; E09 issue #52. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
