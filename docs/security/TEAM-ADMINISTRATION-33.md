# Administración de equipo #33: política A aprobada

Base de esta ampliación: PR #91, rama `issue-33-team-management`; conserva el corte de correo V10. El propietario aprobó expresamente A el 2026-11-11: exclusivamente FIRM_OWNER_ADMIN del propio despacho, identidad/membresía/tenant ACTIVE y MFA confiable de los últimos 10 minutos. No aprueba toda OQ-015 ni ADR-015; OQ-014 y #74 siguen abiertos. FR-005/011, BR-003/004, NFR-007. [Registro de decisión](TEAM-POLICY-PROPOSAL-33.md).

## Alcance implementado

- Directorio paginado y búsqueda literal de nombre/correo dentro del despacho; filtros ACTIVE/SUSPENDED/REVOKED. Nombre/correo privados verificados cuando existen, roles internos, estado global, incorporación, versión. No se fabrica última actividad, nombre, correo o cartera.
- Invitaciones paginadas con estados PENDING/ACCEPTED/REVOKED/EXPIRED, creadas/vencimiento y versión. Nunca tokens, hashes, issuer, subject ni sesiones. El contacto mostrado es el verificado actual de la identidad exacta; el correo no retargetea el receptor.
- Suspensión/revocación existentes integradas con comandos versionados; sólo reactivación SUSPENDED a ACTIVE con identidad global ACTIVE y cuota V9. REVOKED es terminal. No borrado de identidad, membresía ni historial.
- Cambio entre FIRM_SUPERVISOR/ACCOUNTANT/ASSISTANT de otros miembros ACTIVE o SUSPENDED con un rol operativo único. No autoedición, propietario, transferencia a owner ni interpretación de jerarquía. Combinaciones heredadas ambiguas fallan cerrado; no se eliminan roles silenciosamente.
- Sin nuevos permisos para supervisor/contador/auxiliar. AUDIT_VIEW, capacidades fiscales y asignación de cartera no se conceden. [Cartera diferida](../architecture/TEAM-MANAGEMENT-33.md) permanece #74/OQ-013/015.

## Persistencia V11

`V11__approved_team_administration.sql` no modifica V1–V10. Instala tres filas owner/TENANT/MFA en role_capabilities: MEMBERSHIP_VIEW, MEMBERSHIP_ASSIGN_ROLE y MEMBERSHIP_REACTIVATE. Los permisos INVITE/DISABLE de #27 y PLAN_VIEW de V9 se conservan. Extiende los enums tipados de denegación, sin abrir consultas de auditoría.

Añade `version` bigint seguro para JavaScript a memberships/invitations, con trigger BEFORE UPDATE que incrementa también ante comandos heredados; los IDs no cambian. Índices tenant/created_at/id para orden fijo. `identity_private.team_commands` contiene tenant/command/actor/operación/target/huella/result_version, FK tenant/command al journal, FORCE RLS y ningún acceso directo runtime/sesiones. No contiene respuesta serializada, token o contacto.

Las funciones públicas estrechas `identity.team_members`, `team_invitations` y `team_command` requieren el contexto autorizado real, capacidad exacta y owner/MFA; no aceptan actor, roles del solicitante ni SQL. SECURITY DEFINER con search_path fijo, objetos calificados, sin privilegios PUBLIC/sesiones ni BYPASSRLS. Runtime conserva lectura directa sólo de sus propias membresías bajo las cercas RLS anteriores; no recibe SELECT global para resolver el directorio.

El directorio filtra tenant, búsqueda y estado **antes** de count/ORDER/LIMIT/OFFSET. Un CTE materializado mantiene filas y total en el mismo snapshot SQL. Límites page0..200, size1..50, search80 sin controles; orden fijo created_at/id, sin sort arbitrario. Son páginas por offset, no un snapshot durable entre solicitudes; acotar filtros en directorios extensos. No es un directorio global de correos.

## Autorización y concurrencia

Sesión verificada → actor global ACTIVE → preliminar de membresía/capacidad/MFA → lock de raíz → revalidación bajo locks → TenantTransactions → target/version/estado → mutación+evento+outbox+recibo → commit. Se mantiene AuthorizedTenantOperations, no otra ruta de autoridad.

V11 conserva el autorizador de V6 y toma UPDATE de raíz desde el inicio para SETTINGS_MANAGE, MEMBERSHIP_DISABLE, MEMBERSHIP_REACTIVATE y MEMBERSHIP_ASSIGN_ROLE. Las lecturas toman SHARE. Esto serializa escrituras administrativas del despacho y evita upgrades tardíos y permisos conservados después del callback. Después se bloquea la membresía target y, al activar, la cuota mediante el trigger V9 original. Las invitaciones/aceptaciones conservan sus locks y reglas existentes.

La cuota mide membresías ACTIVE, incluidas identidades deshabilitadas, y reservas según V9. Invitaciones pendientes no reservan plazas. Reactivar no usa un contador paralelo ni un bypass de bootstrap. La activación de identidad global DISABLED se rechaza; su recuperación no pertenece a este panel.

