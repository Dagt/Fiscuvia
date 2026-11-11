# Infraestructura física de tenancy — #26

Implementación para revisión, independiente del modelo fiscal Proposed v0.1 y del gate #74. Trazabilidad: FR-001/014, BR-001/002/003/005 y NFR-001..003/008/021. No se declara completo FR-002 (membresía autorizada), la autorización de negocio ni el gate de seguridad del piloto. ADR-007 conserva Proposed.

## Modelo efectivamente creado

Una migración nueva: `backend/src/main/resources/db/migration/V2__tenant_isolation_foundation.sql`. V1 no cambia. Compatible con instalación nueva o V1 existente; Flyway valida checksum, registra V2 una vez y ejecuta su DDL transaccionalmente.

| Objeto | Columnas/propósito | Acceso runtime |
|---|---|---|
| `app.tenants` | `tenant_id uuid PK`, `created_at timestamptz NOT NULL`; raíz mínima del despacho, sin configuración comercial/fiscal | SELECT de su propio contexto; sin provisioning/mutaciones |
| `infrastructure.rls_transaction_context` | `transaction_id xid8 PK`, `tenant_id uuid NOT NULL`; metadatos privados que vinculan una transacción real con un tenant | Ningún acceso directo; schema privado |
| `app.establish_tenant_context(uuid)` | Vincula una sola vez el tenant a la transacción y configura GUCs locales | EXECUTE explícito, para adaptadores internos autorizados |
| `app.current_tenant_id()` | Devuelve el UUID sólo si vínculo privado, GUC y transacción actual concuerdan | EXECUTE explícito para políticas RLS |

No hay tenants sembrados. El migrador es propietario separado del runtime y NO tiene BYPASSRLS. Tablas con ENABLE/FORCE RLS, incluso el registro privado. En ese registro, la única política permite al migrador acceder a metadatos de plataforma; no es una tabla de negocio ni una vía de consulta de expedientes. Las funciones SECURITY DEFINER ejecutan únicamente SQL fijo sobre ese registro, bajo migrador no superuser, search_path fijo `pg_catalog, pg_temp`, sin SQL dinámico ni parámetros textuales de consulta. Se revoca EXECUTE a PUBLIC; no se concede USAGE del schema infrastructure al runtime.

El registro privado evita confiar únicamente en GUCs, que un rol runtime podría modificar mediante SQL. Cambiar/borrar `app.current_tenant_id` no crea un vínculo nuevo ni permite cambiarlo por otro despacho dentro de esa transacción. La función de lectura no castea texto arbitrario a UUID: entradas ausentes, vacías, malformadas, falsificadas o heredadas devuelven NULL. El identificador xid8 incluye época y sólo es válido para la transacción de origen.

Al terminar la transacción, el vínculo queda **invalidado**, aunque una fila de metadatos permanezca hasta la siguiente limpieza. Cada establecimiento elimina hasta 100 vínculos ya visibles de transacciones previas mediante `FOR UPDATE SKIP LOCKED`. Vínculos no confirmados de otras transacciones son invisibles; no se serializan tenants esperando esa limpieza. No es historial ni auditoría. Monitorizar bloat/autovacuum y volumen de esta tabla antes del piloto; no convertirla en almacenamiento de sesiones. En reposo pueden quedar metadatos inertes tras la última operación. No hay limpieza en worker ficticio.

## Políticas y privilegios

`tenant_fence` es RESTRICTIVE, para runtime/migrador, `FOR ALL`, con:

```sql
USING (tenant_id = (SELECT app.current_tenant_id()))
WITH CHECK (tenant_id = (SELECT app.current_tenant_id()))
```

La cerca se combina con AND con políticas permisivas de operación. SELECT se permite a runtime/migrador; INSERT/UPDATE/DELETE de la raíz sólo al migrador y siempre bajo la cerca. Runtime recibe únicamente SELECT; no TRUNCATE, REFERENCES, TRIGGER, ownership, grants globales ni privilegios administrativos. Provisioning corresponde a #31, no existe endpoint para ello.

Toda futura tabla tenant-owned debe incluir `tenant_id uuid NOT NULL`, PK o UNIQUE `(tenant_id,id)`, FKs compuestas, índices tenant-first, ENABLE/FORCE RLS y cerca restrictiva con USING/WITH CHECK. Conceder sólo las operaciones necesarias mediante políticas/grants explícitos. No copiar permisos CRUD de los fixtures a producción. No conceder defaults amplios ni acceso público. RLS no controla TRUNCATE: por eso no se concede. Los checks de integridad referencial de PostgreSQL tienen semántica propia; claves compuestas y unicidad por tenant evitan inferencias sobre claves comerciales ajenas.

