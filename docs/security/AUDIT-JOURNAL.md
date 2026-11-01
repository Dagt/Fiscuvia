# Journal persistente — #29

Base integrada: main `1f09a26fc0afbcc3498b825e1b3f676cda79d2f0` (#84/#28). Infraestructura autorizada por la solicitud de #29; ADR-008 sigue Proposed para operación/consumidores futuros. FR-007, NFR-009/020. No se acepta el modelo fiscal #74 ni AUDIT_VIEW/OQ-015.

## Catálogo efectivo y procedencia

| Código v1 | Productor real | Atribución y datos permitidos |
|---|---|---|
| OWNER_BOOTSTRAPPED | Inserción del evento ACTIVE del primer owner aprobado, con owner_approvals y rol owner presentes | DATABASE_ADMIN, actor_id NULL, membresía y referencia UUID de aprobación; no atribuye la acción al nuevo propietario |
| INVITATION_ISSUED | INSERT confirmado de invitación | Actor interno verificado, tenant autorizado, UUID de invitación y rol inicial |
| INVITATION_REVOKED | Primer cambio de revoked_at | Actor interno, UUID; repetición sin cambio no duplica |
| INVITATION_ACCEPTED | Primer cambio de consumed_at por aceptación verificada | Actor destinatario verificado, UUID y rol; causation enlaza emisión |
| MEMBERSHIP_SUSPENDED / MEMBERSHIP_REVOKED | Cambio efectivo de estado | Actor verificado o DATABASE_ADMIN si es procedimiento migrador; UUID de membresía |
| PRIVILEGED_OPERATION_DENIED | Decisión atribuible de la frontera de autorización, después de rollback | Journal global privado separado: actor, operación y motivo cerrados; **sin tenant solicitado ni recurso** |
| AUTHORIZED_OPERATION_RECORDED | Puerto interno idempotente AuditJournal.execute | Contrato de infraestructura disponible/probado con comando SQL sintético; no lo emiten automáticamente módulos fiscales inexistentes |

Los productores de identidad son triggers AFTER sobre cambios reales. Así también se audita un comando SQL permitido que se ejecute por la vía estrecha; no basta con que el controller recuerde llamar a un logger. Los triggers no crean hechos de obligaciones, SAT, cobros, CFDI o archivos. Esos códigos/contratos son futuros y necesitan nuevas versiones y aprobación del módulo correspondiente. No hay comando público de cambio de roles/políticas; no se inventa ROLE_CHANGED para escrituras administrativas de DDL. La asignación inicial se conserva en la aceptación de invitación.

Los eventos usan UUID, tenant_id NOT NULL, actor_kind/actor_id, código/operación cerrados, clock_timestamp() timestamptz, correlation/command UUID, categoría/UUID de recurso autorizado cuando aplica, SUCCESS, schema_version=1, rol inicial/referencia de aprobación opcionales y causation del mismo tenant. No hay campo JSON libre ni snapshot before/after. Los puertos Java usan Receipt/EventCode/Denial tipados; SQL valida todos los campos y construye el payload outbox. No se guardan tokens, hashes de invitación, cookies, issuer/subject, RFC, PII arbitraria, cuerpos, SQL ni objetos serializados.

## Modelo y fronteras V5

`V5__audit_journal_and_outbox.sql` es aditiva; V1–V4 quedan intactas. `journal.events` tiene PK (tenant_id,event_id), UNIQUE (tenant_id,command_id), FKs a despacho/actor/causación tenant-safe e índices tenant/momento y tenant/código/momento. `journal.outbox` mantiene referencia compuesta al evento. `journal_private.commands` conserva deduplicación y recibo, con trigger diferido que impide confirmar un comando incompleto. `command_context` es metadato privado por xid, no historial; se invalida al terminar y se limpia en lotes acotados SKIP LOCKED. `denials` es global privado sin tenant; `delivery_scopes` es metadata de autoridad de transporte, vacía por defecto.

FORCE RLS en todas las tablas. Cerca RESTRICTIVE de tenant para eventos/outbox; comandos privados con política tenant. Metadata global tiene política exclusiva del migrador y no acceso runtime/sesiones. Runtime no tiene SELECT/INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER sobre ninguna tabla del journal. Sólo EXECUTE en las funciones productoras estrechas. Funciones SECURITY DEFINER pertenecen al migrador NOSUPERUSER/NOBYPASSRLS y tienen search_path fijo pg_catalog,pg_temp, sin SQL dinámico. PUBLIC no recibe ejecución. No se abren lectores HTTP; owner con MFA tampoco adquiere AUDIT_VIEW.

Triggers bloquean UPDATE/DELETE/TRUNCATE de events/denials, incluso errores administrativos ordinarios; outbox preserva intención y estados terminales. Las guardas rechazan drift FORCE, cercas, triggers deshabilitados, grants runtime de tablas o funciones de entrega; sesiones no tienen USAGE. Esto es **append-only lógico**, no WORM, certificación o resistencia a un DBA/migrador que pueda cambiar DDL o a un BFF comprometido. No se implementa encadenamiento hash ni custodia criptográfica de integridad. Actor y FKs no se borran al revocar membresías.

## Contexto y atomicidad

La correlación sale del contexto tipado del filtro del servidor (nunca header/MDC arbitrario). Fuera de HTTP se genera un UUID interno. AuditJournal.bindCommand fija command/correlation por xid mediante HMAC con separación de dominio respecto a la prueba de actor de #27. SQL vuelve a exigir actor verificado y contexto tenant al escribir. No acepta actor_id ni tenant_id como argumentos de un evento público. La clave ya existente permanece privada, sin exportarla ni añadir una clave a Git. Su custodia/rotación productiva sigue pendiente con #27.

IdentityStore vincula el journal después de verificar identidad y antes de la mutación. Aceptación tiene la autoridad específica de invitación de #27 y obtiene tenant de la fila verificada; no necesita un permiso genérico previo ni relajar RLS. El bootstrap migrador usa UUIDs de operación/correlación generados por DB y referencia de aprobación existente; el source DATABASE_ADMIN describe la cuenta técnica, **no identifica a la persona que custodia esa credencial**. Ese riesgo requiere procedimiento de aprobación y custodia individual antes de producción.

Cambio sensible + trigger journal + outbox + commit usan la misma conexión PostgreSQL. Un error de auditoría/outbox aborta el cambio. Rollback antes/después de la escritura o después de append elimina todos los éxitos. No se usan @Async, AFTER_COMMIT ni una conexión independiente para auditar éxito. Las lecturas de autorización/membresía siguen bloqueadas hasta commit; una revocación confirmada antes del chequeo deniega y una posterior espera. El journal empieza con V5: no se reconstruyen hechos/correlaciones históricas inexistentes de V3; sus registros previos se conservan.

AuditJournal.execute(commandId, Runnable) exige la transacción ya autorizada y devuelve un recibo mínimo. La clave está acotada por tenant, actor, operación y target: replay sólo tras autorización fresca, sin repetir callback; otro actor/semántica produce conflicto. El claim de comando y su resultado son transaccionales, incluso con solicitudes concurrentes. No almacena respuestas HTTP ni tokens de invitación: los endpoints actuales conservan sus contratos y no prometen replay de un token bruto ni un protocolo HTTP Idempotency-Key nuevo. Cada comando registra un hecho; lotes futuros deben definir hijos/causalidad y contratos revisados, no reutilizar la misma clave para hechos diferentes.

## Política de denegaciones

Se persisten decisiones de TENANT_SETTINGS_MANAGE, MEMBERSHIP_INVITE, MEMBERSHIP_ASSIGN_ROLE, MEMBERSHIP_DISABLE y AUDIT_VIEW que llegan a la frontera con identidad global verificada y producen un motivo cerrado. No se atribuye identidad a anónimos, sesiones inválidas, CSRF, errores de protocolo OIDC o inputs malformados. Tampoco se registra un tenant ajeno sólo porque se solicitó su UUID.

Se registra una sincronización transaccional; **sólo tras STATUS_ROLLED_BACK** se abre una transacción nueva, se vuelve a verificar el principal y se firma una prueba específica que incluye actor, xid, operación, motivo, correlación y command UUID. La función global estrecha verifica esa prueba antes de insertar. No es un permiso de escritura genérico ni una función para consultar terceros. También funciona si el rechazo ocurrió dentro de una transacción exterior; nada se confirma prematuramente.

Si DB/revalidación/almacenamiento fallan, el acceso permanece denegado y se emite únicamente diagnóstico tipado DENY/INTERNAL_FAILURE con correlación. No se afirma durabilidad de esa denegación fallida, ni se crea un éxito o bypass para recuperarla. ACCESS_DECISION sigue siendo diagnóstico, jamás el journal durable. La política de éxito es más fuerte: sin auditoría no hay commit del cambio. Límites de tasa, alerta por fallo y retención de denegaciones requieren operación aprobada antes del piloto para evitar crecimiento por abuso autenticado.

## Diagnóstico y pendientes

Consultar con rol administrativo controlado, transacción y contexto tenant, según runbook de [outbox](../delivery/OUTBOX.md). No otorgar SELECT runtime/owner para investigar ni subir filas/logs a artifacts. Buscar por tenant/momento/código y correlación del servidor bajo custodia; no ampliar logs SQL. Eventos confirmados no se borran automáticamente. Retención/archivo/borrado legal OQ-008 y acceso AUDIT_VIEW necesitan aprobación; no crear un purge para resolver crecimiento.

[Protocolo y resultados](../../backend/VALIDATION-29.md): SQL real/runtime/transportes sin bypass, OIDC real, oráculos RLS/append-only/grants, atomicidad y carreras. #74 intacto; no hay UI nueva, lector de historial, proveedor productivo, worker de negocio ni avance de #30. Presupuesto $0 intacto, seguimiento #79 si hay bloqueo; el PR registra el estado real por SHA. La primera ejecución de #29 sí inició runners.
