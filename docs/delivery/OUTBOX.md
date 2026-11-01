# Outbox transaccional y protocolo de entrega — #29

ADR-008 continúa Proposed. Esta entrega instala persistencia y protocolo PostgreSQL; **no** instala scheduler, polling, broker, handler de negocio o cuenta de worker. No hay Kafka/Rabbit/Redis, servicios externos ni autoridad de sistema para trabajar expedientes. [Journal y atomicidad](../security/AUDIT-JOURNAL.md).

## Contrato implementado

Por cada evento confirmado, la misma función SQL inserta una fila outbox con event_id y tenant_id, AUDIT_RECORDED, schema_version=1, occurred_at/correlation heredados, causation_id=event_id y deduplication_key=event_id. PK/FK/unique son tenant-first; no existe ventana journal-confirmado/outbox-aún-en-memoria. Payload único permitido: `{ "auditEventId": "UUID_INTERNO" }`. SQL lo construye y rechaza claves adicionales, RFC, secretos u otro JSON. No serializa principal/sesión/AuthorizedAccess. Denegaciones globales no se convierten en trabajos tenant.

Estados PENDING → PROCESSING → DELIVERED o PENDING/DEAD. Attempts 0–5, available_at, lease_token UUID aleatorio, leased_until, completed_at y failure_code cerrado. CHECKs impiden combinaciones incoherentes; intención del evento y estados terminales son inmutables. No hay DELETE/TRUNCATE runtime ni consumidor.

OutboxProtocols.postgres crea el adaptador explícito con un datasource/transaction manager de transporte; no es un bean ni toma automáticamente credenciales runtime/migrador. Claim/ack/retry son funciones estrechas sin tenant como argumento: el tenant deriva de una autoridad de transporte aprobada. Runtime no tiene EXECUTE de esas funciones; entregar sólo un UUID de tenant no concede acceso.

## Frontera de autoridad

`journal_private.delivery_scopes` está vacía al instalar V5. Una concesión explícita vincularía un OID de rol PostgreSQL a **un solo tenant** y referencia UUID de aprobación. Se rechazan roles runtime/sesiones/migrador, superuser, BYPASSRLS, INHERIT, roles administrativos o con membresías, y roles con USAGE de app/identity. Además necesitan grants de funciones concretas; no DML/SELECT de tablas ni USAGE privado. Un rol eliminado/recreado no hereda el OID previo. Desactivar la autoridad bloquea siguientes operaciones; no autoriza trabajo con una fila previamente reclamada.

Las pruebas aprovisionan roles efímeros sin bypass, con scopes marcados por aprobaciones sintéticas, en PostgreSQL desechable. **No se crean roles/scopes de entrega en Compose, variables del JAR o arranque productivo**. Este modelo es sólo autoridad de transporte del aviso; no establece app.current_tenant_id ni llama TenantTransactions. Incluso con un lease válido no puede leer auditoría, membresías, app ni ejecutar un comando fiscal. La aprobación de credenciales, distribución por tenant, custodia/rotación y operación real sigue pendiente.

Alternativas pendientes para un worker de negocio: revalidar identidad/membresía/capacidad/asignación al ejecutar un comando originado por usuario, o definir autoridad de servicio limitada por tipo/tenant con aprobación propia. No reutilizar el principal guardado ni simular un owner. Un aviso histórico de auditoría puede entregarse después de revocar al usuario; eso **no** concede la acción original otra vez. No hay bypass nuevo de RLS de negocio. Las cuentas OID por tenant del protocolo no se adoptan unilateralmente como topología de producción; una solución multi-tenant de transporte necesita revisión de operación/carga y autoridad explícita equivalente.

## Claim, fencing y recuperación

- Claim acotado 1–100, lease 100–300000 ms; orden de elegibilidad/momento/UUID y FOR UPDATE SKIP LOCKED. Selección/claim/rotación de token/incremento de attempts son atómicos. En pruebas se usan leases cortos; producción aún no tiene valores operativos aprobados.
- PENDING listo o PROCESSING con lease vencido y attempts<5 puede reclamarse. Un token nuevo invalida al anterior. Leases agotados pasan a DEAD sin borrar el evento.
- process bloquea la fila del lease vigente, lee la versión persistida, ejecuta el efecto local idempotente y hace ack dentro de la misma transacción. Un segundo consumidor no ejecuta simultáneamente el callback local; el lock también protege si expira el tiempo durante el callback. Si ack detecta expiración, se revierte el efecto local.
- Retry sólo acepta failure_code cerrado, nunca exception.message. Backoff de 1, 2, 4 y 8 s en los intentos reintentables, fórmula limitada a 60 s y máximo cinco intentos. No se reclama antes de available_at. Errores permanentes/versiones no soportadas van a DEAD.
- Se conserva el orden de trabajos listos y se salta la fila bloqueada; un fallo no bloquea indefinidamente la cabecera por retries/attempts finitos. La equidad entre tenants, frecuencia de sondeo y límites de carga de un scheduler real siguen pendientes. Antes de habilitarlo: pausas/jitter, límites de concurrencia y revisión de starvation; nada de polling agresivo por defecto.

