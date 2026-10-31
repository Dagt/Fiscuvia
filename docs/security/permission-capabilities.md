# Catálogo de capacidades (propuesto)

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
