# Entitlements técnicos por despacho — #32

Base: `1d0105e12d62aaed350d09275704d5ecbb4be4a6` (#89). Trazabilidad: FR-009, BR-081, NFR-001/002/003/009/013/015/020/021/026/030–036/040/044. ADR-015 y OQ-007 siguen Proposed/Open para precios, planes comerciales, cobro y cancelación; OQ-014, OQ-015 y #74 permanecen pendientes.

## Alcance y frontera de aprobación

El encargo autoriza infraestructura técnica y administración interna separada, no precios ni permisos implícitos. `TENANT_PLAN_VIEW` es un identificador, sin filas productivas de autorización por defecto. GET y UI preparados deniegan hasta una política limitada aprobada. No existe TENANT_PLAN_MANAGE, endpoint de escritura de planes, operador HTTP, cambio de plan, pagos ni workers nuevos.

Política técnica propuesta, pendiente de respuesta explícita: contar membresías ACTIVE independientemente del estado global del usuario; SUSPENDED/REVOKED no ocupan cupo. Cada membresía cuenta en su despacho, no un usuario global único. Las invitaciones pendientes no reservan automáticamente; comprobar al aceptar/reactivar. La alternativa sería reservar al emitir invitación, requiriendo liberación por expiración/revocación, límites de pendientes y política de limpieza. Otra alternativa sería descontar identidades desactivadas, requiriendo proteger también su reactivación global. No se adopta ninguna silenciosamente.

**Por esa frontera, V8 prepara `membership_capacity()` pero no instala su trigger.** Las aceptaciones aprobadas #27 conservan su comportamiento. Los tests instalan el hook únicamente en PostgreSQL desechable y lo retiran en finally. El PR no debe considerarse enforcement productivo de invitaciones mientras este punto siga pendiente. Las reservas explícitas sí tienen enforcement real de su capacidad, autorización y transacción. La aprobación de consulta propuesta es owner+MFA reciente, sin permiso de modificación, y tampoco se concede mediante fixtures productivos.

## Módulo y contratos

Spring Modulith: `entitlements` depende sólo de APIs públicas identity, audit y platform, sin ciclos ni acceso al puerto RLS inferior. `Capacity` exige un `AuthorizedAccess` vigente, MEMBERSHIP_INVITE y el mismo callback/transacción. `reserve` recibe Resource tipado, UUID idempotente, referencia real y unidades positivas exactas. USERS requiere una unidad y UUID de identidad global existente; no acepta actor/roles/límites de navegador. No hay endpoint de reserva público. Un futuro módulo debe autorizar su operación/recurso antes de consumir el puerto, aportar medición y mutación atómicas, y ampliar políticas SQL y pruebas.

| Recurso | Unidad | Medición actual | Integración futura |
|---|---|---|---|
| USERS | membresía ACTIVE del tenant | COUNT de identity.memberships; nunca contador duplicado | trigger propuesto en aceptación/reactivación, #33 |
| TAXPAYERS | contribuyente | NULL / NOT_INTEGRATED | #34, estado y política de conteo tras #74 |
| STORAGE_BYTES | byte | NULL / NOT_INTEGRATED | almacenamiento privado, reservar bytes antes de upload; confirmar sólo objeto durable verificado; compensar fallo |
| JOBS_RUNNING | trabajo de negocio ejecutándose | NULL / NOT_INTEGRATED | claim/lease/fencing y liberación transaccional |
| JOBS_PENDING | trabajo de negocio pendiente | NULL / NOT_INTEGRATED | enqueue/dequeue real, independiente de running |
| JOBS_PER_PERIOD | trabajo de negocio por período | NULL / NOT_INTEGRATED | definir período, zona, reinicio y reintentos antes de habilitar |

Outbox no es un job de negocio. `Capacity.reserve` devuelve NOT_INTEGRATED para los cinco recursos futuros, sin escribir consumo ni reservas. No hay adaptador productivo ficticio, CRUD fiscal, documentos o matriz fiscal nuevos.

## Modelo físico y migración V8

V1–V7 no cambian. PostgreSQL 18.6, UUID y FK a la raíz existente. Sin backfill de límites comerciales: un tenant anterior conserva sus datos y puede recibir configuración por procedimiento interno. Instalación limpia V1–V8 y upgrade desde V7 con checksums conservados. No hay downgrade destructivo; ante error Flyway revierte V8 y se diagnostica, sin modificar historia aplicada.

- `entitlement_private.limits`: PK tenant/resource, límite entero 0–9007199254740991, versión 1–9007199254740991, valid_from/valid_until. NULL en valid_until sólo significa sin expiración temporal; jamás límite ilimitado. Primera asignación exige expectedVersion=0. Nueva asignación entra en vigor inmediatamente; se conserva la historia previa.
- `changes`: comando UUID global, huella SHA-256 de argumentos canónicos, aprobación, versión/valor/vigencia aplicados, instante y FK al journal. Append-only con rechazo de UPDATE/DELETE/TRUNCATE; índice tenant/resource/version. Metadata administrativa privada global para deduplicar comandos, misma frontera de credencial que provisioning #31.
- `reservations`: PK tenant/command, FK tenant/resource, identidad referenciada, actor real, amount=1, versión de configuración origen, estado RESERVED/CONFIRMED/RELEASED e instante. Índice parcial de reservas vivas y unicidad tenant/identidad en RESERVED. No se libera automáticamente por un reloj ni un worker inexistente.

Las tres tablas usan ENABLE/FORCE RLS, sin acceso directo runtime/sesiones ni PUBLIC. Limits/reservations están cercadas por contexto transaccional; history sólo migrador. Funciones definer con search_path fijo y nombres calificados; privadas sin USAGE/EXECUTE runtime. Guardas de arranque verifican roles, ownership, tablas, FORCE y grants, además de las guardas #26–31. Un migrador/DBA comprometido está fuera de la garantía runtime.

## Administración y auditoría

`infra/postgres/assign-tenant-quota.sql` sólo con conexión fiscuvia_migrator custodiada, nunca desde owner/browser/runtime. Revisar de forma independiente tenant, recurso, límite, versión, vigencia y aprobación; UUID de aprobación no autentica al administrador. Como #31, la credencial migradora constituye autoridad y el registro verificable de aprobación vive en el proceso administrativo externo. No hay promesa criptográfica contra el propio custodio comprometido.

Ejecutar con psql `-W` y `-f infra/postgres/assign-tenant-quota.sql`, variables tenant_id, command_id, approval_reference, resource, limit_value, expected_version y valid_until (vacío = sin expiración). No poner contraseña en argv/logs ni guardar aprobaciones reales en Git. Obtener versión mediante revisión administrativa privada. Un comando por transacción/tenant; reintentar exactamente los mismos parámetros y UUID. Reutilizar clave con parámetros distintos produce conflicto; versión obsoleta rechaza, nunca last-write-wins.

Orden de locks: clave administrativa → perfil raíz FOR UPDATE → configuración FOR UPDATE. Runtime ya congela lifecycle/membresía/capacidad/MFA antes del contexto RLS; reservas bloquean la misma fila de configuración. Administración no puede pasar entre comprobación y commit de una reserva. Dos reservas sobre la última unidad se serializan; no se usa una comprobación Java aislada. Valores y operaciones evitan overflow por resta comparada, usando límites exactos compatibles con TypeScript.

Cada cambio real registra TENANT_QUOTA_CHANGED/TENANT_QUOTA_CHANGE con actor_kind DATABASE_ADMIN, actor_id NULL, tenant, correlation ID, command, contrato v1, SUCCESS y aprobación. Journal y outbox se insertan antes del recibo en la misma transacción; fallo en cualquiera revierte configuración/historia. Reservas nuevas/liberaciones efectivas registran QUOTA_RESERVED/QUOTA_RELEASED con actor verificado y referencia de reserva, sin secretos ni metadata fiscal. Replay no duplica eventos. Confirmación real se liga al cambio de membresía y sus eventos de identidad, jamás a un contador manual. Un outbox sólo contiene auditEventId.

## Estados y concurrencia

Sin configuración: UNCONFIGURED, no capacidad ilimitada; vencida: EXPIRED; agotada: EXHAUSTED. Configuración activa con cupo permite RESERVED. Replay devuelve el estado duradero de la reserva sólo para el mismo actor/referencia; clave incompatible es conflicto. Reserva confirmada no se puede liberar como si borrara consumo real; una membresía se desactiva mediante su flujo autorizado. Liberación repetida es no-op. Rollback conserva posibilidad de reintentar la misma clave.

Uso confirmado proviene de COUNT ACTIVE; reservado es cantidad RESERVED; disponible=max(0,límite-uso-reservado) únicamente con configuración vigente; sobrecupo compara uso+reservas con límite. Reducir por debajo del consumo no elimina ni suspende registros, roles o atribuciones. El hook preparado vuelve a comprobar la configuración vigente incluso para reservas anteriores: una reducción puede impedir confirmar, dejando reserva liberable explícitamente. No se promete que una reserva sobreviva como derecho irrevocable a una reducción administrativa.

## API y presentación

GET `/api/v1/tenants/{tenantId}/entitlements`, seis DTOs cerrados tipados; sesión vigente, identidad/membresía ACTIVE, tenant ACTIVE, TENANT_PLAN_VIEW y MFA si la política lo exige, RLS. UUID candidato nunca es autoridad. Cache-Control no-store, correlation ID y RFC 9457 existentes. Sin mutaciones HTTP de planes. El OpenAPI implementado documenta exclusivamente esta consulta condicionada a autorización; tipos TypeScript regenerados. El borrador comercial/fiscal no cambia.

Plan y uso se abre dentro del contexto autorizado actual mediante controles con teclado. Reutiliza PageHeader del workspace, StatCard, StatusPill y estados Loading/Error/Permission del sistema #52. Muestra límite/versión, medición, reserva, disponible, sobrecupo, ausencia de configuración, recurso futuro y error recuperable. NULL se presenta como no disponible/sin medición, no cero. No precios ni botones comerciales. Claves de consulta incluyen actor/tenant, AbortSignal y limpieza existente al cambiar despacho; 401 retira sesión, 403 retira cifras. Capturas sólo sintéticas de presentación, nunca prueba sustitutiva de OIDC/RLS.

## Dependencias y límites de revisión

#33 debe aprobar conteo/aceptación/reactivación y permisos completos de equipo antes de activar la integración preparada; la política actual prohíbe reactivar mediante otra invitación. #34 necesita adapter de medición y mutación transaccional tras #74; almacenamiento necesita protocolos de upload/compensación, tamaño real y autorización de objetos; jobs necesitan estados/leases/períodos y autoridad de negocio propia. No se resuelven OQ-007/014/015 ni ADR-015 por esta infraestructura.

No habilitar piloto ni considerar terminada la integración de invitaciones sin revisar esos pendientes y la evidencia del SHA del PR. [Validación #32](../../backend/VALIDATION-32.md).
