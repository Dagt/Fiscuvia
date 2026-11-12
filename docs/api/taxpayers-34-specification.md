# #34 — especificación técnica propuesta de contribuyentes

**Proposed; Refs #74, futura #34. No endpoints implementados ni autorización de migración.** Depende de aprobación explícita [D74-01..05](../product/SPEC01-decisions-74.md). Contrato verificable: [OpenAPI Proposed](openapi-taxpayers-34-proposed.yaml). No editar openapi-v1.yaml implementado ni generar tipos productivos desde este contrato hasta implementar el corte autorizado.

## Alcance listo para decidir

API tenant-scoped para alta, consulta paginada/detalle, sustitución explícita de perfil/régimen, archivo y restauración del mismo UUID. Tablas/campos/FK en [v0.1.1](../data-model/schema-v0.1.1-proposed.md). Principal inicial y grants exactos del owner creador únicamente si D74-03 se aprueba. #35 construye directorio; #36 ficha/contactos/cartera/certificados. Sin export, import CSV, SAT/CSF parser, catálogo normativo, obligaciones, documentos, honorarios, revisiones fiscales o editor genérico de grants en #34.

Trazabilidad: FR-004/007/009/012..017 (FR-012 contacto y FR-016 gestión completa diferidos expresamente a #36), BR-001..004/010..014, NFR-001..009/010/013/015/020/021/026/030..036/040/044, ADR-003/005/007/008/010/014/015 Proposed. No declarar FR completos por entregar un subconjunto.

## Modelo y reglas

- Taxpayer UUID estable, tenant FK, RFC normalizado único **también archivado**, persona, nombre legal/comercial, ACTIVE/ARCHIVED, incorporación/modificación UTC, actor membership y versión segura.
- Revisión inmutable por cada versión con snapshot de perfil/régimen. Régimen es conjunto manual de referencias, sin reglas automáticas; vigencia y procedencia no se deducen. No fuentes oficiales configurables desde request.
- Responsabilidad temporal y acceso explícito independientes; FK membership del mismo tenant, no userId global como responsable. Owner creador es PRIMARY inicial propuesto; puede haber contribuyentes sin acceso para otro owner si no tiene grant exacto.
- No DELETE ni PATCH libre de status, RFC/personType/tenant/id/version/autor o timestamps. Cambios de RFC/personType requieren futuro comando de corrección aprobado, no recreación del expediente para borrar historia.
- Edición sólo ACTIVE. ARCHIVED permite consulta y Restore, no generación futura ni edición ordinaria. Archivo conserva responsabilidades/grants/historia; un archivado puede seguir siendo visible sólo a autorizados. Restaurar comprueba cuota, no implica alta fiscal ante SAT.

## RFC: tres niveles diferentes

**Formato local:** trim de espacio ASCII exterior, normalización Unicode NFC y uppercase con Locale.ROOT; rechazar controles, espacios internos, separadores/homoglifos y caracteres fuera del patrón, sin quitar guiones ni rellenar faltantes. Entrada max32 caracteres para normalizar; resultado exactamente13 INDIVIDUAL o12 LEGAL_ENTITY. Patrón propuesto (alfabeto inicial A-Z, Ñ y &):

```text
INDIVIDUAL:   ^[A-ZÑ&]{4}[0-9]{6}[A-Z0-9]{3}$
LEGAL_ENTITY: ^[A-ZÑ&]{3}[0-9]{6}[A-Z0-9]{3}$
```

**Estructura local:** YYMMDD debe tener mes/día realizable en al menos uno de 1900+YY / 2000+YY; no inferir siglo, edad, fecha de constitución ni identidad. Rechazar31 abril o30 febrero aunque pasen regex. No recalcular iniciales de nombres/homoclave ni validar existencia por dígito verificador; un algoritmo de checksum sólo se añadiría con fuente/fixtures de excepciones revisados, y seguiría siendo local. D74-01 propone rechazar RFC genéricos de facturación (XAXX010101000/XEXX010101000) como identidad de un cliente; confirmar esta exclusión y excepciones con propietario/contador antes de implementar, no asumir que describen personas distintas. RFC sintéticos de prueba no se consultan ante SAT.

**Verificación oficial:** no se hace en #34. Respuesta/UX sólo “Formato y estructura local revisados; sin verificación oficial”. El [documento de estructura del SAT](https://wwwmat.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461176322651&ssbinary=true) describe longitudes y componentes; no prueba existencia. Una [especificación técnica del SAT](https://www.sat.gob.mx/cs/Satellite?blobcol=urldata&blobkey=id&blobtable=MungoBlobs&blobwhere=1461175750595&ssbinary=true) incluye patrones de personas; se usa sólo como referencia de alfabeto/estructura, no como aceptación universal de excepciones. El [trámite de verificación de inscripción](https://wwwmatnp.sat.gob.mx/aplicacion/29073/verifica-si-estas-registrado-en-el-rfc) es una consulta separada y describe captcha; no se automatiza ni se presenta como API pública. Fuentes consultadas 2026-11-12; no se envió ningún RFC ni se infiere régimen/obligaciones de ellas.

Decisión de aceptar formato/estructura sin checksum bloqueante es D74-01. La etiqueta de validación en output es RFC_LOCAL_STRUCTURE_V1; officialVerification=NOT_PERFORMED fijado por servidor. No input de satVerified, fecha oficial o estado fiscal.

## Validaciones de perfil y régimen

JSON cerrado, propiedades desconocidas400; tipos estrictos, no coerción. Nombres trim/NFC, sin controles; legalName1..300, tradeName NULL o1..200. No construir nombre a partir de RFC ni imponer que coincida con SAT sin fuente. Límite de body64KiB propuesto, comprobar en aplicación sin debilitar límites existentes.

regimes requerido como arreglo0..10. Cada elemento: code exactamente tres dígitos, validFrom y validTo date o NULL explícitos; sin fechas inválidas. Ambos NULL = vigencia desconocida; fin sin inicio422; fin exclusivo posterior a inicio. Varios códigos simultáneos válidos como captura de referencia, misma clave sin solapamiento/duplicado en el conjunto vigente. No restringir códigos por catálogo inventado ni prometer que son vigentes/aplicables. Fecha de incorporación no se usa como validFrom por omisión. Orden de elementos canónico por código/fechas para fingerprint; duplicados422, no borrarlos silenciosamente. Datos históricos del mismo código pueden cambiar entre snapshots por corrección sin sobrescribir anteriores.

Source MANUAL/reviewStatus PENDING_REVIEW siempre de servidor para el nuevo snapshot; no funcionalidad de verificación humana en #34. Un futuro estado de revisión requerirá evidencia y permiso propios, nunca se equipara a SAT.

## Contratos HTTP propuestos

Prefijo T=`/api/v1/tenants/{tenantId}`. Sesión BFF same-origin vigente, no Bearer. Mutaciones JSON, CSRF X-CSRF-TOKEN e Idempotency-Key UUID obligatorios; no-store y X-Correlation-Id UUID del servidor en respuestas. No usar tenant/actor/roles del body como autoridad. DTOs separados de SQL; no serializar entidades ni principals.

| Método / ruta | Input / resultado | Autorización y estado |
|---|---|---|
| GET T/taxpayers | page0..10000(default0), size1..100(default20), sort id/legalName/createdAt asc/desc; search max80 literal, status ACTIVE(default)/ARCHIVED; Page {items,page,size,hasNext} sin total | TAXPAYER_VIEW collection; RLS antes de filtros/orden/LIMIT; vacío sin grants |
| POST T/taxpayers | rfc, personType, legalName, tradeName(nullable), regimes; 201 primer alta, 200 replay; recibo {targetId,version,replayed} y Location al item | TAXPAYER_CREATE/TENANT + owner/MFA propuesto; quota vigente; UUID/PRIMARY/grants exactos de servidor |
| GET T/taxpayers/{id} | Taxpayer completo con createdAt/updatedAt/version, regímenes y procedencia, rfcValidation/officialVerification | TAXPAYER_VIEW item; uniforme404 para ausente/no autorizado |
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

Mapper actual sólo conoce algunos SQLSTATE de equipo/cuota y 23505: #34 deberá añadir errores tipados propios sin reciclar P33xx ni mapear toda FK fallida a conflicto detallado. El ocultamiento de item se conserva desde IdentityStore; CSRF puede rechazar antes de MVC con403. No rate limiting operativo por existir429 en catálogo; no se promete SLO ni integración SAT.

## Unidad transaccional y replay

1. Sesión/actor/membresía/tenant/capacidad/MFA/grants frescos en `AuthorizedTenantOperations.execute`. Nunca TenantTransactions directo desde módulo ni permiso de UI.
2. Dentro del callback, bindCommand con UUID de request y correlación servidor. Deduplicar tenant/command con bloqueo y fingerprint canónico que incluye actor membership, operación, target, expectedVersion y payload ya normalizado (regímenes ordenados). Huella no se publica/loguea. Igual comando con diferencias →409; no mezclar clave con otro productor del journal, que es UNIQUE tenant/command.
3. Replay exacto tras autorización fresca devuelve recibo original antes de comparar estado/versión/cuota actuales. No repite callback de negocio, no retorna snapshot histórico como actual ni vuelve a mostrar secretos. Recurso archivado puede recibir replay de alta si el actor sigue autorizado para CREATE; el recibo no da lectura. Un actor revocado no puede recuperar un recibo.
4. Comando nuevo: validar input, tomar cuota cuando aumenta ACTIVE, bloquear recurso, comparar versión/estado/invariantes; generar datos de servidor. Actualización atómica WHERE tenant/id/version; versión+1 y updatedAt. No-op semántico409, incluida sustitución idéntica con otra clave.
5. Perfil, revisión, referencias de régimen, asignación/grants de bootstrap si alta, evento tipado, outbox AUDIT_RECORDED y recibo se confirman juntos. El journal sólo admite un hecho por command: alta registra un hecho compuesto TAXPAYER_CREATED que comprende su bootstrap, no cuatro eventos con la misma clave. Cambios de cartera posteriores usan comandos propios. Fallo de cualquier sink/constraint aborta todo; retry usa la misma clave.
6. Respuesta recibo sin PII; cliente hace GET nuevo para estado actual. No llamar VIEW dentro de CREATE/EDIT/ARCHIVE en mismo xid; target/conjunto quedan congelados. Abort de fetch no revierte commit confirmado.

No basta `AuditJournal.execute` actual: deduplica scope pero no todo el payload HTTP ni produce eventos fiscales. Proponer extensión aditiva de catálogo y productor estrecho de taxpayers, siguiendo patrón de team_commands, con comprobaciones SQL y trigger/función de negocio que no pueda saltar auditoría. Event codes propuestos TAXPAYER_CREATED / TAXPAYER_PROFILE_UPDATED / TAXPAYER_ARCHIVED / TAXPAYER_RESTORED, actor verificado+tenant+recurso+command/correlation+versión/cambio cerrado; no snapshot/nombre/RFC en journal/outbox. Las snapshots privadas permiten revisar valores anteriores. No lector público journal ni worker.

Cuota TAXPAYERS: COUNT ACTIVE real y serialización en limits; no reserva USERS, no contador paralelo. Alta/restauración ganadoras incrementan consumo por su fila; dos solicitudes por última plaza producen un éxito y409. Reducción de límite no borra archivos/clientes y puede impedir restore; no bloquea editar un ACTIVE existente ni archivar. Sin límite no existe “ilimitado”; tests configuran cuota sintética por procedimiento administrativo real.

## Integración con #33 y frontera #36

Backend obtiene membership del actor validado; ni userId ni membershipId del body de alta elige propietario. PRIMARY inicial referencia miembro ACTIVE mismo tenant, conservado aunque luego suspendido/revocado. V11 no recibe side effects automáticos fiscales y no cambia. Un cambio de rol reevalúa capacidades; grants exactos pueden persistir según decisión de conservación/revisión D74-03. #36 usa las relaciones temporales preparadas y comandos específicos de asignación/acceso aprobados, no escritura directa runtime a grants ni nuevo directorio global. Si el owner exige seleccionar responsable adicional desde alta, actualizar D74-04 y contrato antes de #34; no implementarlo silenciosamente.

## Frontend esperado y aceptación visual

#34 es API/modelo, no debe simular pantallas terminadas ni completar #35/#36. Sus contratos soportan el directorio S03 y ficha S04 de referencia aprobada #19/#52. #35 compondrá AppShell navy/teal, PageHeader “Contribuyentes”, CTA sólo con autoridad presentada por backend, FilterBar, DataTable desktop y lista móvil, formulario con persona/RFC/nombres/régimen manual opcional y procedencia visible; paginación hasNext sin total inventado. Resolución de acciones de presentación debe diseñarse en #35 sin endpoint genérico de permisos y reautorización siempre al comando.

Formulario muestra “No verifica inscripción ante SAT”; valida al capturar y en servidor, mantiene input al422/409, confirmación de archivo/restauración con motivo/costo de cuota y conflicto con recarga; no optimistic success ni botón SAT. Al cambiar tenant/actor aborta y limpia filas/formulario/caché; 401/403 retira datos. No RFC en URL/localStorage; datos sintéticos, nombres largos y teclado/foco. Si #34 toca UI por necesidad justificada, exige capturas reales1440×900/390×844, vacío/error/permisos/conflicto, comparación con referencia escrita y accesibilidad; no imagen generada. No capturas en este PR documental.

## Estrategia de pruebas exigida a #34

| Área | Oráculos útiles |
|---|---|
| RFC/perfil | Física13/moral12, minúsculas/exteriores, Ñ/&, errores de longitud/tipo/fecha/puntuación, febrero bisiesto/ambigüedad de siglo, genéricos según decisión, campos desconocidos/tipos/coerciones, textos largos/controles, cero SAT network call |
| Régimen/historia | Conjunto vacío, distintos regímenes concurrentes, límites10, solapamiento mismo código, vigencia desconocida, corrección conserva snapshot, procedencia no editable, no configuración fiscal derivada |
| PostgreSQL real / Flyway | Clean install y upgrade V11, checksums V1–V11, rollback DDL, FK tenant y same-taxpayer, RFC unique lifetime, guardas FORCE/privilegios/functions y JAR sin fixtures |
| Autorización HTTP/OIDC real | Cuatro roles/matriz aprobada, owner sin grant, MFA/CSRF, membresía SUSPENDED/REVOKED, identidad DISABLED, tenant suspendido; dos tenants mismo RFC y dos contadores separados dentro de uno |
| SQL/RLS adversarial | SELECT sin WHERE; GUC falso/sesión pool; VIEW no UPDATE/DELETE; definer sin contexto; grant a taxpayer ajeno/huérfano rechazado; mutaciones que quitan tenant_fence o resource_fence deben fallar oráculo |
| Listas | Scope antes de search/order/LIMIT, sort whitelist estable, hasNext sin fuga, misma señal404, ausencia de respuesta con detalles de duplicado fuera de cartera |
| Concurrencia | Dos altas mismo RFC (una confirma), dos ediciones misma versión, archivo vs edición, restore vs última cuota, grant nuevo no amplía colección congelada, revocación antes/después del lock, orden sin deadlocks |
| Idempotencia/atomicidad | Igual clave/contenido concurrente una mutación/evento/outbox/recibo; variaciones actor/payload/operación409; replay tras revocación denegado; replay no compara versión vieja como comando nuevo; fallos inyectados journal/outbox/recibo/commit revierten perfil/revisión/grants/cuota |
| Privacidad/contratos | DTO/runtime TS futuro, RFC9457/error headers, no-store, no RFC/nombres/secretos en logs/journal/artifacts; checksum no se llama verificación SAT |
| Límites modulares/regresión | Modulith/ArchUnit; mantener suites existentes sin skip/reducción. Añadir casos de negocio sin duplicar tests que sólo copian implementación |

No agregar tests productivos en #74. En #34 los positivos deben usar las políticas migradas **después de aprobación**, no grants de prueba que sustituyan autorización; fixtures sintéticos sirven a datos/negativos y quedan fuera de JAR. Ejecutar Maven clean verify con PostgreSQL/Keycloak reales, contratos HTTP AJV, generación API y checks web sólo si afectados, tools/ci/OpenAPI/seguridad y cinco checks remotos del SHA final. Evidencia de UI sólo si existe cambio real. No declarar implementable autorizado hasta resolver D74-01..05.
