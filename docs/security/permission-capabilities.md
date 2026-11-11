# Catálogo de capacidades (propuesto)

## Administración de equipo #33 — ampliación aprobada

A aprobada #33: MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE y MEMBERSHIP_REACTIVATE exclusivamente owner propio ACTIVE+MFA reciente; no autoedición/owner/REVOKED, cuota V9. OQ-015 conserva los permisos fiscales/auxiliares pendientes. [Controles y alcance](TEAM-ADMINISTRATION-33.md).

## Corte aprobado #33: invitación por correo local/test

Se reutiliza MEMBERSHIP_INVITE owner ACTIVE + MFA reciente, sin grants nuevos. El propietario aprobó explícitamente el flujo de correo sólo con identidades Keycloak local/test ya verificadas y destinatario issuer/subject exacto. [Implementación y contrato](LOCAL-EMAIL-INVITATIONS-33.md). MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE y autoridad de reactivación siguen sin autorización: [propuesta pendiente A](TEAM-POLICY-PROPOSAL-33.md). No usar la aprobación del correo como aprobación de la matriz de equipo/fiscal. No hay permisos sintéticos positivos sustituyendo esa autorización.

## Propuesta mínima pendiente #31

`TENANT_READ` conserva sus grants aprobados #27. `TENANT_SETTINGS_MANAGE` cuenta con [aprobación expresa limitada del propietario](https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209): sólo FIRM_OWNER_ADMIN, en su despacho ACTIVE, identidad/membresía ACTIVE y MFA confiable ≤10 minutos. Campos: nombre comercial, contacto opcional, zona horaria permitida y locale es-MX. No permite estado/owner/roles/logo/expedientes; supervisor/contador/auxiliar DENY. **V7 instala permanentemente esa única política con requires_mfa=true y referencia de aprobación; V1–V6 no cambian.** Las pruebas OIDC/SQL y navegador usan la política migrada, sin grant sintético de edición. Alta/suspensión/reactivación usan exclusivamente el procedimiento migrador interno aprobado, sin OPERATOR_TENANT_PROVISION HTTP ni privilegios fiscales. [Autoridad, fronteras y pruebas](../architecture/TENANT-PROVISIONING.md). No resuelve toda OQ-015 ni aprueba proveedor/MFA productivo.

Implementar comprobación centralizada por capacidad y resource scope; no dispersar `if role === ...` ni confiar en listas de permisos proporcionadas por el frontend. Estado: Proposed hasta aprobación.

## Identidad / tenant
- TENANT_READ, TENANT_SETTINGS_MANAGE, TENANT_PLAN_VIEW, TENANT_PLAN_MANAGE
- MEMBERSHIP_INVITE, MEMBERSHIP_ASSIGN_ROLE, MEMBERSHIP_DISABLE, MEMBERSHIP_VIEW
- AUDIT_VIEW, OPERATOR_TENANT_PROVISION, OPERATOR_SUPPORT_REQUEST (sin acceso fiscal directo)

## Clientes
- TAXPAYER_VIEW, TAXPAYER_CREATE, TAXPAYER_EDIT, TAXPAYER_ARCHIVE
- TAXPAYER_ASSIGN, TAXPAYER_CONTACT_MANAGE, TAXPAYER_EXPORT

## Obligaciones / revisión
- TEMPLATE_VIEW, TEMPLATE_MANAGE, OBLIGATION_CONFIGURE, OBLIGATION_VIEW
- OBLIGATION_GENERATE, OBLIGATION_PREPARE, OBLIGATION_SUBMIT_REVIEW
- OBLIGATION_APPROVE, OBLIGATION_REJECT, OBLIGATION_RECORD_FILED
- TAX_PAYMENT_RECORD, OBLIGATION_REOPEN, OBLIGATION_MARK_NOT_APPLICABLE
- CERTIFICATE_EXPIRY_READ, CERTIFICATE_EXPIRY_EDIT

## Tareas / documentos
- TASK_VIEW, TASK_CREATE, TASK_ASSIGN, TASK_EDIT, TASK_CLOSE
- DOCUMENT_UPLOAD, DOCUMENT_VIEW, DOCUMENT_DOWNLOAD, DOCUMENT_SHARE
- DOCUMENT_CLASSIFY, DOCUMENT_ARCHIVE

## Honorarios
- FEE_AGREEMENT_VIEW, FEE_AGREEMENT_MANAGE, FEE_CHARGE_CREATE
- FEE_PAYMENT_RECORD, FEE_ADJUST, FEE_REPORT_VIEW

## Reportes / CFDI / portal
- REPORT_VIEW, REPORT_EXPORT, GLOBAL_SEARCH
- CFDI_IMPORT, CFDI_VIEW, CFDI_EXPORT (posterior)
- SAT_DOWNLOAD_REQUEST (posterior; NO implica acceso a llave privada)
- PORTAL_DOCUMENT_SUBMIT, PORTAL_DELIVERABLE_VIEW (posterior)

## Autorización en capas
Se evalúa membership + tenant + capability + resource ownership/assignment + estado. No resolver por frontend. Auditar permisos elevados. Denegar por defecto si capacidad sin asignación aceptada.

## Tests
Denegar cross-tenant, same-tenant fuera de cartera, resource ID adivinado, role revocado, empleado suspendido, portal restringido. Repetir en export, archivos, job y búsqueda. Los mapas de asignación concretos a roles se detallarán antes del issue correspondiente y deberán ser aprobados; no conceder ALL a roles operativos por comodidad.

## Alcance efectivo #27

Los cuatro códigos de rol iniciales pertenecen a cada membresía persistida, no a tokens/UI. Esta entrega sólo autoriza membresía ACTIVE y operaciones mínimas owner+MFA de invitación/revocación; no implementa esta matriz completa de capacidades. #28 debe consumir AuthorizedTenantOperations y añadir capacidad/recurso antes del trabajo fiscal. [Guía efectiva](AUTHENTICATION.md).

## Infraestructura efectiva #28 y aprobación pendiente

[Política implementada y modelo físico](RESOURCE-AUTHORIZATION.md). Los códigos del catálogo no conceden acceso. Únicos grants productivos conservados de #27: TENANT_READ para los cuatro roles iniciales; MEMBERSHIP_INVITE/MEMBERSHIP_DISABLE para owner con MFA reciente. Las demás combinaciones permanecen DENY, incluso owner/supervisor. No hay ALL ni permisos heredados de claims.

V4 implementa asignación explícita por tenant/membresía/capacidad/categoría/UUID y una cerca SQL por recurso ligada a la transacción. Los permisos fiscales y documentales usados en las pruebas son sintéticos y no se instalan por Flyway. Propuesta para aprobación: capacidades por rol + asignación exacta por operación, sin alcance tenant-wide implícito. OQ-015, asignaciones múltiples OQ-013 y matriz definitiva requieren decisión del propietario; no se resuelven aquí.


V9 — política limitada aprobada: [referencia del propietario](https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955). TENANT_PLAN_VIEW sólo owner ACTIVE + MFA ≤10 minutos; trigger productivo de cupos en INSERT/UPDATE ACTIVE, sin cuotas comerciales por defecto. La aceptación conserva el flujo original y confirma reservas sin duplicar consumo; reactivaciones registran journal/outbox. [Detalles y límites](../architecture/TENANT-ENTITLEMENTS.md). No resuelve OQ-007/014/015, ADR-015 completa ni #74.
