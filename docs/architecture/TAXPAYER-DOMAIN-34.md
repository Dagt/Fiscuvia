# C01 #34 — contribuyentes, API y frontera de seguridad

Estado de esta rama: implementación del alcance inicial autorizado por Dagt en [D74-01..05](../decisions/issue-74-owner-approval.md), después del merge humano de #92. Base exacta `4c748a833a1ca2f0deef5c6fbd7702c0f66996cd`. La aceptación limitada no aprueba las restantes OQ fiscales. [Validación y límites de evidencia](../delivery/VALIDATION-34.md).

Trazabilidad: FR-004/007/009/012..017 (contactos y gestión completa de responsables diferidos a #36), BR-001..004/010..014, NFR-001..009/010/013/015/020/021/026/030..036/040/044. Se conserva el estado de cada ADR; implementar este corte no acepta íntegramente ADR Proposed ni declara todos esos FR completos.

## Corte implementado

Módulo Spring Modulith `taxpayers`, con dependencias públicas de identidad, auditoría y plataforma. Reutiliza `AuthorizedTenantOperations`, el vínculo HMAC de actor/contexto por transacción, `access_control.authorize_operation`, RLS, journal/outbox, errores RFC 9457 y los límites existentes. No introduce autenticación, contador de uso, auditoría ni autorización paralelos.

La migración aditiva **V12__taxpayer_domain_and_secure_apis.sql** conserva V1–V11 byte a byte. Crea los agregados fiscales autorizados y extiende las funciones y allowlists existentes. No hay datos fiscales reales, secretos SAT, catálogo normativo, integración SAT, calendario, cobros, documentos, obligaciones, exportaciones ni worker automático.

## Modelo físico efectivo

El DDL V12 es la fuente física. [v0.1.1](../data-model/schema-v0.1.1-proposed.md) conserva la propuesta de módulos posteriores. No ejecutar el esquema conceptual como SQL.

| Tabla | Clave e integridad | Contenido y acceso |
|---|---|---|
| `taxpayers.taxpayers` | PK `(tenant_id,id)`; UNIQUE `(tenant_id,rfc_normalized)` vitalicio; FK tenant y membresías compuestas | RFC, persona, nombres, ACTIVE/ARCHIVED, versión, fechas y actores; runtime sólo SELECT con RLS |
| `taxpayers.taxpayer_revisions` | PK `(tenant_id,taxpayer_id,version)`; FK contribuyente, actor y comando journal | Snapshot completo e inmutable por versión, operación y razón cerrada; sin lectura runtime |
| `taxpayers.taxpayer_regime_references` | PK `(tenant_id,taxpayer_id,taxpayer_version,ordinal)`; FK revisión y capturador | Hasta diez referencias por snapshot, código de tres dígitos, fechas opcionales, MANUAL/PENDING_REVIEW y captura; runtime SELECT protegido |
| `taxpayers.taxpayer_assignments` | PK `(tenant_id,id)`; FK contribuyente/membresía/comandos del mismo tenant; UNIQUE parcial de PRIMARY vigente | Sólo relación PRIMARY inicial; historial inmutable, sin API de reasignación |
| `taxpayer_private.grant_reviews` | PK del grant exacto + secuencia; UNIQUE grant/comando; FK grant, recurso, revisor y journal | ENABLE/DISABLE, versión de membresía y rol revisados, actor/fecha; append-only; ninguna API administrativa en #34 |
| `taxpayer_private.commands` | PK `(tenant_id,command_id)`; FK recurso, miembro, evento y comando journal | SHA-256 del contenido canónico, operación, UUID, versión resultante, evento y fecha; no respuesta completa ni perfil |
| `taxpayer_private.operation_scope` | PK xid8, accesible sólo a funciones internas del migrador | Alcance transaccional de escritura o medición escalar; no es grant, contador ni autorización persistente |

V12 añade `access_control.resource_grants.taxpayer_id` generado desde `resource_id` únicamente para kind TAXPAYER y su FK compuesta al contribuyente. No permite grants fiscales huérfanos ni hacia otro tenant. Otros tipos de recurso conservan NULL y su comportamiento previo.

Todas las tablas nuevas usan ENABLE y FORCE RLS, propietario migrador no BYPASSRLS y ausencia de DML runtime. Las cuatro tablas de dominio tienen políticas restrictivas independientes de tenant y recurso. Las tablas privadas no conceden USAGE/lectura a runtime ni sesiones; sus políticas protegen el acceso de las funciones SECURITY DEFINER. `operation_scope` es metadato de transacción privado, con limpieza acotada SKIP LOCKED y coincidencia exacta de xid, tenant y recurso.

Los índices incluyen la unicidad RFC por tenant, directorio `(tenant_id,status,legal_name,id)`, PRIMARY abierto único y relación `(tenant_id,membership_id,taxpayer_id)`, además de PK/UNIQUE. Búsqueda literal con ILIKE y parámetros, sin índices de texto prematuros. Evaluar EXPLAIN con volumen representativo antes de añadir índices; no se afirma capacidad de producción bajo carga sostenida.

UUID, tenant, RFC, persona y metadatos de creación son inmutables. Un trigger exige avance exacto de versión +1 y otro diferido exige snapshot coincidente y responsable inicial antes del commit. No hay DELETE/TRUNCATE de contribuyentes ni UPDATE/DELETE/TRUNCATE de historia/recibos/revisiones. Las versiones son enteros 0..9007199254740991. Fechas de captura son timestamptz, vigencias civiles date y los intervalos son `[desde,hasta)`.

## RFC y regímenes

La validación local recorta sólo espacios ASCII exteriores, aplica NFC y mayúsculas ROOT. Conserva Ñ y &, rechaza caracteres ajenos al alfabeto aprobado, controles, separadores, homoglifos y fechas imposibles. Persona física: 13 caracteres y cuatro iniciales; moral: 12 y tres iniciales. YYMMDD debe ser realizable en 1900+YY o 2000+YY; evaluar 2000+YY cubre esa unión sin deducir siglo. Sólo se excluyen por igualdad normalizada XAXX010101000/XEXX010101000; no sus prefijos. No se verifica homoclave, checksum, identidad o inscripción SAT.

La respuesta declara `RFC_LOCAL_STRUCTURE_V1` y `NOT_PERFORMED` para verificación oficial. Un RFC puede existir en distintos despachos y sigue reservado en su tenant al archivarse. El conflicto no devuelve UUID, nombres, estado ni datos del expediente existente. La corrección de RFC/persona está fuera de este corte.

Regímenes: código manual de tres dígitos, sin catálogo SAT oficial. Fechas ambas NULL representan vigencia desconocida; término sin inicio se rechaza; término debe ser posterior al inicio. Se aceptan códigos diferentes simultáneos y tramos contiguos del mismo código; se rechazan duplicados/solapamientos de un mismo código, incluida vigencia desconocida. El comando valida el conjunto completo y el trigger protege la inserción de solapamientos dentro de un snapshot. El runtime no puede insertar ni eludir la serialización del agregado. MANUAL y PENDING_REVIEW se fijan en servidor y no se aceptan como campos de entrada. Cada versión conserva el conjunto completo y sus metadatos de captura; no hay inferencia de obligaciones.

## Política efectiva aprobada

Siempre se requieren identidad, tenant y membresía ACTIVE y contexto de operación vigente. Roles del proveedor OIDC, parámetros HTTP o pertenencia al despacho no conceden cartera.

| Operación | Roles autorizados | Recurso y condiciones |
|---|---|---|
| CREATE | FIRM_OWNER_ADMIN | TAXPAYER_CREATE sobre TENANT, MFA confiable de máximo diez minutos, límite vigente y plaza disponible |
| VIEW/listado | FIRM_OWNER_ADMIN, FIRM_SUPERVISOR, ACCOUNTANT | TAXPAYER_VIEW por UUID exacto y revisión efectiva vigente; colección sin grants devuelve página vacía |
| EDIT | Los mismos tres | TAXPAYER_EDIT exacto y revisión vigente; sólo ACTIVE; expectedVersion |
| ARCHIVE/RESTORE | FIRM_OWNER_ADMIN | TAXPAYER_ARCHIVE exacto y revisión vigente, MFA reciente, versión/estado; restore además capacidad disponible |
| ASSIGN | Ninguno operativo en #34 | Grant inicial reservado para #36, sin role_capability, habilitación ni endpoint |

ASSISTANT permanece denegado, incluso si hay grant histórico. VIEW no implica EDIT y EDIT no implica VIEW. No hay owner omnipotente ni supervisor con cartera global.

CREATE registra PRIMARY al miembro creador, cuatro grants exactos VIEW/EDIT/ARCHIVE/ASSIGN y tres revisiones ENABLE para las operaciones efectivas. Todo es parte de la misma transacción. El bootstrap no delega acceso ni constituye una API genérica de grants.

Para cada grant se toma **la última revisión por secuencia**, que debe ser ENABLE y coincidir con `identity.memberships.version` y el único rol interno actual. Se comprueba al congelar el conjunto autorizado en `authorize_operation` y al acceder a filas en `resource_allowed`. Grants activos sin revisión, DISABLE posterior o revisión obsoleta no son efectivos. Un cambio de rol, suspensión/reactivación o recuperación del rol anterior mediante #33 incrementa la versión de membresía y no recupera acceso. Revocar no borra historia. Pertenecer a varios despachos no comparte grants ni habilitaciones. La revisión/recuperación manual y sus decisiones permanecen para #36.

Se conserva la serialización existente: raíz del despacho, membresía/roles/políticas y grants quedan bloqueados durante la operación autorizada. Las modificaciones administrativas #33 toman la raíz en UPDATE y esperan operaciones fiscales previas; las operaciones posteriores revalidan la versión nueva. Una revocación de grant espera su SHARE lock y las solicitudes siguientes quedan denegadas. El conjunto de UUID se congela por xid: insertar grants concurrentemente no amplía una operación iniciada.

`taxpayers.approved_policy()` y el guard de arranque verifican la matriz de ocho políticas, MFA, ausencia de ASSIGN operativo y políticas fiscales adicionales, privilegios, tablas FORCE RLS, fences, FK y triggers. No es una garantía contra un DBA malicioso con poder de modificar todas las funciones; las pruebas de filas reales complementan el catálogo.

## Estados, cuotas y transacciones

CREATE → ACTIVE v0; EDIT conserva ACTIVE y aumenta versión; ARCHIVE ACTIVE → ARCHIVED; RESTORE ARCHIVED → ACTIVE sobre el mismo UUID. Archivado no borra historia, grants ni PRIMARY. Editar archivado, repetir transición con clave nueva o sustituir el perfil por uno idéntico produce 409. Una clave previa exacta devuelve su recibo original tras autorización fresca, aun si el estado/version actual ya cambió; no ejecuta otra transición.

Sólo ACTIVE consume TAXPAYERS. CREATE y RESTORE bloquean la fila existente de `entitlement_private.limits` en FOR UPDATE y cuentan todas las filas ACTIVE del tenant mediante función interna estrecha. Cuenta uso real, aunque el owner no tenga VIEW de toda la cartera. Límite inexistente, no vigente o agotado deniega el incremento. ARCHIVE libera capacidad sin reserva/counter adicional; lectura/edición siguen disponibles a autorizados aunque falte capacidad. USERS conserva sus reservas y reglas previas. `entitlement_api.usage` informa TAXPAYERS como MEASURED, reserved=0; recursos posteriores siguen NOT_INTEGRATED. El cliente existente de límites acepta esta integración.

`taxpayers.command` es la única función de escritura runtime. Exige actor y contexto de capacidad/UUID exacto, comando journal con prueba, acción cerrada y validación completa. Serializa la clave mediante advisory lock tenant/comando; usa row lock y expectedVersion sobre el agregado; inserta recibo al final. Fingerprint SHA-256 incluye actor, tenant, acción, target, versión y contenido normalizado (regímenes ordenados). Misma clave/contenido devuelve UUID y versión originales; distinto contenido/actor/acción produce conflicto. Un replay nunca revisa ni reactiva permisos, duplica evento ni consume otra plaza.

Cambios de agregado, revisión/regímenes, PRIMARY, grants/revisiones iniciales, evento tipado, outbox y recibo se confirman o revierten juntos. Eventos: TAXPAYER_CREATED, TAXPAYER_PROFILE_UPDATED, TAXPAYER_ARCHIVED, TAXPAYER_RESTORED. Sólo referencias/códigos necesarios en journal; perfil y RFC permanecen en las tablas protegidas. Outbox conserva el contrato existente de referencia a evento, sin efectos fiscales automáticos.

## HTTP implementado y consumo posterior

Fuente generadora: [openapi-v1.yaml](../api/openapi-v1.yaml). El archivo `openapi-taxpayers-34-proposed.yaml` es antecedente histórico del alcance aprobado y no genera clientes. Tipos generados en `web/src/shared/api/schema.d.ts`; la UI #35 debe consumir los contratos reales, sin simular permisos ni añadir validación SAT.

| Método y ruta bajo `/api/v1/tenants/{tenantId}` | Resultado |
|---|---|
| GET `/taxpayers` | TaxpayerPage `items,page,size,hasNext`, sin total global |
| POST `/taxpayers` | 201 en alta, 200 replay; CommandReceipt y Location del UUID |
| GET `/taxpayers/{taxpayerId}` | Taxpayer con perfil actual y referencias manuales |
| PATCH `/taxpayers/{taxpayerId}` | Sustitución de legalName/tradeName/regimes, expectedVersion obligatorio; recibo |
| POST `/taxpayers/{taxpayerId}/archivals` | expectedVersion y reasonCode SERVICE_ENDED/DUPLICATE_REVIEW/OTHER |
| POST `/taxpayers/{taxpayerId}/restorations` | expectedVersion y reasonCode SERVICE_RESUMED/CORRECTION |

Sesión BFF cookie, CSRF existente en mutaciones, Idempotency-Key UUID, correlation ID de servidor y Cache-Control no-store. Cuerpos cerrados: campos desconocidos/protegidos, JSON inválido o propiedades nullable requeridas ausentes →400; propiedades presentes que incumplen validación →422. `tradeName`, `validFrom` y `validTo` se envían explícitamente como NULL si no se conocen. Límite real de 65536 bytes antes de deserializar, tras filtros de seguridad. legalName admite hasta600 unidades UTF-16 de entrada y valida máximo300 puntos de código después de NFC/trim; tradeName NULL o máximo200 normalizados. Los esquemas de entrada distinguen estos límites de los nombres canónicos de salida. No hay PATCH libre de estado ni campo de capacidad/actor/tenant en cuerpo.

Listado: page0..10000, size1..100 (20 por defecto), status ACTIVE por defecto o ARCHIVED, sort id/legalName/createdAt asc/desc con UUID de desempate; búsqueda literal max80 sin controles sobre nombre/RFC. Autorizar/RLS antes de búsqueda, orden y LIMIT/OFFSET. Parámetros vinculados y sort de allowlist, sin SQL del usuario. Error de paginación400, filtros semánticos inválidos422.

Errores estables existentes:401 sesión;403 operación de colección no permitida/CSRF;404 item ajeno, no asignado, inexistente o capacidad/MFA de item no válida;409 duplicado/estado/versión/cuota/idempotencia;413 tamaño;415 media type;422 validación;500 fallo interno. No se expone SQL ni detalle de constraint. QUOTA_UNAVAILABLE conserva código/status y usa texto común a membresías/contribuyentes. Las denegaciones de recurso no permiten enumerar existencia.

## Entrega y diferimientos

Implementado en esta rama: modelo, API, validación local, RLS, revisión fiscal versionada, bootstrap, cuota, auditoría, idempotencia y contratos. Aprobado pendiente: UI de directorio #35 y evolución posterior dentro de su alcance. Propuesto/no aprobado: resto del esquema de obligaciones, documentos, honorarios y matriz fiscal; las OQ no se cierran automáticamente. Bloqueado sin decisión específica: catálogo y plazos normativos, reglas automáticas, autoridad fiscal de sistema, proveedor/MFA productivos y custodia SAT.

Sin endpoints de grants, reasignación, revisión/rehabilitación fiscal, contactos, documentos ni historial 360 en #34. Un usuario que pierde habilitación conserva historia y queda sin acceso hasta el mecanismo autorizado de #36; no existe reparación automática ni bypass administrativo HTTP. El frontend existente continúa sin directorio/ficha fiscal. No hay rediseño visual, por lo que este corte no requiere nuevas capturas de UI; se ejecutan las regresiones visuales/navegador existentes.

Despliegue no ejecutado: V12 es aditiva y transaccional, probada desde V11 y en instalación limpia. Antes de una promoción humana, confirmar ausencia de grants TAXPAYER huérfanos en la base destino: la FK los rechaza y la migración revierte sin inventar contribuyentes ni borrar historia. Una base V11 productiva legítima no contiene estos grants, porque aún no había políticas fiscales aprobadas. Una vez usada V12 no se elimina historial para volver atrás; corregir hacia adelante mediante migración nueva y revisión humana. Mantener PR Draft, sin merge ni despliegue automático.
