# C03 #36 — contratos y modelo propuestos, no operativos

**Proposed.** Requiere [D36-01/02](../decisions/issue-36-approval-proposal.md); D36-03 continúa separado y bloqueado. Este documento no activa endpoints, tablas, capacidades ni grants. El OpenAPI implementado y sus tipos generados sólo deben incluir operaciones efectivamente disponibles. V13 es la última migración verificada en main `05f1d0b58783438e193ae9ad86e7181b0e62c135`; no se reserva un número definitivo para DDL todavía bloqueado.

## Lectura

Rutas bajo `/api/v1/tenants/{tenantId}/taxpayers/{taxpayerId}`. Todas requieren sesión real y autorización fresca TAXPAYER_VIEW **item** del UUID exacto, tenant/identidad/membresía ACTIVE, grant activo y última revisión vigente. No admiten autoridad del navegador, RFC como PK o cartera global por rol. 401 sesión; 404 recurso ausente/ajeno/no autorizado; 400 identificadores/parámetros inválidos; 500 fallo seguro. JSON cerrado, correlación y Cache-Control:no-store.

| GET propuesto | DTO / semántica |
|---|---|
| `/administration` | administrationVersion, updatedAt nullable, canManageContacts/canManageCertificates derivados de EDIT vigente y parent ACTIVE. Sin flags genéricos de capacidades. Ausencia de agregado sólo equivale a versión0 tras autorizar la lectura, nunca ante403/404. |
| `/contacts?page&size&status` | items, page, size, hasNext, administrationVersion. Contact: id, name, relationship nullable, email nullable, phone nullable, primary, status ACTIVE/INACTIVE, version, createdAt/updatedAt. Sin userId OIDC ni verificación legal de email. |
| `/certificate-metadata?status` | administrationVersion, today, warningDays=30 e items. Certificate: id, type EFIRMA/CSD, expiresOn nullable, source MANUAL, reviewStatus PENDING_REVIEW, status ACTIVE/INACTIVE, version, createdAt/updatedAt. Alert: UNKNOWN_DATE/PAST_RECORDED_DATE/DUE_SOON/NONE y daysUntil nullable. No afirma validez SAT. |
| `/responsibilities?page&size` | Sólo relaciones reales autorizadas del expediente: id, type PRIMARY existente, membershipId tenant-scoped, displayName nullable actual, role nullable si no es único, membershipStatus, startedAt/endedAt. No incluye emails, identidad global, acceso efectivo de terceros ni permite asignar. |
| `/activity?page&size` | Entradas mínimas: id opaco estable de proyección, source PROFILE/CONTACT/CERTIFICATE/RESPONSIBILITY, action de enum, occurredAt, version y hasNext. No actor identificado, cuerpo anterior/nuevo de PII, comandos idempotentes, journal/event IDs ni AUDIT_VIEW. Sólo fuentes realmente implementadas; no completar con eventos ficticios. |

Páginas0..10000, size1..100, orden fijo por fecha/id; contactos filtran estado antes de LIMIT y RLS. La actividad usa la misma consulta/snapshot para fuentes UNION y hasNext. No devuelve total global. Los vínculos de fuentes permanecen tenant-safe; no se crea una tabla de eventos vacía sólo para anticipar módulos futuros.

**Directorio:** añadir `responsibleMembershipId` UUID opcional a GET colección y un GET `/taxpayers/responsible-options` bajo VIEW colección. EXISTS actual PRIMARY se aplica junto con RLS antes de LIMIT/OFFSET. Las opciones derivan únicamente de PRIMARY de contribuyentes visibles; nunca de todos los usuarios/membresías globales. Payload mínimo membershipId, displayName nullable, rol/estado permitido; opciones paginadas y sin conteos de cartera ajena. Un UUID válido ajeno produce cero coincidencias sin revelar identidad. No habilitarlo sin D36-01.

## Escrituras propuestas D36-02

Cada POST/PATCH autoriza TAXPAYER_EDIT item exacto y vuelve a comprobar parent ACTIVE. Política propuesta para owner/supervisor/contador con grants vigentes; auxiliar DENY, sin MFA adicional a EDIT. El cuerpo no acepta tenant, actor, autoría, timestamps, procedencia, revisión, grants ni versiones resultantes. CSRF e Idempotency-Key UUID obligatorios. Fecha civil `YYYY-MM-DD`, instantes UTC y enteros seguros para JavaScript.

