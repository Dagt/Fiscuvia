# Política limitada de equipo — propuesta #33

Base revisada: main `19d6ec043ccbfda3c75c39b3e5428a72666cd3ba`, V1–V9 intactas. Estado: **A pendiente; B aprobada expresamente sólo para desarrollo y pruebas con Keycloak local/test, 2026-11-11**. Este documento no concede permisos administrativos adicionales. FR-005/011, BR-003/004; OQ-014/015 y #74 conservan sus pendientes.

## Decisión A: administración dentro del despacho

| Operación | Capacidad | Propuesta limitada |
|---|---|---|
| Listar colaboradores e invitaciones del tenant propio | MEMBERSHIP_VIEW | Sólo FIRM_OWNER_ADMIN, identidad/membresía/tenant ACTIVE y MFA confiable ≤10 minutos |
| Invitar y revocar invitaciones | MEMBERSHIP_INVITE | Conservar autorización #27; destinatario exacto y roles FIRM_SUPERVISOR, ACCOUNTANT o ASSISTANT; no owner |
| Suspender/revocar colaboradores no owner | MEMBERSHIP_DISABLE | Conservar autorización #27; nunca el propio actor ni una membresía con rol owner |
| Reactivar una membresía SUSPENDED | MEMBERSHIP_REACTIVATE | Nuevo permiso sólo para owner + MFA reciente; validar USERS mediante el trigger V9; REVOKED permanece terminal |
| Cambiar entre supervisor, contador y auxiliar | MEMBERSHIP_ASSIGN_ROLE | Sólo owner + MFA reciente, sobre otro colaborador no owner; nunca otorgar/quitar owner ni editarse a sí mismo |
| Consultar cupo | TENANT_PLAN_VIEW | Reutilizar permiso V9 y API existentes; no permitir al owner cambiar cuotas |

Supervisor, contador y auxiliar no reciben facultades administrativas nuevas. Un rol operativo no concede capacidades fiscales. No se habilita administración global, transferencia de propiedad ni degradación del último owner. La protección de todos los owners evita dejar al despacho sin propietario por estas operaciones; recuperación de un owner inactivo requiere el procedimiento interno vigente, no un bypass HTTP.

Listados limitados al tenant autorizado, paginación/búsqueda en servidor, sin acceso SQL global al directorio. Mutaciones con versión esperada, idempotencia y auditoría/outbox en la misma transacción. Mantener orden raíz → membresías/políticas → cuota; serializar los cambios para evitar upgrades de locks y revocaciones inconsistentes. No eliminar identidades ni autoría histórica.

## Decisión B: correo e identidad

El propietario aprobó expresamente este alcance en la conversación de #33 del 2026-11-11, incluyendo que el correo no es prueba suficiente de identidad, issuer/subject exactos en aceptación y ningún token en logs, auditoría, listados o respuestas posteriores. Su segunda respuesta reiteró el mismo alcance de correo; **no se interpreta como aprobación de A**. V10 y el formulario implementan únicamente este corte, sin nuevos grants de equipo.

OQ-014 todavía no aprueba proveedor/semántica de correo en producción. No existe un servicio de entrega configurado. Propuesta para el emisor Keycloak exclusivamente local/test:

1. Usar correo únicamente de una identidad que ya completó OIDC real y cuyo correo fue verificado por el emisor local controlado. Persistir la referencia exacta issuer/subject; nunca unir identidades globales por correo.
2. Recoger nombre opcional y correo verificado desde el callback OIDC comprobado, en persistencia privada mínima; no guardarlos como claims/tokens en Spring Session ni en logs/auditoría/outbox. El nombre es presentación, no prueba de identidad.
3. Introducir correo en el formulario, resolverlo sólo en backend dentro de esa política de emisor y fijar issuer/subject en la invitación existente. Comparación conservadora: dominio ASCII en minúsculas, sin colapsar alias/puntos ni vincular correos no verificados. Rechazar coincidencias ambiguas y destinatarios todavía no verificados con un mensaje genérico, sin endpoint de búsqueda global.
4. Conservar tokens aleatorios de 32 bytes, sólo hash persistido, vigencia de 24 horas, revocación y consumo atómico de un solo uso. Evitar duplicados vivos del mismo destinatario/tenant. Reintentar una creación no duplica la invitación; el token sólo se entrega una vez y nunca se recupera de persistencia.
5. Entrega manual explícita mediante token mostrado una vez al owner autorizado; no incluirlo en URL, logs, capturas o historial del navegador. No afirmar envío de correo, no añadir proveedor, credenciales o entrega externa. Producción permanece cerrada hasta aprobar proveedor, semántica de verificación y canal de entrega.

Alternativa B: conservar exclusivamente issuer/subject exactos de #27 y diferir la entrada por correo hasta aprobar una política de proveedor. En esa alternativa no se anunciará como implementada la invitación por correo.

## Cartera y datos disponibles

No existen clientes/contribuyentes productivos aprobados. La asignación de cartera permanece contrato futuro tras #74/OQ-013/015, usando las fronteras de autorización por recurso #28; no se crearán tablas fiscales ni carteras simuladas. Fecha de incorporación procede de la membresía real. Última actividad no se mostrará sin fuente específica confiable; no sustituirla por la hora actual. Nombre/correo ausentes se identifican como no disponibles, no se inventan.

## Pruebas exigidas al implementar

Política migrada real, sin grants sintéticos de administración; otros roles/tenants/MFA inválido deniegan. PostgreSQL18.6 y OIDC Keycloak reales, cuotas agotadas/concurrentes, reactivación de suspendido y rechazo de revocado/owner/autoedición, cambios de rol auditados, rollback/idempotencia, invitación caducada/reutilizada/revocada/destinatario equivocado y datos retirados al cambiar despacho. Mantener todas las suites y cinco checks por SHA; capturas reales desktop/móvil y estados de UI.
