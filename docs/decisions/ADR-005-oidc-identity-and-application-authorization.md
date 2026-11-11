# ADR-005 — OIDC identity and application authorization

**Status:** Proposed  
**Date:** 2026-10-29  
**Decision owner:** Product owner (base técnica autorizada 2026-10-30; proveedor/operación productivos pendientes)

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

### Autorización parcial #33 — 2026-11-11

Sólo desarrollo/test Keycloak: localizar por correo una identidad ya OIDC-verificada, fijar issuer/subject exactos al invitar y validar esos valores al aceptar; persistencia privada mínima de nombre/correo sin claims/tokens en sesión. Token aleatorio, hash irreversible, uso único/expiración/revocación, entrega manual de una sola vez; no correo externo ni producción. [V10 y formulario implementados](../security/LOCAL-EMAIL-INVITATIONS-33.md). Esta decisión no aprueba toda la matriz de equipo, proveedor productivo u OQ-014; el ADR conserva Proposed.

### Autorización técnica parcial del propietario — 2026-10-30, revisión de PR #83

Se autorizaron explícitamente: BFF del mismo origen; Spring Security Code+PKCE/state/nonce; Spring Session JDBC PostgreSQL privado; cookies Secure/HttpOnly/SameSite, CSRF/rotación/expiración/logout; Keycloak real sólo local/test; identidad global separada de membresías; roles FIRM_OWNER_ADMIN/FIRM_SUPERVISOR/ACCOUNTANT/ASSISTANT; privilegios denegados sin MFA verificable; bootstrap administrativo controlado e invitaciones de un uso para identidad exacta verificada.

Esta aprobación permite dependencias, migración V3 e implementación de #27. Sustituye la petición de aprobación previa del apartado histórico siguiente. [Diseño físico y comportamiento implementados](../security/AUTHENTICATION.md). No autoriza proveedor de producción, contratación, despliegues, claims MFA genéricos, invitaciones productivas por email ni recuperación/revocación federada sin contrato verificado. Esas decisiones siguen Proposed/Open; **no se marca Accepted el ADR completo**.

### Propuesta revisable de #27 — 2026-10-30

[Evaluación de proveedores, BFF/sesiones, modelo lógico y bootstrap RLS](issue-27-oidc-session-proposal.md). El proveedor de producción no se ha elegido. La propuesta solicita aprobación de la base BFF/JDBC, frontera de identidad global y política mínima de MFA/invitaciones antes de nuevas dependencias o migraciones. No hay autenticación implementada en esa fase y este ADR conserva **Proposed**; OQ-014 sigue Open.
E02 issue #27. Cambios que contradigan este documento requieren issue, propuesta de ADR y aprobación; no modificar implementación silenciosamente. Una vez aprobado, actualizar estado a Accepted y registrar fecha de aprobación, responsable y PR.