| Método/ruta | Cuerpo cerrado propuesto |
|---|---|
| POST `/contacts` | expectedAdministrationVersion, name, relationship nullable, email nullable, phone nullable, primary boolean explícito |
| PATCH `/contacts/{contactId}` | expectedAdministrationVersion, expectedVersion, name, relationship/email/phone nullable; no PATCH de estado/principal |
| POST `/contacts/{contactId}/primary-designations` | expectedAdministrationVersion, expectedVersion |
| POST `/contacts/{contactId}/deactivations` | expectedAdministrationVersion, expectedVersion; no DELETE ni elección automática de sustituto |
| POST `/certificate-metadata` | expectedAdministrationVersion, type EFIRMA/CSD, expiresOn nullable |
| PATCH `/certificate-metadata/{certificateId}` | expectedAdministrationVersion, expectedVersion, expiresOn nullable; tipo/UUID/autoría inmutables |
| POST `/certificate-metadata/{certificateId}/deactivations` | expectedAdministrationVersion, expectedVersion; no cancelación SAT implícita |

Recibo propuesto: taxpayerId, targetId, version, administrationVersion, replayed. UUID de alta generado por servidor. Misma clave/actor/payload canónico devuelve recibo original después de autorización fresca, sin repetir eventos, consumir otra plaza o actualizar habilitaciones. Clave cambiada/otro payload409; versión hija o administrativa obsoleta409; estado/no-op/duplicado de certificado activo409; validación422. Un fallo de cualquier sink revierte datos, versiones, snapshots, evento, outbox y recibo. Claves repetidas entre familias no duplican journal; error seguro sin datos de otra operación.

El límite de20 contactos ACTIVE se comprueba bajo el lock del expediente; es una cota administrativa, no una cuota comercial nueva. Un solo principal ACTIVE mediante UNIQUE parcial y CHECK primary=>ACTIVE. Correo sintáctico opcional máximo254 y teléfono hasta40, sin controles; sin UNIQUE de correo. Normalización NFC/espacios exteriores, nombre1..160 y relación opcional hasta120. UNKNOWN se representa con NULL explícito. No se deduce representante legal, identidad o principal por ser el primer registro.

Certificados: como máximo un ACTIVE por tipo; para registrar otra metadata del mismo tipo se desactiva expresamente el registro previo y se conserva su historia. ExpiresOn puede ser NULL. Hoy se muestra «vence hoy»; anterior a hoy «fecha registrada pasada»; 1..30 días «próximo según fecha registrada»; >30 sin alerta. Inactivos no generan alerta vigente. Fecha administrativa America/Mexico_City y umbral30 fijos; sin catálogo legal, configuración, jobs o notificación externa.

## Persistencia necesaria, sólo tras aprobación

Propuesta de namespaces `taxpayer_details` (funciones públicas cerradas) y `taxpayer_details_private` (tablas y helpers). No altera la matriz de ocho políticas D74 ni introduce DML/SELECT privado runtime. El agregado administrativo añade concurrencia de contactos/certificados sin incrementar artificialmente taxpayer.version ni reescribir los snapshots de perfil C01.

| Tabla propuesta | Integridad mínima |
|---|---|
| administration | PK tenant/taxpayer, FK compuesta al contribuyente, version monotónica segura, updatedAt/actor de servidor; creación perezosa en primera mutación |
| contacts | PK tenant/id, UNIQUE tenant/taxpayer/id, FK padre y autorías a memberships del mismo tenant; estados/versiones/nulabilidad/cotas anteriores; principal único parcial |
| contact_revisions | PK tenant/contact/version, FK tenant/taxpayer/contact, FK comando/actor tenant-safe; snapshot append-only, cambios y desactivación conservados |
| certificate_metadata | PK tenant/id, UNIQUE tenant/taxpayer/id, FK padre y autores, tipo cerrado, fecha nullable, MANUAL/PENDING_REVIEW, ACTIVE/INACTIVE; activo único por contribuyente/tipo |
| certificate_revisions | PK tenant/certificate/version y FK completas al recurso/comando/actor; append-only, sin archivos o secretos |
| commands | PK tenant/command UUID, actor, acción/target, huella SHA-256, versiones resultantes, evento/instante; no copia de respuesta o PII |
| command_scope | xid/tenant/taxpayer/comando privados, sólo alcance efímero de la función autorizada; sin autoridad persistente ni exposición runtime |

