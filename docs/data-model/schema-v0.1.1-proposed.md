# Esquema v0.1.1 — enmienda propuesta SPEC01

**Proposed, 2026-11-12; Refs #74. No es DDL ni autorización Flyway.** Complementa y corrige [v0.1](schema-v0.1.md) sólo tras aceptar explícitamente el corte correspondiente. [Decisiones D74](../product/SPEC01-decisions-74.md), [contrato #34](../api/taxpayers-34-specification.md).

## Convenciones y esquema efectivo

Conservar V1–V11 y sus checksums. Raíz existente `app.tenants(tenant_id)`, perfiles `tenant_private.profiles`, usuarios globales `identity_private.users(id)`, miembros `identity.memberships(tenant_id,id)`, roles `identity.membership_roles`. Ninguna FK futura usa tenants.id ni una tabla users nueva. Roles globales de IdP y RFC no delimitan autorización.

Las tablas nuevas de dominio usan PK **(tenant_id,id)**, UUID interno generado por servidor, FK tenant→app.tenants; para referencias que además deben coincidir en contribuyente, añadir UNIQUE completo (tenant_id,taxpayer_id,id) y usar las tres columnas. No depender de un índice parcial como destino FK. ON DELETE RESTRICT; sin DELETE/TRUNCATE runtime. tenant/id y filiación del recurso son inmutables. No usar CASCADE para borrar evidencia/historial.