Las escrituras requieren expectedVersion e Idempotency-Key UUID. Replays autorizados de actor/huella exactos devuelven sólo target/version original/replayed, sin repetir efectos ni afirmar que ése sea el estado actual. La UI vuelve a consultar. Misma clave/otro payload o estado inválido409; versión vieja409; target ausente/ajeno en tenant propio404; autoridad insuficiente403; sesión inválida401. Se revalida autoridad incluso al reintentar. No-ops se rechazan como conflicto, salvo el replay exacto.

## Auditoría y outbox

Los cambios de estado usan el journal existente; reactivar emite MEMBERSHIP_REACTIVATED del trigger V9. El cambio real de rol emite MEMBERSHIP_ROLE_CHANGED con actor/correlación confiables y **roles anterior/nuevo de enum cerrado** (`previous_role_code`, `role_code`). Se inserta su outbox AUDIT_RECORDED de payload mínimo auditEventId en la misma transacción. No se actualiza una fila append-only para agregar metadata; no hay cuerpos/contactos/tokens en eventos.

Si falla el journal, outbox o recibo, se revierte rol/estado/versión/cuota. No hay worker ni acceso de lectura general al journal. Las denegaciones del autorizador conservan la frontera global privada sin UUID de tenant ajeno. Diagnósticos y métricas usan categorías cerradas; no nombres, correos, targets o SQL.

## API y frontend

GET `/api/v1/tenants/{tenantId}/team/members` y GET `.../invitations` con page/size/search/status. POST `.../members/{memberId}/status`, `.../members/{memberId}/role`, `.../invitations/{invitationId}/revoke`: CSRF, idempotencia y DTOs cerrados. JSON tipado, no-store, RFC9457 y correlación. OpenAPI3.1 implementado y TypeScript generados; no endpoint genérico authorize ni administración global.

Equipo abre Colaboradores; ofrece Invitaciones y Nueva invitación. DataTable real con paginación, filtros de servidor, tarjetas móviles, estados y panel lateral. Cambios requieren confirmación; el diálogo muestra mensajes seguros de cuota/conflicto, no excepciones. Una notificación de éxito requiere HTTP200 real. Tras mutar se consultan de nuevo directorio y consumo. La identidad del actor no puede editarse mediante estos comandos; no se fuerza un refetch de /me que desmonte el workspace. La sesión mantiene sus revalidaciones y los controles backend existentes.

Caché en memoria con clave usuario/tenant/vista/página/filtro y gcTime0; se ocultan filas mientras se revalida, se retiran ante401/403 y se abortan operaciones al salir. Cambiar actor/tenant desmonta paneles/formularios y no permite notificaciones tardías. No persistencia en localStorage, URLs de navegación ni tokens dentro de Query. Los UUID son localizadores, nunca autorización.

El correo conserva [el alcance B](LOCAL-EMAIL-INVITATIONS-33.md): opt-in Keycloak local/test, identidad previamente verificada, issuer/subject exactos, entrega manual y token una sola vez. Ningún listado recupera el secreto. La administración A no activa correo de producción, SMTP ni proveedor externo.

## Verificación y runbook

TeamAdministrationTests37: PostgreSQL18.6 y Keycloak reales; owner/otros roles/MFA, tenant, directorio/páginas/búsqueda, estados/cuotas, protección owner, versiones/replay, carreras de rol/reactivación y revocación con lock observable; sinks fallidos, recibos privados, guardas y privacidad. TeamAdministrationMigrationIT3: upgrade V10, checksum y rollback. Se preservan las 416 Java anteriores.

La regresión final debe acreditar **456 Java=412 Surefire+44 Failsafe,129 Vitest,61 Playwright fundación,20 OIDC y23 tools/ci**, sin omisiones. [Comandos](../../backend/VALIDATION-33.md), [evidencias reales](../ux/evidence/issue-33/README.md). Los resultados exactos por SHA se publican en PR #91; este documento no transforma tests pendientes en PASS.

Ante403: revisar sesión/estado/MFA con flujo OIDC, sin conceder roles manualmente ni relajar RLS. Ante409 de cuota: consultar Plan y uso; el owner no cambia límites. Ante versión vieja: actualizar la lista y confirmar una nueva operación. Ante fallo de sink: revisar diagnóstico allowlist y reintentar la misma clave; nunca borrar journal o editar migraciones aplicadas. Producción OIDC/MFA/correo, retención y matriz fiscal siguen pendientes de aprobación; cartera permanece diferida, no bloquea A.

Hardening de compatibilidad: la ruta heredada ya excluía REVOKED en su WHERE y responde204 sin mutar. La prueba HTTP verifica ese contrato, la permanencia de REVOKED y el rechazo de la reactivación nueva. V11 añade defensa en el trigger para que ninguna actualización convierta REVOKED en otro estado; no se afirma una vulnerabilidad previa que no existía. No se elimina la ruta ni se reduce su cobertura.

Salir de una vista o abortar fetch no revierte una transacción que el servidor ya confirmó. Al volver se consulta estado real; versión y clave idempotente evitan repetir efectos.