La guarda de arranque rechaza SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/REPLICATION/INHERIT, membresías de roles DB, ownership de DB/schemas/tablas, CREATE de schema y grants de TRUNCATE/REFERENCES/TRIGGER. Revisa todas las tablas de app: tenant_id UUID obligatorio, ENABLE/FORCE y existencia de una cerca restrictiva con contexto en USING/WITH CHECK. No reemplaza la revisión completa de SQL/políticas ni es un detector universal de manipulación por un DBA. Entidades globales futuras requieren schema/guardas explícitamente revisados; no añadir excepciones silenciosas.

## Contrato de transacciones para módulos

`platform.TenantTransactions.execute(UUID, Supplier<T>)` es el único puerto aplicativo añadido. Implementación JDBC/TransactionTemplate interna. Usa la misma conexión ligada al DataSource por Spring; comprueba transacción real, conexión no-autocommit y contexto posterior al callback. No usa un ThreadLocal de tenant ni headers/MDC como autorización.

```java
// Sólo después de comprobar identidad, membresía, capacidad y recurso en #27/#28.
return tenantTransactions.execute(authorizedTenantId, () -> repository.operation());
```

REQUIRED crea una transacción o participa en la exterior. Anidar el mismo tenant conserva conexión/contexto; otro tenant se rechaza y la operación exterior termina en rollback incluso si se captura el error interno. El contexto de una transacción exterior vive hasta su commit/rollback, no hasta el retorno del callback. Savepoints/NESTED están deshabilitados en el transaction manager para impedir borrar una primera vinculación mediante rollback parcial. No emitir BEGIN/COMMIT/ROLLBACK/SAVEPOINT/SET de sesión manualmente desde repositorios. El puerto no expone REQUIRES_NEW ni opciones de propagación; no envolverlo en transacciones independientes para cambiar despacho dentro de una operación.

La vinculación requiere una escritura de metadatos: una transacción exterior read-only se rechaza. Para consultas tenant-scoped usar este puerto con transacción read-write, aunque la consulta de negocio sea SELECT. Evaluar el costo de metadatos con carga real antes del piloto; no fingir SLO a partir de los tests.

`set_config(..., true)` equivale a SET LOCAL parametrizado: commit y rollback restauran la configuración. Incluso un SET de sesión ajeno no concede contexto en la siguiente transacción porque el vínculo/xid no coincide. Repositorios usan JdbcTemplate/DataSourceUtils, nunca una conexión nueva sin contexto. Reintentos necesitan una transacción nueva autorizada; no reusar un contexto de una transacción abortada. Excepciones de contexto tienen texto fijo, sin UUID/SQL/driver; RFC 9457 conserva el error público seguro. Diagnóstico operacional `TENANT_CONTEXT_REJECTED`; sin tenant en logs, métricas o spans.

## Límites y decisiones pendientes

- #26 implementa infraestructura en platform. El futuro módulo tenants gestionará provisioning/configuración; identity resolverá sujetos y membresías. No se crean módulos vacíos ni dependencias circulares.
- El issue original menciona memberships. OQ-014/022 y #27 siguen abiertos: se difiere su tabla, FK de identidad, estado y bootstrap hasta definir identidad global/OIDC. Una membresía sin identidad sería un registro ficticio sin autorización verificable. Esta decisión limitada se somete a revisión en el Draft; no acepta unilateralmente el modelo v0.1 ni resuelve las OQ.
- Un UUID válido no demuestra autorización. No hay adaptador HTTP de tenancy ni selección por header/query/body. Sólo pruebas y futuros adaptadores internos explícitos podrán llamar el puerto, después de autorización servidor.
- RLS protege frente a consultas que omiten accidentalmente el filtro tenant. No es un sandbox frente a ejecución arbitraria de código/SQL con las credenciales runtime: al iniciar una transacción ese rol puede establecer un tenant. La selección confiable de #27/#28 sigue siendo indispensable. El migrador/DBA puede alterar DDL; proteger esas credenciales y separar su uso de runtime.
- No hay users, memberships, capacidades, sesiones, bootstrap de identidad, taxpayer ni tablas fiscales, auditoría/outbox o workers. #27/#28/#29/#30 y #74 conservan sus gates.

## Pruebas y recuperación