## Matriz de fallos

| Caso | Garantía / acción |
|---|---|
| Commit y crash antes de despachar | Fila durable PENDING permanece; no depende de memoria/listener |
| Dos claims | Lock SKIP LOCKED y actualización atómica entregan una sola concesión vigente |
| Crash con lease | Recuperación tras vencimiento con token nuevo; ack viejo no modifica nada |
| Efecto SQL local seguido de fallo | process hace rollback de efecto+ack; recibo del consumidor usa event_id UNIQUE |
| Efecto externo confirmado, crash antes de ack | Puede repetirse; downstream debe deduplicar por event_id. **At-least-once, nunca exactly-once** |
| Lease vence mientras hay efecto local | Fencing rechaza ack y revierte efecto; sólo después se libera el lock y otro puede recuperar |
| Retry duplicado o ticket de otro tenant | Token/estado/scope actuales se verifican en SQL; no basta el Claim Java |
| Usuario/membresía revocados | No cambia el hecho histórico; transporte sólo entrega aviso. Un trabajo de negocio futuro necesita autoridad nueva, no el tenant_id del payload |
| Versión futura no soportada | No ejecuta efecto; estado DEAD/UNSUPPORTED_VERSION |
| Tipo/payload inválido o sensible | Productor/CHECKs rechazan y revierten; no se redirige a una cola permisiva |
| Autoridad de transporte revocada | Claim/process/ack rechazan o no encuentran concesión; no se abre app context |

No puede garantizarse exclusión de un proceso externo que ignore el fencing tras perder su lease. No se publican efectos externos en este PR; los consumidores reales necesitarán idempotencia/fencing downstream. Un recibo SQL de prueba no certifica exactly-once de red ni procesamiento fiscal.

## Runbook y pendientes de operación

Desarrollo: Docker Linux/Ryuk, Java 25/Wrapper y PostgreSQL 18.6; [comandos completos](../../backend/VALIDATION-29.md). Los tests son independientes de Compose y no requieren credenciales externas. V5 aplica sobre V4 y también en instalación nueva; checksum y DDL transaccional/rollback se prueban. No editar V1–V5 aplicadas para corregir datos; usar migración revisada posterior o recuperación autorizada. No resetear volúmenes ni regenerar claves.

Diagnóstico administrativo: abrir transacción migrador controlada, `SELECT app.establish_tenant_context(UUID_AUTORIZADO)` y consultar columnas técnicas de `journal.outbox`/eventos por tenant/código/momento; rollback al terminar. Nunca incorporar contraseñas/filas completas a logs o artifacts. Revisar PENDING debido, PROCESSING vencido, DEAD/failure_code, attempts y latencia con cardinalidad acotada. El PR no concede un API de monitorización ni métricas por tenant; índices ya preparan consultas autorizadas futuras.

DEAD requiere revisión de causa/versión y un procedimiento futuro de reemisión con nueva causalidad; no se habilita replay arbitrario ni UPDATE de intención/terminales. Retención, archivo, alertas por cola/denegación, autovacuum/bloat, backups/restore, límites de carga, credenciales de servicio y revisión independiente siguen pendientes. Las tablas privadas de contexto se limpian de manera acotada; eventos/recibos no se purgan automáticamente.

Referencia TrailerSaaS: OutboxStore/DurableJobWorker/V25 aportan locking, leases, retry y fencing como prácticas contrastadas. No se copian su GUC de actor vacío, permiso CRUD runtime, logging de IDs o worker automático: Fiscuvia exige actor verificado para producción y transporte separado, sin autoridad de negocio. El namespace audit de la referencia es un placeholder, no evidencia de un journal reutilizable. Referencias primarias: [SELECT/SKIP LOCKED PostgreSQL 18](https://www.postgresql.org/docs/18/sql-select.html), [RLS](https://www.postgresql.org/docs/18/ddl-rowsecurity.html), [fases de eventos transaccionales Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html). El éxito aquí se escribe antes del commit en la misma DB, no mediante un listener AFTER_COMMIT.

No se cambia presupuesto $0, no se deshabilitan workflows y no se continúa con #30. Si no ejecutan runners, REMOTE CI NOT VERIFIED y causa comprobada/#79; si ejecutan, informar el resultado real por SHA en el PR, incluidos fallos. La primera ejecución de #29 sí inició pasos; no se atribuye un fallo de scanner a billing.
