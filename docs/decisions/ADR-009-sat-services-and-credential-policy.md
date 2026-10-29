# ADR-009 — SAT services and credential policy

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-084..085, BR-073  
**Tracking:** E13 #65–67

## Contexto y problema
Acceso a e.firma es sensible; servicios externos y condiciones SAT cambian.

## Requisitos / restricciones
FR-084..085, BR-073. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Guardar .key/password localStorage; scraping de portal sin autorización.

## Decisión propuesta
MVP sin custodia llaves; sólo metadata. Futuro ADR de arquitectura de firma local vs KMS/HSM tras validación técnica/legal.

## Razonamiento y ventajas
Evita riesgo irreversible temprano, facilita MVP útil.

## Costos / consecuencias negativas
Integración directa SAT queda bloqueada hasta aprobación OQ-009.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E13 #65–67. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