Todas con ENABLE/FORCE RLS, propietario migrador sin BYPASSRLS, tenant/recurso exactos y FK ON DELETE RESTRICT. Sin UPDATE/DELETE/TRUNCATE de revisiones/recibos. Los helpers nuevos **no heredan el scope MEASURE de cuota** como permiso para leer contactos/certificados. No hay SELECT privado runtime/sesiones ni EXECUTE PUBLIC; las proyecciones exigen contexto item exacto y se prueban contra SQL sin WHERE y contexto manipulado.

Los comandos bloquean el contribuyente antes de comprobar ACTIVE y el agregado administrativo antes de expectedAdministrationVersion. Cambiar principal captura también la desmarcación del anterior con versiones/historia, en la misma transacción. Dos designaciones concurrentes con la misma versión administrativa no confirman ambas. Un cambio de perfil base independiente no pierde cambios de contactos; archivo/restore se serializan mediante el mismo padre y se revalida estado. La raíz de tenant/membresías/políticas conserva el orden de locks #33–35; no se actualizan ni reviven grants.

Eventos nuevos tipados, únicamente tras aprobar escrituras: CONTACT_CREATED/UPDATED/PRIMARY_DESIGNATED/DEACTIVATED y CERTIFICATE_METADATA_RECORDED/UPDATED/DEACTIVATED, resourceKind TAXPAYER y UUID padre. Journal/outbox existente con referencias mínimas, sin nombres/correos/teléfonos/fechas capturadas de certificados en logs o payloads públicos. La actividad privada puede proyectar códigos/fecha/versiones desde recibos/historial; no consulta auditoría global ni inventa filas si falta autorización.

## Delegación y responsabilidades adicionales: otro gate

Sólo propuesta D36-03. Operaciones específicas candidatas: `primary-responsibility-replacements`, `collaborator-relationships`, `relationship-endings`, `access-grants`, `access-revocations`, `grant-reviews`. Nunca PATCH arbitrario de grants, roles o estados. Antes de definir su contrato ejecutable se necesitan autoridad de arranque de ASSIGN, recuperación tras invalidez del propio owner y protección de la última autoridad; no se incluye un endpoint que se salte esas decisiones.

Cada candidato requeriría ASSIGN item efectivo, owner ACTIVE, MFA≤10min, destinatario mismo tenant y roles/capacidades expresamente permitidos, versiones de relación/grant/revisión/membresía y autorización fresca. Responsabilidad no concede grants. Cambios de rol o reactivación no rehabilitan historia. Se puede finalizar la atribución de un suspendido sin borrar autoría, únicamente bajo comando aprobado; nunca reasignación automática.

Modelo candidato sin DDL: conservar la relación PRIMARY de C01 como fuente única, extender su sustitución mediante fin e inicio atómicos, y añadir CONTRIBUTOR únicamente cuando se autorice el comando. UUID, tenant, taxpayerId, membershipId, tipo cerrado, startedAt/endedAt de servidor, versión y referencias de comando/autoría tenant-safe. FK compuestas a contribuyente y membresía; unicidad parcial de PRIMARY actual por expediente y de cada CONTRIBUTOR actual por expediente/membresía. Una sustitución bloquea el padre, termina el PRIMARY anterior y crea el siguiente en la misma transacción; no deja dos actuales ni elimina el anterior. El propietario creador puede seguir siendo PRIMARY en un despacho unipersonal sin que eso sustituya el grant. No se crea una relación por cada rol ni se copia entre tenants para un mismo usuario.

La consulta futura distinguirá relación vigente/histórica de membresía ACTIVE/SUSPENDED/REVOKED; no mostrará «con acceso» por la mera existencia de la asignación. No se promete que siempre exista una autoridad efectiva: suspensión/revocación puede invalidar al último administrador y exige el procedimiento de recuperación aún pendiente, no una excepción de acceso owner. La protección de la última autoridad debe especificar cómo interactúa con esos comandos de seguridad antes de habilitar delegación.

Pruebas previstas al habilitar: FK cruzadas, RLS y SQL sin filtros, contextos falsos/pool reutilizado, dos tenants/mismo RFC, owner sin grant, supervisor/contador con y sin grant, auxiliar, suspendido/revocado/rol cambiado/revisión obsoleta, MFA para acciones sensibles, versiones/primary concurrente, límite20, certificados únicos/fechas, replays y payload cambiado, rollback de cada sink y DTOs cerrados contra HTTP real. No sustituirlas por mocks ni afirmar implementados los endpoints de esta propuesta.
