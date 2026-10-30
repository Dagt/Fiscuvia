# ADR-005 — OIDC identity and application authorization

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (pending explicit approval)  
**References:** FR-001..009, NFR-007  
**Tracking:** E02 issue #27

## Contexto y problema
Usuarios con múltiples roles, contador, supervisor, auxiliar y portal; no contraseñas en BD.

## Requisitos / restricciones
FR-001..009, NFR-007. Debe respetar el alcance de `docs/product/MVP.md`, el modelo multi-tenant y reglas legales/seguridad documentadas.

## Alternativas consideradas
Auth local propia; roles entregados por frontend/JWT sin consulta.

## Decisión propuesta
Autenticación mediante proveedor OIDC con MFA, identidad global + memberships/roles/capacidades en Fiscuvia.

## Razonamiento y ventajas
MFA, recuperación, soporte, roles y revocación separada.

## Costos / consecuencias negativas
Seleccionar proveedor, cookies vs bearer y pricing; OQ-014.

## Riesgos y validación
No aceptar producción sin pruebas de contratos, RLS, seguridad y flujos según `docs/delivery/DEFINITION-OF-DONE.md`. Confirmar compatibilidad técnica y preguntas abiertas relevantes antes de fijar comportamiento irreversible.

## Seguimiento

### Propuesta revisable de #27 — 2026-10-30

[Evaluación de proveedores, BFF/sesiones, modelo lógico y bootstrap RLS](issue-27-oidc-session-proposal.md). El proveedor de producción no se ha elegido. La propuesta solicita aprobación de la base BFF/JDBC, frontera de identidad global y política mínima de MFA/invitaciones antes de nuevas dependencias o migraciones. No hay autenticación implementada en esa fase y este ADR conserva **Proposed**; OQ-014 sigue Open.
E02 issue #27. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
