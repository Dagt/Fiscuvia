# #34 — especificación técnica autorizada en su alcance inicial

**Implementación C01 #34 en esta rama, posterior al merge humano de #92:** [modelo físico, seguridad y diferencias de transporte](../architecture/TAXPAYER-DOMAIN-34.md), [OpenAPI implementado generador](openapi-v1.yaml), [validación](../delivery/VALIDATION-34.md). D74-01..05 conserva su alcance limitado. El contrato openapi-taxpayers-34-proposed.yaml queda como antecedente de aprobación de #74; no genera clientes. Este documento conserva las reglas aprobadas; metadatos HTTP efectivos se detallan en el contrato implementado.

## Alcance aprobado y frontera de implementación

API tenant-scoped para alta, consulta paginada/detalle, sustitución de perfil/régimen, archivo/restore del mismo UUID; [tablas/campos/FK](../data-model/schema-v0.1.1-proposed.md). Principal inicial, cuatro grants exactos del creador y habilitaciones versionadas según D74-03. #35 directorio/formularios; #36 ficha/contactos/historial/cartera/certificados y revisión explícita de acceso. Gestión completa de responsables/contactos/sucursales diferida. Sin export/import CSV, SAT/CSF parser, catálogo normativo, obligaciones, documentos, honorarios ni revisión fiscal; sin endpoints de grants/delegación/rehabilitación en #34.