`TenantRlsTests`: rol runtime real, ausencia/forja de contexto, mismo RFC sintético en A/B, CRUD cruzado, WITH CHECK, FKs/índices/uniqueness, mismo PID físico con pool de tamaño 1, commit/rollback/excepción, nesting, read-only y guardas. Mutaciones deliberadas de RLS en el schema **de prueba** deben hacer fallar el oráculo de aislamiento; alterar FORCE/grants en la raíz debe disparar la guarda. Se restaura cada mutación en finally. No se ejecutan migraciones de prueba en producción.

`TenantConcurrencyTests`: cuatro conexiones realmente simultáneas y 32 operaciones con commit/excepción/reutilización, sin bloqueo de limpieza ni herencia de contexto. `TenantHttpPrivacyTests`: headers/query/body no establecen tenant y rechazo interno conserva Problem Details/log seguro. `TenantMigrationIT`: upgrade V1, checksum V2, rollback atómico y reintento de DDL fallido. Los fixtures se aprovisionan con migrador y contexto, respetando FORCE; todas las aserciones de aislamiento usan runtime. Sin mocks ni bypasses para los datos.

Los fixtures `rls_fixture.records/links` viven exclusivamente en recursos de tests y PostgreSQL desechable. PK/FK `(tenant_id,id)`, índice `(tenant_id,record_id)` y RFC explícitamente DEMO; no son contribuyentes ni un módulo fiscal de producción.

Para desarrollo, iniciar Compose y backend con los comandos existentes: Flyway aplica V2. Reiniciar/recrear sólo el contenedor local para aplicar opciones de logging nuevas, preservando el volumen; el script de roles no necesita repetirse en una base V1. No resetear datos para ocultar un fallo. Fallo de migración: revisar SQL fuente/historial en entorno sintético; no editar V1/V2 ya aplicadas, reparar mediante migración nueva revisada. Sin downgrade destructivo automático: restauración respaldada/probada antes de entornos reales.

PostgreSQL local/Testcontainers usa log_min_messages=LOG y error verbosity terse, sin sentencias/parámetros. Se suprimen ERROR/DETAIL con valores rechazados y se conservan LOG de arranque/FATAL/PANIC; a cambio se pierde detalle SQL de servidor, disponible como diagnóstico aplicativo seguro. No habilitar log_statement/slow-query/audit logs con datos privados sin una política revisada. La prueba canario provoca un error de conversión y verifica los logs reales del contenedor; runtime no puede elevar log_statement. Logs de fallos catastróficos y administración requieren custodia restringida, nunca artifacts públicos.

Referencias primarias: [RLS y privilegios/limitaciones](https://www.postgresql.org/docs/18/ddl-rowsecurity.html), [SET LOCAL y savepoints](https://www.postgresql.org/docs/18/sql-set.html), [funciones SECURITY DEFINER](https://www.postgresql.org/docs/18/sql-createfunction.html), [logging PostgreSQL](https://www.postgresql.org/docs/18/runtime-config-logging.html). Referencia técnica contrastada: [contexto TrailerSaaS](https://github.com/Dagt/TrailerSaas/blob/fa0898e12cbdc358ea7cd7119c76a1e3a11e248c/backend/src/main/java/com/trailersaas/identity/internal/PostgresRlsContext.java) y sus tests TenantRlsPostgresTests. Se adoptan conexión ligada, transacción real y tests de pool; se amplían FORCE/cerca/vínculo inmutable. No se copian identidad bootstrap, JPA ni modelo logístico.

## Entitlements técnicos #32

[Modelo, migración V8, reservas, reglas de consumo y límites de aprobación](../architecture/TENANT-ENTITLEMENTS.md). Sin precios, cobros, grants implícitos ni módulos fiscales. La evidencia vigente corresponde al SHA del PR #32; resultados de PRs anteriores no lo acreditan.


V9 — política limitada aprobada: [referencia del propietario](https://github.com/Dagt/Fiscuvia/pull/90#issuecomment-6798951955). TENANT_PLAN_VIEW sólo owner ACTIVE + MFA ≤10 minutos; trigger productivo de cupos en INSERT/UPDATE ACTIVE, sin cuotas comerciales por defecto. La aceptación conserva el flujo original y confirma reservas sin duplicar consumo; reactivaciones registran journal/outbox. [Detalles y límites](../architecture/TENANT-ENTITLEMENTS.md). No resuelve OQ-007/014/015, ADR-015 completa ni #74.
