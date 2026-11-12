# Actores, roles y matriz de permisos — propuesta v0.1

**Revisión SPEC01 #74:** [matriz inicial #34 aprobada](../security/FISCAL-PERMISSIONS-PROPOSAL-74.md) según [D74-03](../decisions/issue-74-owner-approval.md); sin grants efectivos instalados aquí. Owner/supervisor/contador requieren recurso exacto con habilitación vigente, auxiliar DENY; reactivar/cambiar rol no recupera grants históricos sin revisión explícita. La tabla orientativa histórica no concede cartera completa ni permisos de módulos posteriores. Cuatro roles iniciales, sin Billing/portal nuevos; V7/V9/V11 conservan alcance administrativo.

**Estado:** Proposed. Referencias FR-001–009, BR-001–005, ADR-0002. No equiparar 'rol' con acceso universal; las capacidades son verificadas por backend y restringidas por tenant/expediente/estado.

## Actores
1. **PLATFORM_OPERATOR:** mantenimiento de SaaS y tenant provisioning, plan y monitoreo. No tiene acceso por defecto a CFDI, documentos o honorarios. Acceso excepcional separado, expirable y auditado.
2. **FIRM_OWNER_ADMIN:** define configuración del despacho, usuarios, permisos, plantillas, cartera y cuotas. Puede actuar como contador en despachos unipersonales (separación de funciones configurable, OQ-005).
3. **FIRM_SUPERVISOR:** revisa declaraciones, acuses y pagos dentro de expedientes autorizados; puede reasignar trabajo.
4. **ACCOUNTANT:** gestiona contribuyentes asignados, crea obligaciones, prepara cierres, adjunta evidencias y registra presentaciones con permisos.
5. **ASSISTANT:** captura y prepara trabajo/documentos de su cartera; sin facultad de aprobación salvo asignación explícita.
6. **FIRM_BILLING:** ve cuotas y cobranza de contribuyentes autorizados; no implica permiso de leer CFDI o credenciales.
7. **TAXPAYER_PORTAL (futuro):** acceso sólo a contribuyente(s) expresamente delegado(s), documentos compartidos y solicitudes; no ve controles internos del despacho.

## Matriz orientativa (hasta aprobación)
| Capacidad | Owner | Supervisor | Accountant | Assistant | Billing | Portal |
|---|---|---|---|---|---|---|
| Tenant settings/plan | ✓ | — | — | — | — | — |
| Invitar/modificar usuarios | ✓ | limitado* | — | — | — | — |
| Alta/edición contribuyentes | ✓ | ✓ | asignados | asignados limitado | — | — |
| Leer expediente general | ✓ | asignados | asignados | asignados | datos mínimos | propio/compartido |
| Editar plantillas obligaciones | ✓ | ✓ autorizado | propuesta | — | — | — |
| Preparar evidencia de obligación | ✓ | ✓ | asignados | asignados | — | — |
| Aprobar revisión | ✓ | ✓ | delegado explícito | — | — | — |
| Registrar presentación/pago SAT | ✓ | autorizado | autorizado | preparado/no final* | — | — |
| Ver documentos fiscales | ✓ | asignados | asignados | asignados según tipo | — | compartidos |
| Gestionar honorarios | ✓ | rol específico* | lectura propia* | — | ✓ | — |
| Ver auditoría de expediente | ✓ | asignados | asignados limitado | actividad propia | honorarios propios | — |
| Ver todos los expedientes | ✓ sólo tenant | según rol/alcance | no | no | no | no |

`*` decisiones por resolver OQ-005/OQ-015; no implementar permisos temporales amplios por omisión. Ningún rol recibe acceso por esta tabla si un capabilty policy no se ha definido y probado.

## Jerarquía de autorización
Autenticación -> membresía activa en despacho -> capacidad explícita -> asignación/contribuyente/documento -> estado de dominio -> operación; aplicar en comandos, listas, exports, API y jobs.

## Invariantes
- El administrador de plataforma no es automáticamente FIRM_OWNER_ADMIN.
- El contribuyente no inicia sesión en la plataforma interna y sus usuarios portal no heredan permisos del despacho.
- Roles deshabilitados no borran historial de acciones; revocación surte efecto servidor.
- Cambios de permiso se auditan y las consultas de acceso se prueban con dos tenants y cartera parcial.

## Roles iniciales efectivos de #27 (local/test)

FIRM_OWNER_ADMIN, FIRM_SUPERVISOR, ACCOUNTANT y ASSISTANT se asignan a membresías servidor, nunca a roles arbitrarios del IdP/UI. Todos requieren membresía ACTIVE para abrir contexto. Sólo owner con MFA local reciente puede invitar/revocar/suspender en esta base; no hay promoción pública a owner. Supervisor/contador/auxiliar no reciben una matriz funcional implícita. Capacidad, asignación de expediente y administración completa siguen en #28/#33. [Controles y límites de producción](../security/AUTHENTICATION.md).

## Base #28

[Capacidad y asignación por recurso](../security/RESOURCE-AUTHORIZATION.md) se comprueban en backend/SQL dentro de la misma transacción. Esta implementación no aprueba la matriz orientativa: owner y supervisor tampoco reciben acceso fiscal universal. Sólo se conservan permisos técnicos explícitos de #27; permisos operativos no configurados deniegan. La prueba de dos contadores con carteras separadas usa políticas/recursos sintéticos desechables, no tablas fiscales ni una resolución de OQ-015.