Trazabilidad: FR-004/007/009/012..017 (FR-012 contacto y FR-016 gestión completa diferidos expresamente a #36), BR-001..004/010..014, NFR-001..009/010/013/015/020/021/026/030..036/040/044, ADR-003/005/007/008/010/014/015 Proposed. No declarar FR completos por entregar un subconjunto.

## Modelo y reglas

- Taxpayer UUID estable, tenant FK, RFC normalizado único **también archivado**, persona, nombre legal/comercial, ACTIVE/ARCHIVED, incorporación/modificación UTC, actor membership y versión segura.
- Revisión inmutable por cada versión con snapshot de perfil/régimen. Régimen es conjunto manual de referencias, sin reglas automáticas; vigencia y procedencia no se deducen. No fuentes oficiales configurables desde request.
- Responsabilidad temporal y acceso efectivo independientes; FK membership del mismo tenant, no userId global como responsable. Owner creador PRIMARY inicial aprobado; otro owner sin grant exacto habilitado no tiene acceso. Grant activo histórico no revive al reactivar/cambiar rol.
- No DELETE ni PATCH libre de status/RFC/personType/tenant/id/version/autor/timestamps. Corrección futura de RFC/personType necesita autorización específica, actor/fecha/motivo, historial, prevención de duplicados e integridad; fuera de #34. No recrear expediente para ocultar errores.
- Edición sólo ACTIVE. ARCHIVED permite consulta y Restore, no generación futura ni edición ordinaria. Archivo conserva responsabilidades/grants/historia; un archivado puede seguir siendo visible sólo a autorizados. Restaurar comprueba cuota, no implica alta fiscal ante SAT.

## RFC: tres niveles diferentes

**Formato local:** trim de espacio ASCII exterior, normalización Unicode NFC y uppercase con Locale.ROOT; rechazar controles, espacios internos, separadores/homoglifos y caracteres fuera del patrón, sin quitar guiones ni rellenar faltantes. Entrada max32 caracteres para normalizar; resultado exactamente13 INDIVIDUAL o12 LEGAL_ENTITY. Patrón propuesto (alfabeto inicial A-Z, Ñ y &):

```text
INDIVIDUAL:   ^[A-ZÑ&]{4}[0-9]{6}[A-Z0-9]{3}$
LEGAL_ENTITY: ^[A-ZÑ&]{3}[0-9]{6}[A-Z0-9]{3}$
```

**Estructura local:** YYMMDD debe tener mes/día realizable en al menos uno de 1900+YY / 2000+YY; no inferir siglo, edad, fecha de constitución ni identidad. Rechazar31 abril o30 febrero aunque pasen regex. No recalcular iniciales/homoclave ni validar existencia por dígito verificador; un algoritmo de checksum sólo se añadiría con fuente/fixtures revisados y seguiría siendo local. D74-01 aprueba rechazar **exactamente** XAXX010101000 y XEXX010101000 después de normalizar. Son valores genéricos documentados por el [SAT, clave genérica en CFDI](https://wwwmat.sat.gob.mx/articulo/16302/regla-2.7.1.26), consultado 2026-11-12; esta fuente identifica los valores, no autoriza una regla por prefijos ni una consulta de existencia. Un RFC distinto que empiece XAXX/XEXX no se rechaza sólo por su prefijo. No implementar excepciones: requieren contador y aprobación propia posterior. RFC sintéticos no se consultan ante SAT.

**Verificación oficial:** no se hace en #34. Respuesta/UX sólo “Formato y estructura local revisados; sin verificación oficial”. El [documento de estructura del SAT](https://wwwmat.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461176322651&ssbinary=true) describe longitudes y componentes; no prueba existencia. Una [especificación técnica del SAT](https://www.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461175750595&ssbinary=true) incluye patrones de personas; se usa sólo como referencia de alfabeto/estructura, no como aceptación universal de excepciones. El [trámite de verificación de inscripción](https://wwwmatnp.sat.gob.mx/aplicacion/29073/verifica-si-estas-registrado-en-el-rfc) es una consulta separada y describe captcha; no se automatiza ni se presenta como API pública. Fuentes consultadas 2026-11-12; no se envió ningún RFC ni se infiere régimen/obligaciones de ellas.

D74-01 aprueba formato/estructura local sin afirmar existencia oficial. Output rfcValidation=RFC_LOCAL_STRUCTURE_V1 y officialVerification=NOT_PERFORMED fijados por servidor. No input de satVerified/fecha oficial/estado fiscal; el rechazo exacto de genéricos no se convierte en validación SAT ni en prohibición general para personas extranjeras.

## Validaciones de perfil y régimen

JSON cerrado, propiedades desconocidas400; tipos estrictos, no coerción. Nombres trim/NFC, sin controles; legalName1..300, tradeName NULL o1..200. No construir nombre a partir de RFC ni imponer que coincida con SAT sin fuente. Límite de body64KiB propuesto, comprobar en aplicación sin debilitar límites existentes.

Cuerpos limitados a 65536 bytes. Propiedades nullable requeridas ausentes producen400 por deserialización; enviarlas explícitamente como NULL.

regimes requerido como arreglo0..10. Cada elemento: code exactamente tres dígitos, validFrom y validTo date o NULL explícitos; sin fechas inválidas. Ambos NULL = vigencia desconocida; fin sin inicio422; fin exclusivo posterior a inicio. Varios códigos simultáneos válidos como captura de referencia, misma clave sin solapamiento/duplicado en el conjunto vigente. No restringir códigos por catálogo inventado ni prometer que son vigentes/aplicables. Fecha de incorporación no se usa como validFrom por omisión. Orden de elementos canónico por código/fechas para fingerprint; duplicados422, no borrarlos silenciosamente. Datos históricos del mismo código pueden cambiar entre snapshots por corrección sin sobrescribir anteriores.

Source MANUAL/reviewStatus PENDING_REVIEW siempre de servidor para el nuevo snapshot; no funcionalidad de verificación humana en #34. Un futuro estado de revisión requerirá evidencia y permiso propios, nunca se equipara a SAT.

## Contratos HTTP implementados en C01

Prefijo T=`/api/v1/tenants/{tenantId}`. Sesión BFF same-origin vigente, no Bearer. Mutaciones JSON, CSRF X-CSRF-TOKEN e Idempotency-Key UUID obligatorios; no-store y X-Correlation-Id UUID del servidor en respuestas. No usar tenant/actor/roles del body como autoridad. DTOs separados de SQL; no serializar entidades ni principals.

| Método / ruta | Input / resultado | Autorización y estado |
|---|---|---|
| GET T/taxpayers | page0..10000(default0), size1..100(default20), sort id/legalName/createdAt asc/desc; search max80 literal, status ACTIVE(default)/ARCHIVED; Page {items,page,size,hasNext} sin total | TAXPAYER_VIEW collection; RLS antes de filtros/orden/LIMIT; vacío sin grants |
| POST T/taxpayers | rfc, personType, legalName, tradeName(nullable), regimes; 201 primer alta, 200 replay; recibo {targetId,version,replayed} y Location al item | TAXPAYER_CREATE/TENANT sólo owner propio ACTIVE/MFA≤10min, cuota vigente/disponible; UUID/PRIMARY/grants y habilitaciones de bootstrap de servidor |
| GET T/taxpayers/{id} | Taxpayer completo con createdAt/updatedAt/version, regímenes y procedencia, rfcValidation/officialVerification | TAXPAYER_VIEW item con grant efectivo/revisión vigente; uniforme404 para ausente/no autorizado/obsoleto |
| PATCH T/taxpayers/{id} | expectedVersion + **perfil completo** legalName/tradeName/regimes; 200 recibo | TAXPAYER_EDIT item, ACTIVE; sustituye sólo este perfil y conserva revisión; ausencia de campo obligatorio422 |
| POST T/taxpayers/{id}/archivals | expectedVersion + reasonCode SERVICE_ENDED / DUPLICATE_REVIEW / OTHER; 200 recibo | TAXPAYER_ARCHIVE item+MFA, sólo ACTIVE; código cerrado sin texto sensible |
| POST T/taxpayers/{id}/restorations | expectedVersion + reasonCode SERVICE_RESUMED / CORRECTION; 200 recibo | TAXPAYER_ARCHIVE item+MFA, sólo ARCHIVED; cuota y restore mismo UUID |

DUPLICATE_REVIEW no permite fusionar ni eliminar historia: sólo motivo administrativo de archivo. POST archivo/restauración son comandos explícitos; repeat con otra clave sobre estado ya alcanzado409, replay exacto200 sin repetir efectos. PATCH es sustitución del perfil definido, no JSON Patch, no JSON Merge Patch ni modificación libre de estado. No se acepta reason libre para evitar PII en auditoría; si se requiere nota privada #36 necesita política propia.

Taxpayer output: id/rfc/personType/legalName/tradeName/status/version/createdAt/updatedAt/regimes/rfcValidation/officialVerification. No nombres de colaboradores, contacto global, object_key, SQL o grants. Cada régimen devuelve code/validFrom/validTo/source/reviewStatus; capturedBy/historia se conserva en DB, su UI queda #36. Estado archivado no oculta archivo previo en historia privada.

Búsqueda GET literal sobre nombre/RFC normalizados, escape de %/_ en SQL parametrizado; no regex de usuario. Navegador no guarda búsqueda/RFC en URL de navegación, logs, analytics o almacenamiento persistente; backend/observabilidad ya redactan query y requieren revisión de SafeRoutes al agregar rutas. No filtros responsable/régimen sin contrato y pruebas propios de #35; no count global para satisfacer diseño visual.

## Errores y privacidad

RFC9457 y códigos de infraestructura existentes, detail fijo ES-MX y fieldErrors sólo nombre/código/mensaje, nunca rejectedValue. No-store también en errores. Headers de correlación/CSRF conservan convenciones actuales. No reflejar URI, IDs ajenos, RFC, SQL/constraint ni stack.

| HTTP / code | Caso |
|---|---|
| 400 BAD_REQUEST / INVALID_PAGINATION | JSON/tipo/UUID/header malformado, campo desconocido o paginación/sort inválido |
| 401 AUTHENTICATION_REQUIRED | Sesión ausente/caducada/identidad inválida; sin WWW-Authenticate Bearer |
| 403 ACCESS_DENIED | CSRF inválido, colección/alta sin política/MFA/membresía/contexto permitido |
| 404 NOT_FOUND | Item ausente/ajeno/no asignado/sin autoridad, sin revelar cuál condición falló |
| 409 STALE_VERSION | expectedVersion distinto, después de autorización y comprobación de replay |
| 409 IDEMPOTENCY_KEY_REUSED | Mismo commandId con otro actor/operación/target/payload canónico |
| 409 CONFLICT | RFC duplicado o estado/no-op incompatible; mensaje fijo no devuelve expediente existente |
| 409 QUOTA_UNAVAILABLE | Alta/restauración sin límite vigente o agotado; no números de terceros |
| 413 PAYLOAD_TOO_LARGE / 415 UNSUPPORTED_MEDIA_TYPE | Body excedido/content type no admitido |
| 422 VALIDATION_FAILED | Formato/estructura RFC, nombre/régimen/fecha/rango de versión inválido |
| 500 INTERNAL_ERROR | Error seguro; rollback de mutación si journal/outbox/recibo falla |

C01 añade SQLSTATE P3401..P3406 para validación, ausencia, versión, cuota, estado y contenido idempotente; 23505 se convierte en conflicto seguro. No recicla P33xx ni expone FK/constraints. El ocultamiento de item se conserva desde IdentityStore; CSRF puede rechazar antes de MVC con403. No rate limiting operativo por existir429 en catálogo; no se promete SLO ni integración SAT.

## Unidad transaccional y replay

1. Sesión/actor/membresía/tenant/rol/capacidad/MFA/grants y habilitación versionada frescos en `AuthorizedTenantOperations.execute`. En items/colecciones sólo entran grants con última revisión ENABLE de versión/rol actuales; helper RLS comprueba lo mismo. Nunca TenantTransactions directo ni permiso de UI.
2. Dentro del callback, bindCommand con UUID de request y correlación servidor. Deduplicar tenant/command con bloqueo y fingerprint canónico que incluye actor membership, operación, target, expectedVersion y payload ya normalizado (regímenes ordenados). Huella no se publica/loguea. Igual comando con diferencias →409; no mezclar clave con otro productor del journal, que es UNIQUE tenant/command.
3. Replay exacto tras autorización fresca devuelve recibo original antes de comparar estado/versión/cuota actuales. No repite negocio, no devuelve snapshot como actual ni renueva revisiones/grants. Recurso archivado puede recibir replay de alta si actor sigue autorizado para CREATE; el recibo no da lectura ni habilita su acceso anterior. Actor revocado o grant obsoleto para comando item no puede recuperar recibo por bypass.
4. Comando nuevo: validar input, tomar cuota cuando aumenta ACTIVE, bloquear recurso, comparar versión/estado/invariantes; generar datos de servidor. Actualización atómica WHERE tenant/id/version; versión+1 y updatedAt. No-op semántico409, incluida sustitución idéntica con otra clave.
5. Perfil, revisión, regímenes, PRIMARY y cuatro grants si alta, tres revisiones ENABLE de bootstrap VIEW/EDIT/ARCHIVE, evento/outbox/recibo confirman juntos. ASSIGN queda grant reservado sin política operativa ni revisión habilitante hasta #36. Un único hecho compuesto TAXPAYER_CREATED comprende el bootstrap; no cuatro eventos con la misma clave. Cambios/revisión de cartera posteriores usarán comandos propios. Fallo de sink/constraint aborta todo; retry misma clave sin renovar una habilitación antigua.
6. Respuesta recibo sin PII; cliente hace GET nuevo para estado actual. No llamar VIEW dentro de CREATE/EDIT/ARCHIVE en mismo xid; target/conjunto quedan congelados. Abort de fetch no revierte commit confirmado.

No basta `AuditJournal.execute` actual: deduplica scope pero no todo el payload HTTP ni produce eventos fiscales. Proponer extensión aditiva de catálogo y productor estrecho de taxpayers, siguiendo patrón de team_commands, con comprobaciones SQL y trigger/función de negocio que no pueda saltar auditoría. Event codes propuestos TAXPAYER_CREATED / TAXPAYER_PROFILE_UPDATED / TAXPAYER_ARCHIVED / TAXPAYER_RESTORED, actor verificado+tenant+recurso+command/correlation+versión/cambio cerrado; no snapshot/nombre/RFC en journal/outbox. Las snapshots privadas permiten revisar valores anteriores. No lector público journal ni worker.

Cuota TAXPAYERS: COUNT ACTIVE real y serialización en limits; no reserva USERS, no contador paralelo. Alta/restauración ganadoras incrementan consumo por su fila; dos solicitudes por última plaza producen un éxito y409. Reducción de límite no borra archivos/clientes y puede impedir restore; no bloquea editar un ACTIVE existente ni archivar. Sin límite no existe “ilimitado”; tests configuran cuota sintética por procedimiento administrativo real.

## Integración con #33 y frontera #36

Backend obtiene membership del actor validado, no userId/membershipId/owner del body. PRIMARY/historial se conservan aunque el miembro sea suspendido/revocado. V11 permanece intacta: sus cambios administrativos avanzan membership.version, incluido SET_ROLE, y conservan estados/cuotas/respuestas. #34 añade el cotejo con [grant_reviews](../data-model/schema-v0.1.1-proposed.md), no modifica ahora #33 ni agrega endpoints de cartera. Si cambia esa versión, ninguna habilitación anterior se reactiva al volver ACTIVE o recuperar rol. Distinguir miembro ACTIVE, rol vigente, grant existente, revisión ENABLE de versión/rol actuales y permiso final; el journal de #33 no sustituye la revisión explícita fiscal.

Las condiciones deben aplicarse en authorize_operation, conjunto congelado de colección, resource_allowed, comandos y replay, con lock de membresía/rol/grant/revisión hasta commit. Ejemplo: revisión7→suspensión8→reactivación9 deniega; contador→auxiliar→contador también deniega. Colección sin grants efectivos vacía; item sin habilitación404. Sin backfill de revisiones ni auto-review al login/reactivar/replay. Una actualización administrativa revertida no cambia la versión durable y conserva su autoridad previa; una confirmada exige nueva revisión. #36 desarrollará y someterá a aprobación sus comandos concretos de asignación/revisión; en #34 no se rehabilita cartera antigua.

Cualquier productor futuro de cambios de rol debe invalidar habilitaciones avanzando versión en el mismo commit. Probar que todos los productores existentes lo hacen, incluidos caminos administrativos admitidos; no abrir SQL runtime a membresías/roles/grants. Si la integración exige un ajuste aditivo, realizarlo con regresión de #33 en el PR de implementación, nunca reescribir V1–V11. Recuperación de un owner sin otra autoridad fiscal queda sin bypass y requiere procedimiento específico futuro, no permiso global nuevo.

## Frontend esperado y aceptación visual

#34 es API/modelo, no debe simular pantallas terminadas ni completar #35/#36. Sus contratos soportan el directorio S03 y ficha S04 de referencia aprobada #19/#52. #35 compondrá AppShell navy/teal, PageHeader “Contribuyentes”, CTA sólo con autoridad presentada por backend, FilterBar, DataTable desktop y lista móvil, formulario con persona/RFC/nombres/régimen manual opcional y procedencia visible; paginación hasNext sin total inventado. Resolución de acciones de presentación debe diseñarse en #35 sin endpoint genérico de permisos y reautorización siempre al comando.

Formulario muestra “No verifica inscripción ante SAT”; valida al capturar y en servidor, mantiene input al422/409, confirmación de archivo/restauración con motivo/costo de cuota y conflicto con recarga; no optimistic success ni botón SAT. Al cambiar tenant/actor aborta y limpia filas/formulario/caché; 401/403 retira datos. No RFC en URL/localStorage; datos sintéticos, nombres largos y teclado/foco. Si #34 toca UI por necesidad justificada, exige capturas reales1440×900/390×844, vacío/error/permisos/conflicto, comparación con referencia escrita y accesibilidad; no imagen generada. No capturas en este PR documental.

## Estrategia de pruebas exigida a #34

| Área | Oráculos útiles |
|---|---|
| RFC/perfil | Física13/moral12, minúsculas/exteriores, Ñ/&, errores de longitud/tipo/fecha/puntuación, febrero bisiesto/ambigüedad de siglo, genéricos según decisión, campos desconocidos/tipos/coerciones, textos largos/controles, cero SAT network call |
| Régimen/historia | Conjunto vacío, distintos regímenes concurrentes, límites10, solapamiento mismo código, vigencia desconocida, corrección conserva snapshot, procedencia no editable, no configuración fiscal derivada |
| PostgreSQL real / Flyway | Clean install y upgrade V11, checksums V1–V11, rollback DDL, FK tenant y same-taxpayer, RFC unique lifetime, guardas FORCE/privilegios/functions y JAR sin fixtures |
| Autorización HTTP/OIDC real | Cuatro roles/matriz aprobada, owner sin grant, grants históricos sin revisión/obsoletos/DISABLE, MFA/CSRF, miembro SUSPENDED/REVOKED, identidad DISABLED, tenant suspendido; dos tenants/mismo RFC y carteras separadas |
| SQL/RLS adversarial | SELECT sin WHERE; GUC falso/sesión pool; VIEW no UPDATE/DELETE; definer sin contexto; grant a taxpayer ajeno/huérfano rechazado; mutaciones que quitan tenant_fence o resource_fence deben fallar oráculo |
| Listas | Scope antes de search/order/LIMIT, sort whitelist estable, hasNext sin fuga, misma señal404, ausencia de respuesta con detalles de duplicado fuera de cartera |
| Concurrencia | Dos altas mismo RFC, ediciones misma versión, archivo vs edición, restore vs última plaza; grant/revisión nuevos no amplían colección congelada; revocación/cambio de rol antes/después del lock; orden sin deadlocks |
| No reactivación de permisos | Suspender→reactivar y contador→auxiliar→contador mantienen DENY en HTTP/lista/SQL/RLS; revisiones viejas nunca se reusan, última DISABLE prevalece, replay de CREATE/EDIT/#33 no rehabilita; rollback de cambio administrativo no invalida un hecho no confirmado; mutación que quita cotejo de versión del autorizador o RLS detectada; #34 no expone revisión/delegación |
| Idempotencia/atomicidad | Igual clave/contenido concurrente una mutación/evento/outbox/recibo; variaciones actor/payload/operación409; replay tras revocación denegado; replay no compara versión vieja como comando nuevo; fallos inyectados journal/outbox/recibo/commit revierten perfil/revisión/grants/cuota |
| Privacidad/contratos | DTO/runtime TS futuro, RFC9457/error headers, no-store, no RFC/nombres/secretos en logs/journal/artifacts; checksum no se llama verificación SAT |
| Límites modulares/regresión | Modulith/ArchUnit; mantener suites existentes sin skip/reducción. Añadir casos de negocio sin duplicar tests que sólo copian implementación |

No agregar tests productivos en #74. La futura #34 usará políticas migradas con referencia a D74-01..05 ya aprobadas; positivos no sustituyen autorización con grants sintéticos. Fixtures de datos/negativos fuera del JAR; probar bootstrap del productor real. Las filas supervisor/contador usan políticas reales migradas y fixtures administrativos de datos (grants/revisiones) sólo en PostgreSQL desechable, sin simular la política ni publicar revisión de cartera productiva antes de #36. Maven clean verify PostgreSQL/Keycloak, contratos AJV, generación API/checks afectados, tools/ci/OpenAPI/seguridad y cinco checks del SHA final, sin reducir regresión #33. La especificación inicial está autorizada; quedan revisión final/merge de #92 por el owner y trabajo posterior #34, no implementación en esta tarea.