Fechas civiles date, instantes timestamptz UTC. Intervalos [desde,hasta) con hasta NULL = abierto; fecha inicial NULL sólo donde se define “desconocida”, nunca sustituirla por incorporación. Versiones bigint 0..9007199254740991, avance atómico +1, error antes de overflow. VARCHAR+CHECK de estados, no ordinal JPA. NOT NULL explícito: CHECK por sí solo no rechaza NULL. No CHECK con subquery a otra tabla: usar FK/unique o función/trigger estrecho bajo lock para invariantes entre filas. Véanse [constraints PostgreSQL18](https://www.postgresql.org/docs/18/ddl-constraints.html).

## Corte #34: tablas propuestas

Namespace lógico `taxpayers` para datos protegidos y `taxpayer_private` para recibos; nombres a fijar en migración aditiva después de aprobación. Sin cambios de tablas actuales en este PR.

### taxpayers.taxpayers

| Columna | Tipo / null | Restricción / significado |
|---|---|---|
| tenant_id, id | uuid NOT NULL | PK compuesta; tenant FK a raíz real |
| rfc_normalized | varchar(13) NOT NULL | Normalización y patrón por persona; UNIQUE(tenant_id,rfc_normalized), **incluye archivados** |
| person_type | varchar NOT NULL | INDIVIDUAL / LEGAL_ENTITY; longitud RFC coherente; inmutable en #34 |
| legal_name | varchar(300) NOT NULL | trim, 1..300 puntos de código, sin controles; no se infiere del RFC |
| trade_name | varchar(200) NULL | NULL o trim no vacío 1..200, sin controles |
| status | varchar NOT NULL | ACTIVE / ARCHIVED; no estado de inscripción SAT |
| version | bigint NOT NULL | Default0 y rango seguro; CAS por expectedVersion |
| created_at, updated_at | timestamptz NOT NULL | Incorporación y última modificación del expediente, generadas por DB |
| created_by_membership_id, updated_by_membership_id | uuid NOT NULL | FK (tenant_id,membership_id)→identity.memberships; no borrar al revocar |
| archived_at, archived_by_membership_id | timestamptz / uuid NULL | Ambos NULL si ACTIVE, ambos presentes si ARCHIVED, miembro mismo tenant |

RFC/personType inmutables en #34. Una corrección real de identidad requiere diseño posterior, motivo y experto cuando corresponda; no crear duplicado para evitar esa revisión. Restore conserva UUID, createdAt e historial, limpia metadata de archivo en proyección y agrega nueva revisión. No hay primary_owner_membership_id ni régimen singular duplicados en esta tabla.

Índices: PK tenant/id, unique tenant/RFC; (tenant_id,status,legal_name,id) para directorio; búsquedas literales acotadas pueden hacer scan dentro del tenant autorizado. Sin pg_trgm/índice PII global hasta medir necesidad; orden secundario por id, filtros siempre anteriores a paginación.

### taxpayers.taxpayer_revisions

`tenant_id uuid, taxpayer_id uuid, version bigint, command_id uuid, changed_at timestamptz, actor_membership_id uuid, change_kind varchar, reason_code varchar NULL`, más snapshot completo tipado de rfc/personType/legalName/tradeName/status y metadata administrativa del expediente. PK (tenant_id,taxpayer_id,version); FK al taxpayer, miembro y **(tenant_id,command_id)→journal.events(tenant_id,command_id)**. change_kind CREATE / UPDATE_PROFILE / ARCHIVE / RESTORE. Una revisión por versión, incluida0. No columnas JSON arbitrarias ni texto libre en el journal.

Historial append-only privado de expediente: contiene PII de negocio y usa las mismas cercas de taxpayer; no log técnico/artifact. UPDATE/DELETE/TRUNCATE rechazados. La fila actual y su revisión deben coincidir al commit mediante trigger diferido estrecho; un comando incompleto revierte. No se publica historial HTTP en #34 ni se habilita AUDIT_VIEW. Retención y exportación OQ-008 permanecen pendientes.

### taxpayers.taxpayer_regime_references

`tenant_id uuid, taxpayer_id uuid, taxpayer_version bigint, ordinal smallint, regime_code varchar(3), valid_from date NULL, valid_to date NULL, source varchar, review_status varchar, recorded_at timestamptz, recorded_by_membership_id uuid`.

PK (tenant_id,taxpayer_id,taxpayer_version,ordinal), FK completa a taxpayer_revisions y al miembro. ordinal0..9; código `[0-9]{3}` **referencia capturada**, sin FK a catálogo SAT inventado. source=MANUAL y review_status=PENDING_REVIEW en #34 fijados por servidor. RecordedAt/autor del nuevo snapshot describen su captura; no falsificar fecha de verificación oficial. Revisión posterior podrá enlazar evidencia y resultado, mediante nuevo contrato aprobado.

Conjunto 0..10 por revisión. Historial de régimen vive en snapshots de cada versión, no se sobrescribe una vigencia anterior. Permite diferentes códigos simultáneos. Para el mismo código rechazar duplicado/solapamiento dentro del conjunto vigente; fechas conocidas requieren valid_to>valid_from; valid_from NULL exige valid_to NULL y representa vigencia desconocida. Una entrada desconocida del mismo código no coexiste con otra entrada de ese código. Comprobación de conjunto en función de mutación con lock del taxpayer; no un CHECK entre filas ni extensión btree_gist prematura. Las revisiones antiguas pueden diferir por corrección: representan lo que se capturó, no una verdad fiscal retroactiva. Fin exclusivo es convención del contrato, no inferencia sobre legislación.

### taxpayers.taxpayer_assignments

`tenant_id uuid, id uuid, taxpayer_id uuid, membership_id uuid, assignment_type varchar, started_at timestamptz, ended_at timestamptz NULL, created_command_id uuid, ended_command_id uuid NULL`.

PK tenant/id; UNIQUE tenant/taxpayer/id; FK compuestas a taxpayer y membership; FK tenant/command al journal. CHECK ended_at>started_at y coherencia de ended_command. Tipo PRIMARY / CONTRIBUTOR / REVIEWER propuesto para #36; **#34 sólo crea PRIMARY para el creador** según D74-03. Horas UTC de efectividad administrativa, sin asignaciones programadas ni automatizador en MVP. Datos de rol no duplicados: supervisor/contador/auxiliar provienen de membership_roles actual.

UNIQUE parcial (tenant_id,taxpayer_id) WHERE ended_at IS NULL AND assignment_type='PRIMARY'; UNIQUE parcial (tenant_id,taxpayer_id,membership_id,assignment_type) WHERE ended_at IS NULL. No solapamientos de períodos del mismo miembro/tipo: comando de reasignación #36 bloquea taxpayer y conserva inicio/fin, sin backdating. Archivo de taxpayer no cierra atribuciones ni concede acceso; deja de ser operable por reglas de estado. Invariante de #34: exactamente un PRIMARY inicial; cambio futuro permite intervalo sin responsable sólo por política aprobada, no al desactivar silenciosamente un miembro.

Índices (tenant_id,membership_id,ended_at,taxpayer_id) y (tenant_id,taxpayer_id,started_at,id). Asignación funcional y grants de acceso son conceptos distintos.

### taxpayer_private.commands

`tenant_id uuid, command_id uuid, actor_membership_id uuid, operation varchar, target_id uuid, fingerprint bytea(32), result_version bigint, event_id uuid, recorded_at timestamptz`.

PK tenant/command; FK tenant/target→taxpayers, tenant/member→memberships, tenant/event→journal.events; vincular command al mismo evento mediante FK tenant/command y validación de coherencia. Fingerprint SHA256 de argumentos canónicos, scope actor/operación/target/contenido/expectedVersion. Guarda sólo recibo estable, no respuesta fiscal ni token. Force RLS y sin SELECT/DML runtime/sesiones; funciones estrechas de comando. No purge/TTL automático antes de política de retención; garantía de replay mientras el recibo se conserve. Límite de solicitud/input evita tamaño arbitrario.

## Cambios futuros necesarios en infraestructura (no ejecutados)

1. **Integridad de grants:** añadir a access_control.resource_grants una columna generada STORED `taxpayer_id`, igual a resource_id sólo si resource_kind=TAXPAYER, NULL para otros tipos. FK (tenant_id,taxpayer_id)→taxpayers.taxpayers(tenant_id,id), MATCH SIMPLE, ON DELETE RESTRICT; CHECK/inmutabilidad impiden retargeting. El PK existente sigue (tenant_id,membership_id,resource_kind,capability,resource_id); no nuevo registro genérico sin FK. Durante upgrade revisar grants previos de esa categoría: en base limpia no hay productivos; si hay huérfanos, fallar y pedir remediación administrativa, no eliminarlos ni conceder acceso automáticamente.
2. **Políticas/códigos:** después de D74-03, añadir TAXPAYER_ARCHIVE a allowlists Java/SQL/observabilidad/journal de forma aditiva, código por recurso, sin wildcard. Política CREATE usa código existente/TENANT; VIEW/EDIT/ARCHIVE/ASSIGN usan TAXPAYER. Añadir producers de eventos y startup guards. No tocar los textos de V1–V11 ni resembrar aprobaciones parciales.
3. **Alta acotada:** función SECURITY DEFINER verifica contexto exacto CREATE/TENANT y actor/membresía/MFA, genera ID, valida RFC sin enumerar otros expedientes, mide cuota, inserta perfil+snapshot+régimen+PRIMARY+grants exactos del creador+evento/outbox+recibo en un commit. No acceso runtime directo a grants. No llamar un segundo AuthorizedTenantOperations de otra capacidad dentro de ese callback: contexto xid es inmutable. Retornar recibo y hacer GET nuevo para lectura normal.
4. **Capacidad:** TAXPAYERS no puede usar `entitlement_private.reservations.reference_id` porque tiene FK a identity_private.users y amount=1/semántica USERS. Proponer función estrecha de medición/comprobación TAXPAYERS sobre COUNT status=ACTIVE, lock de la fila limits(tenant,TAXPAYERS) y mutación en esa misma transacción. Operación síncrona no necesita reserva durable nueva. Extender Capacity mediante adaptador aprobado si se usa, sin aceptar MEMBERSHIP_INVITE como autoridad fiscal. Concurrencia/limit/view requieren tests propios; nunca presentar NOT_INTEGRATED como cero.

Para materializar el alta sin conceder lectura, proponer `taxpayer_private.creation_context(transaction_id xid8 PK, tenant_id uuid, taxpayer_id uuid, membership_id uuid)`: metadata privada y efímera, sin acceso runtime/sesiones, efectiva sólo en su xid y contexto CREATE/TENANT verificado. Se inserta exclusivamente dentro de la función de alta con UUID generado por DB, nunca por parámetro HTTP. Su scope sólo habilita INSERT de ese taxpayer y sus hijos/bootstrap; no SELECT de perfiles existentes ni UPDATE/DELETE. Limpiar registros antiguos en lotes SKIP LOCKED como los contextos actuales; una fila persistente después de commit no concede autoridad. No FK al taxpayer aún inexistente: es metadata de contexto, no historial ni grant. Pruebas de scope falso/caducado/tenant distinto son obligatorias.

## RLS y frontera de mutación

Para todas las tablas de expediente: ENABLE/FORCE RLS, tenant_fence RESTRICTIVE con `tenant_id=(SELECT app.current_tenant_id())`; resource_fence RESTRICTIVE exige `access_control.resource_allowed(tenant,'TAXPAYER',taxpayerId,capacidad fija del comando)`. En taxpayers el recurso es id; en hijos es taxpayer_id. No usar sólo current_setting ni condición OR con TENANT_READ. [RLS PostgreSQL18](https://www.postgresql.org/docs/18/ddl-rowsecurity.html).

Runtime recibe sólo SELECT de tablas/vistas protegidas para VIEW; mutaciones mediante funciones específicas, sin DML directo ni acceso privado. Dentro de función definer de EDIT/ARCHIVE, políticas migrador siguen FORCE y exigen contexto/capacidad de ese comando para leer/bloquear/modificar; VIEW no satisface UPDATE. Permisivas por comando además de las cercas restrictivas: la cerca sola no concede acceso. CREATE es excepción de inserción explícita: política específica de alta comprueba contexto CREATE/TENANT y comando/ID recién generado en creation_context; no una cerca `true` general que abra lectura de cartera. Hacer test de SQL runtime sin WHERE y del definer fuera de contexto. Funciones con search_path pg_catalog,pg_temp, nombres calificados, SQL fijo, sin EXECUTE PUBLIC/sesiones ni BYPASSRLS.

La medición de cuota requiere contar **todos** los ACTIVE del tenant, no sólo cartera visible. Es otra operación interna específica: función privada definer sin EXECUTE runtime que devuelve únicamente COUNT a la función de cuota/alta o al DTO agregado autorizado TENANT_PLAN_VIEW. Política de lectura migradora tenant-scoped con propósito privado de medición, nunca SELECT de perfiles para un caller CREATE/TENANT. El scope de medición se abre/cierra dentro del helper, ligado al xid, y no admite SQL/target de cliente; falla cerrada sin actor/contexto de cuota autorizado. No aplicar resource_allowed del creador al COUNT porque permitiría exceder cuota ocultando otras carteras. Esta excepción escalar debe revisarse en #34, probar que incluye carteras ajenas del mismo tenant y que no expone nombres/RFC/filas; no conceder SELECT directo ni BYPASSRLS. Auditoría/registros privados no usan esta excepción para leer perfiles.

Orden propuesto de locks: raíz tenant antes de membresías/política/grants (puerto vigente), luego recibo de comando para dedupe; en alta/restauración fila de cuota TAXPAYERS, después recurso/unique RFC; en edición/archivo recurso. Archivo no toma cuota ni necesita otra membresía: una alta concurrente puede contar conservadoramente hasta su commit. #36 cambio de acceso necesita plan propio de locks: raíz UPDATE desde autorización, miembros target en UUID ordenado **antes** de grants/recursos para serializar con #33; no upgrades SHARE→UPDATE tardíos. Operaciones largas/lotes deben acotarse. Tenant distinto o cambio de capacidad en la transacción se rechaza.

## Esquema preparado para módulos posteriores (no crear con #34)

Todas las filas siguientes llevan tenant_id, UUID y PK tenant/id; FKs compuestas y referencias triple donde corresponde. Estados/catálogos dependen de OQ del módulo; este mapa fija relaciones, no enums fiscales finales.

| Entidad | Claves, referencias e invariantes propuestas |
|---|---|
| obligation_templates | Identidad estable tenant/code UNIQUE; no version mutable como PK de identidad |
| obligation_template_versions | PK tenant/template_id/version; políticas tipadas y validadas, snapshot/fuente/aprobador; versión publicada inmutable; no JSON ejecutable libre |
| taxpayer_obligation_configs | tenant/taxpayer/id UNIQUE; taxpayer FK; identidad estable de seguimiento, no crear otra para cada cambio |
| obligation_config_versions | PK tenant/config_id/version; FK a plantilla versión exacta, vigencia [from,to), responsable membership tenant-safe; nuevas versiones no reescriben ocurrencias |
| obligation_occurrences | UNIQUE tenant/config_id/period_start/period_end, sin versión en dedupe; UNIQUE tenant/taxpayer/id; FK completa a config+taxpayer, config_version y template_version; period_end>start; due_on date con fuente/jurisdicción/regla versión o captura manual; snapshot de política, versión de concurrencia |
| occurrence_exceptions | FK tenant/taxpayer/occurrence; fecha anterior/nueva, causa, actor, fuente, regla aprobada; apéndice, no sobrescribir plazo histórico |
| obligation_reviews | UNIQUE tenant/occurrence/attempt; FK triple a ocurrencia; autor/revisor membership; evidencia versión exacta; intentos y decisiones conservados |
| filing_records | FK triple a ocurrencia; UNIQUE tenant/occurrence/sequence; vínculo a filing anterior del **mismo taxpayer/occurrence** mediante clave completa; filing_kind sujeto OQ-004; fecha/fuente/actor; sin booleano satVerified editable |
| tax_payment_records | FK triple a ocurrencia y, si existe filing, FK tenant/taxpayer/occurrence/filing; importe decimal+moneda, evidencia exacta; reversión referencia pago de la misma ocurrencia; nunca FK a fee_charge |
| tasks / task_events | taxpayer opcional sólo para tarea administrativa tenant-wide con política específica; si occurrence existe taxpayer obligatorio y FK triple; sin ese permiso nuevo sólo tareas de expediente. Asignado membership tenant-safe; eventos append-only, versión y recurrencia por serie/período |
| taxpayer_contacts | FK taxpayer; contacto comercial no OIDC; datos/versionado y dedupe por contribuyente tras OQ-013, sin emails globalmente únicos |
| certificate_expiries | FK taxpayer; cert_type, expires_on, source y capturedAt/actor/review; cambio como historial; sin .key/.cer/password/blob en este modelo |

Cambios de configuración dentro de un período y obligaciones de mismo tipo en contextos distintos requieren clave de contexto explícita/contador antes de #39/#40; no esconder el conflicto creando dos config_id para una misma obligación. Complementarias crean filing_records, no una ocurrencia duplicada para el mismo período. Generación automática bloqueada sin autoridad de servicio aprobada: el transporte de outbox no tiene ese poder.

## Documentos: versión física y vínculos verificables

`documents` es metadata lógica (tenant,id,taxpayer_id,classification,status,version,createdBy); `document_versions` contiene (tenant,document_id,revision) PK, taxpayer_id, object_id FK y procedencia/source/capturedAt/actor; `stored_objects` conserva object_key opaco único, checksum, tamaño, MIME, estado de escaneo y timestamps. Binaries sólo object storage privado. Nunca derivar object_key del RFC/nombre. La clasificación/permisos no residen en el bucket ni nombre del archivo. Reemplazo produce nueva versión, no cambia la evidencia referenciada.

Enlaces concretos `taxpayer_document_links`, `occurrence_document_links`, `filing_document_links`, `tax_payment_document_links`, `task_document_links`: cada uno FK tenant/taxpayer/document/version y FK tenant/taxpayer/target_id (más occurrence_id si hace falta); añadir UNIQUE completos en destinos para hacer las FK realizables. Prohibido target_type+target_id sin integridad. Una evidencia tiene que apuntar a versión AVAILABLE del mismo contribuyente; un trigger/función estrecha valida estado bajo lock, la FK valida pertenencia. Archivo posterior no destruye una evidencia ni blob referenciado; conservación OQ-008. Transferir documento entre contribuyentes requiere flujo específico, no UPDATE taxpayer_id.

La lectura/descarga requiere permisos del documento y del contexto de expediente autorizado mediante función específica, sin cambiar de target/capacidad en el mismo xid; definir integración de categorías en #46. No basta autorizar taxpayer para descargar cualquier documento. MANUAL_UPLOAD y EXTERNAL_IMPORT son orígenes distintos; captura de CSF no demuestra vigencia SAT ni habilita integración oficial. Ninguna tabla/documento físico se crea aquí.

## Honorarios: cuenta por cobrar separada

`fee_agreements` (taxpayer, periodicidad/vigencia, versión), `fee_charges` (taxpayer, agreement/version opcional, período, due_on, importe/moneda/estado), `fee_payments` (**taxpayer obligatorio**, fecha, importe, moneda, método, versión inmutable), `fee_allocations` y `fee_adjustments`.

Charge UNIQUE tenant/agreement/period para recurrencia; extraordinario sin agreement requiere command único. Allocations FK tenant/taxpayer/payment y tenant/taxpayer/charge, evitando aplicar pago de un cliente al de otro del mismo despacho. Monto positivo decimal exacto, misma moneda; sumas limitadas por pago y saldo bajo locks de payment/charges en orden UUID, no CHECK SUM ni float. Ajustes/reversiones compensatorios con motivo/aprobador, FK al movimiento original del mismo cliente; sin edición/borrado destructivo. Saldo = cargos+ajustes−aplicaciones netas, parcialidad/adeudo y atraso derivados; cancelación exige política de reversión si hay aplicaciones. OQ-011 valida escala/redondeo/impuestos del servicio antes de fijar numeric definitivo.

No FK compartida entre fee_payments y tax_payment_records ni ledger monetario genérico; no cobranza electrónica, PAC, facturación o cálculo de impuesto en este PR.

## Plan de migración posterior y verificación

Después de aprobación, #34 añade sólo tablas del corte, FK real de grants, políticas exactas/eventos, funciones/guards y medición TAXPAYERS. Crear namespace/tablas/destinos unique antes de FKs y funciones; asegurar ciclos de snapshot/journal con constraints diferibles sólo donde haga falta. Upgrade desde V11 y clean install deben probarse en PostgreSQL real; verificar V1–V11 blob/checksum intactos, huérfanos, rollback DDL, reejecución única y privilegios runtime/sesiones. No asignar número V12 hasta inspeccionar latest main; sin migración fiscal en #74.
