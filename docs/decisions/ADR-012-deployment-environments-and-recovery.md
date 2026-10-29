# ADR-012 — Deployment environments and recovery

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** NFR-023,NFR-041..043  
**Tracking:** E15 #70/#71

## Contexto y problema
Servicio SaaS necesita despliegues repetibles y separación de entornos.

## Requisitos / restricciones
NFR-023,NFR-041..043. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Servidor compartido sin aislamiento; despliegue manual no rastreable.

## Decisión propuesta
Front estático CDN, backend Docker, PostgreSQL managed, private buckets, staging/prod aislados; backup y restore ensayado.

## Razonamiento y ventajas
Escala gradual, costo administrable, recuperación medible.

## Costos / consecuencias negativas
SLO/RPO/RTO y proveedores pendientes; gastar sólo tras métricas.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento
E15 #70/#71. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
